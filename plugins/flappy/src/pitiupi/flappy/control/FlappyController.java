package pitiupi.flappy.control;

import pitiupi.application.messaging.MessageDispatcher;
import pitiupi.application.messaging.SendTarget;
import pitiupi.application.messaging.TransportMode;
import pitiupi.control.ConnectionManager;
import pitiupi.flappy.net.FlappyAction;
import pitiupi.flappy.net.FlappyMatchMessage;
import pitiupi.flappy.net.FlappyStateMessage;
import pitiupi.models.Messages.Message;
import pitiupi.presentation.shell.LocalNetworkInfo;
import pitiupi.presentation.shell.PeerIpv4;
import pitiupi.presentation.shell.PluginRoutingPanel;

import java.net.InetAddress;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class FlappyController {

    private final ConnectionManager connectionManager;
    private final String localInstanceId;
    private final Map<String, GhostState> ghostsByInstance = new LinkedHashMap<>();
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pitiupi-flappy-net");
        t.setDaemon(true);
        return t;
    });
    private final AtomicInteger pendingStatePackets = new AtomicInteger(0);

    private FlappyUiSink uiSink;

    private String currentMatchId = "solo";
    private long currentSeed = 2026L;
    private int outboundSeqNum = 0;
    private boolean localReady = false;
    private String lastHandledSyncMatchId = "";

    private final AtomicInteger totalPacketsSent = new AtomicInteger(0);
    private final AtomicInteger totalPacketsReceived = new AtomicInteger(0);
    private final AtomicInteger totalPacketsLost = new AtomicInteger(0);
    private final AtomicInteger totalPacketsOutOfOrder = new AtomicInteger(0);

    public FlappyController(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        this.localInstanceId = UUID.randomUUID().toString().substring(0, 8);
    }

    public void setUiSink(FlappyUiSink uiSink) {
        this.uiSink = uiSink;
    }

    public String getLocalInstanceId() {
        return localInstanceId;
    }

    public String getCurrentMatchId() {
        return currentMatchId;
    }

    public long getCurrentSeed() {
        return currentSeed;
    }

    public void setMatchContext(String matchId, long seed) {
        this.currentMatchId = matchId;
        this.currentSeed = seed;
        this.outboundSeqNum = 0;
        this.localReady = false;
        for (GhostState ghost : ghostsByInstance.values()) {
            ghost.resetForNewMatch();
        }
    }

    public boolean isLocalReady() {
        return localReady;
    }

    private static final long PEER_STALE_TIMEOUT_MS = 45_000L;

    public int getTotalPlayersCount() {
        pruneStaleGhosts();
        return 1 + ghostsByInstance.size();
    }

    public int getReadyPlayersCount() {
        pruneStaleGhosts();
        int count = localReady ? 1 : 0;
        for (GhostState ghost : ghostsByInstance.values()) {
            if (ghost.isReady()) {
                count++;
            }
        }
        return count;
    }

    private void pruneStaleGhosts() {
        long now = System.currentTimeMillis();
        ghostsByInstance.values().removeIf(ghost -> (now - ghost.getLastUpdateMillis()) > PEER_STALE_TIMEOUT_MS);
    }

    public int getRequiredReadyCount() {
        return (getTotalPlayersCount() / 2) + 1;
    }

    public boolean isQuorumReached() {
        return getReadyPlayersCount() >= getRequiredReadyCount();
    }

    public void resetReadyStates() {
        this.localReady = false;
        for (GhostState ghost : ghostsByInstance.values()) {
            ghost.setReady(false);
        }
    }

    public void setLocalReady(PluginRoutingPanel.RoutingSnapshot routing, boolean ready) {
        this.localReady = ready;
        FlappyMatchMessage msg = new FlappyMatchMessage(
                FlappyAction.READY_STATE,
                currentMatchId,
                currentSeed,
                System.currentTimeMillis(),
                localInstanceId,
                localPlayerName(),
                localPlayerIp(),
                0,
                ready
        );
        log((ready ? "READY enviado" : "READY cancelado") + " ("
                + getReadyPlayersCount() + "/" + getTotalPlayersCount()
                + ", mín. " + getRequiredReadyCount() + ")");
        networkExecutor.execute(() -> {
            int sent = dispatchMessage(routing, msg);
            if (sent > 0) {
                totalPacketsSent.addAndGet(sent);
            }
            notifyTelemetry();
        });
        checkReadyQuorum(routing);
        notifyTelemetry();
    }

    private void checkReadyQuorum(PluginRoutingPanel.RoutingSnapshot routing) {
        if (!isQuorumReached()) {
            return;
        }
        String leaderId = localReady ? localInstanceId : null;
        for (GhostState ghost : ghostsByInstance.values()) {
            if (ghost.isReady()) {
                if (leaderId == null || ghost.getInstanceId().compareTo(leaderId) < 0) {
                    leaderId = ghost.getInstanceId();
                }
            }
        }
        if (localInstanceId.equals(leaderId)) {
            long seed = (System.currentTimeMillis() % 90000L) + 10000L;
            broadcastMatchStart(routing, seed);
            if (uiSink != null) {
                uiSink.onLocalQuorumStartRequested(currentMatchId, seed);
            }
        }
    }

    public Collection<GhostState> getGhosts() {
        pruneStaleGhosts();
        return ghostsByInstance.values();
    }

    public void clearGhosts() {
        ghostsByInstance.clear();
        localReady = false;
        if (uiSink != null) {
            uiSink.onTelemetryUpdated();
        }
    }

    public void broadcastMatchStart(PluginRoutingPanel.RoutingSnapshot routing, long seed) {
        String matchId = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        setMatchContext(matchId, seed);

        FlappyMatchMessage msg = new FlappyMatchMessage(
                FlappyAction.SYNC_START,
                matchId,
                seed,
                System.currentTimeMillis(),
                localInstanceId,
                localPlayerName(),
                localPlayerIp(),
                0
        );

        log("SYNC_START enviado [match=" + matchId + ", seed=" + seed + ", modo=" + describeTransport(routing) + "]");
        boolean isUdp = routing == null || routing.transport() != TransportMode.TCP;
        networkExecutor.execute(() -> {
            int sent = dispatchMessage(routing, msg);
            totalPacketsSent.addAndGet(Math.max(1, sent));
            if (isUdp) {
                try {
                    Thread.sleep(80L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
                int retrySent = dispatchMessage(routing, msg);
                if (retrySent > 0) {
                    totalPacketsSent.addAndGet(retrySent);
                }
            }
            notifyTelemetry();
        });
    }

    public void sendStateTick(
            PluginRoutingPanel.RoutingSnapshot routing,
            float birdY,
            float velocityY,
            float worldX,
            int score,
            boolean alive
    ) {
        if (alive && pendingStatePackets.get() >= 2) {
            return;
        }
        outboundSeqNum++;
        FlappyStateMessage msg = new FlappyStateMessage(
                currentMatchId,
                currentSeed,
                localInstanceId,
                localPlayerName(),
                localPlayerIp(),
                outboundSeqNum,
                birdY,
                velocityY,
                worldX,
                score,
                alive,
                System.currentTimeMillis()
        );

        pendingStatePackets.incrementAndGet();
        networkExecutor.execute(() -> {
            try {
                int sent = dispatchMessage(routing, msg);
                if (sent > 0) {
                    totalPacketsSent.addAndGet(sent);
                }
            } finally {
                pendingStatePackets.decrementAndGet();
            }
        });
    }

    public void sendGameOver(PluginRoutingPanel.RoutingSnapshot routing, int finalScore) {
        FlappyMatchMessage msg = new FlappyMatchMessage(
                FlappyAction.GAME_OVER,
                currentMatchId,
                currentSeed,
                System.currentTimeMillis(),
                localInstanceId,
                localPlayerName(),
                localPlayerIp(),
                finalScore
        );
        log("GAME_OVER enviado (Hops: " + finalScore + ")");
        networkExecutor.execute(() -> {
            int sent = dispatchMessage(routing, msg);
            if (sent > 0) {
                totalPacketsSent.addAndGet(sent);
            }
            notifyTelemetry();
        });
    }

    public void onMatchMessageReceived(FlappyMatchMessage msg, PluginRoutingPanel.RoutingSnapshot activeRouting) {
        if (msg == null || isSelf(msg.getSenderInstanceId())) {
            return;
        }
        totalPacketsReceived.incrementAndGet();
        GhostState ghost = ensureGhost(msg.getSenderInstanceId(), msg.getSenderName(), msg.getSenderIp());
        ghost.touchLastUpdate();

        FlappyAction action = msg.getAction();
        if (action == FlappyAction.SYNC_START) {
            if (msg.getMatchId() != null && msg.getMatchId().equals(lastHandledSyncMatchId)) {
                return;
            }
            lastHandledSyncMatchId = msg.getMatchId() != null ? msg.getMatchId() : "";
            ghost.resetForNewMatch();
            log("Convite de corrida recebido de " + ghost.getPlayerName()
                    + " (" + ghost.getPlayerIp() + ") · Seed #" + msg.getSeed());
            if (uiSink != null) {
                uiSink.onRemoteMatchStartRequested(
                        msg.getMatchId(),
                        msg.getSeed(),
                        ghost.getPlayerName(),
                        ghost.getPlayerIp()
                );
            }
            FlappyMatchMessage ack = new FlappyMatchMessage(
                    FlappyAction.JOIN_ACK,
                    msg.getMatchId(),
                    msg.getSeed(),
                    System.currentTimeMillis(),
                    localInstanceId,
                    localPlayerName(),
                    localPlayerIp(),
                    0
            );
            String senderIp = msg.getSenderIp();
            networkExecutor.execute(() -> {
                int sent = sendDirectOrBroadcast(senderIp, activeRouting, ack);
                totalPacketsSent.addAndGet(Math.max(0, sent));
            });
        } else if (action == FlappyAction.JOIN_ACK) {
            ghost.resetForNewMatch();
            log(ghost.getPlayerName() + " sincronizou na corrida [match=" + msg.getMatchId() + "]!");
        } else if (action == FlappyAction.READY_STATE) {
            ghost.setReady(msg.isReady());
            log(ghost.getPlayerName() + (msg.isReady() ? " está PRONTO!" : " entrou na sala de espera.")
                    + " (" + getReadyPlayersCount() + "/" + getTotalPlayersCount()
                    + ", mín. " + getRequiredReadyCount() + ")");
            if (msg.isReady() && uiSink != null) {
                uiSink.onRemoteLobbyInviteReceived(ghost.getPlayerName(), ghost.getPlayerIp());
            }
            checkReadyQuorum(activeRouting);
        } else if (action == FlappyAction.GAME_OVER) {
            ghost.setAlive(false);
            ghost.setFinalScore(msg.getFinalScore());
            log(ghost.getPlayerName() + " sofreu DROP! Hops finais: " + msg.getFinalScore());
        }

        notifyTelemetry();
    }

    public void onStateMessageReceived(FlappyStateMessage msg) {
        if (msg == null || isSelf(msg.getSenderInstanceId())) {
            return;
        }
        if (!"solo".equals(currentMatchId)
                && msg.getMatchId() != null
                && !"solo".equals(msg.getMatchId())
                && !currentMatchId.equals(msg.getMatchId())) {
            return;
        }
        totalPacketsReceived.incrementAndGet();
        GhostState ghost = ensureGhost(msg.getSenderInstanceId(), msg.getSenderName(), msg.getSenderIp());
        ghost.touchLastUpdate();

        int prevLost = ghost.getPacketsLost();
        int prevOoo = ghost.getPacketsOutOfOrder();

        ghost.applyNetworkState(
                msg.getSenderName(),
                msg.getSenderIp(),
                msg.getSeqNum(),
                msg.getBirdY(),
                msg.getBirdVelocityY(),
                msg.getWorldX(),
                msg.getScore(),
                msg.isAlive()
        );

        int deltaLost = ghost.getPacketsLost() - prevLost;
        int deltaOoo = ghost.getPacketsOutOfOrder() - prevOoo;
        if (deltaLost > 0) {
            totalPacketsLost.addAndGet(deltaLost);
        }
        if (deltaOoo > 0) {
            totalPacketsOutOfOrder.addAndGet(deltaOoo);
        }

        notifyTelemetry();
    }

    private void notifyTelemetry() {
        if (uiSink != null) {
            uiSink.onTelemetryUpdated();
        }
    }

    private GhostState ensureGhost(String instanceId, String name, String ip) {
        String key = instanceId != null && !instanceId.isBlank() ? instanceId : (name + "@" + ip);
        String safeName = name != null && !name.isBlank() ? name : "Peer";
        String safeIp = PeerIpv4.normalize(ip != null ? ip : "");
        return ghostsByInstance.computeIfAbsent(
                key,
                k -> new GhostState(k, safeName, safeIp, ghostsByInstance.size())
        );
    }

    private boolean isSelf(String senderInstanceId) {
        return senderInstanceId != null && senderInstanceId.equals(localInstanceId);
    }

    private int dispatchMessage(PluginRoutingPanel.RoutingSnapshot routing, Message message) {
        if (routing == null || message == null) {
            return 0;
        }
        TransportMode mode = routing.transport() != null ? routing.transport() : TransportMode.MULTICAST_UDP;
        try {
            if (routing.everyoneInRoom()) {
                return MessageDispatcher.send(connectionManager, message, SendTarget.broadcastAll(), mode);
            }
            String ip = PeerIpv4.normalize(routing.peerIp());
            if (ip.isEmpty()) {
                return 0;
            }
            InetAddress addr = InetAddress.getByName(ip);
            return MessageDispatcher.send(connectionManager, message, SendTarget.singlePeer(addr), mode);
        } catch (Exception ex) {
            return 0;
        }
    }

    private int sendDirectOrBroadcast(
            String targetIp,
            PluginRoutingPanel.RoutingSnapshot routing,
            Message message
    ) {
        try {
            String normalized = PeerIpv4.normalize(targetIp);
            if (!normalized.isEmpty() && !LocalNetworkInfo.isLocalIpv4(normalized)) {
                InetAddress addr = InetAddress.getByName(normalized);
                return MessageDispatcher.send(connectionManager, message, SendTarget.singlePeer(addr), TransportMode.UNICAST_UDP);
            }
        } catch (Exception ignored) {
        }
        return dispatchMessage(routing, message);
    }

    private String localPlayerName() {
        String name = connectionManager.getUserName();
        return (name != null && !name.isBlank()) ? name.trim() : "Jogador";
    }

    private String localPlayerIp() {
        return PeerIpv4.normalize(LocalNetworkInfo.bestLocalIpv4());
    }

    private static String describeTransport(PluginRoutingPanel.RoutingSnapshot routing) {
        if (routing == null || routing.transport() == null) {
            return "UDP multicast";
        }
        return routing.transport().getLabel();
    }

    private void log(String text) {
        if (uiSink != null) {
            uiSink.appendNetworkLog(text);
        }
    }

    public int getTotalPacketsSent() {
        return totalPacketsSent.get();
    }

    public int getTotalPacketsReceived() {
        return totalPacketsReceived.get();
    }

    public int getTotalPacketsLost() {
        return totalPacketsLost.get();
    }

    public int getTotalPacketsOutOfOrder() {
        return totalPacketsOutOfOrder.get();
    }
}
