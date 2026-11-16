package pitiupi.flappy.GUI;

import pitiupi.application.messaging.PluginTransportCatalog;
import pitiupi.application.messaging.TransportMode;
import pitiupi.application.presence.UserPeerView;
import pitiupi.control.ConnectionManager;
import pitiupi.flappy.control.FlappyController;
import pitiupi.flappy.control.FlappyUiSink;
import pitiupi.flappy.control.GhostState;
import pitiupi.presentation.shell.PeerDraftPanel;
import pitiupi.presentation.shell.PeerIpv4;
import pitiupi.presentation.shell.PluginRoutingPanel;
import pitiupi.presentation.shell.PluginSessionKeys;
import pitiupi.presentation.shell.PluginSessionWorkspace;
import pitiupi.presentation.shell.SessionPeerLabels;
import pitiupi.presentation.shell.SessionTabBar;
import pitiupi.presentation.shell.SessionTabDirectionLabels;
import pitiupi.presentation.shell.ui.ShellUi;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.HierarchyEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class FlappyPanel extends JPanel implements FlappyUiSink {

    private static final String CARD_GAME = "__game__";
    private static final String CARD_DRAFT = "__draft__";
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ConnectionManager connectionManager;
    private final FlappyController controller;
    private final PluginRoutingPanel routingPanel;
    private final PluginSessionWorkspace workspace;
    private final SessionTabBar sessionTabs;

    private final CardLayout bodyLayout = new CardLayout();
    private final JPanel bodyCards = new JPanel(bodyLayout);
    private final FlappyCanvas canvas;
    private final JTextArea scoreboardArea = new JTextArea(9, 24);
    private final JTextArea networkLogArea = new JTextArea(10, 24);
    private PeerDraftPanel draftPanel;

    private final Map<String, String> peerDisplayNames = new HashMap<>();
    private String lastSessionKey = PluginSessionKeys.ROOM;
    private long lastScoreboardRefreshMs = 0L;
    private String lastScoreboardText = "";
    private boolean scoreboardPending = false;
    private final javax.swing.Timer scoreboardThrottleTimer;

    public FlappyPanel(ConnectionManager connectionManager, FlappyController controller) {
        this.connectionManager = connectionManager;
        this.controller = controller;
        this.controller.setUiSink(this);

        this.routingPanel = new PluginRoutingPanel(
                PluginTransportCatalog.PluginKind.PING,
                "<html><i>Na aba <b>Todos ↑</b> os pacotes de jogo saem em <b>UDP multicast</b> para a sala. "
                        + "Em uma aba de peer (<b>+ Nova</b>), os pacotes saem em <b>UDP unicast</b> direto para o IP do colega.</i></html>",
                false
        );

        this.canvas = new FlappyCanvas(
                controller,
                routingPanel::snapshot,
                this::refreshScoreboardAndTelemetry
        );

        this.workspace = new PluginSessionWorkspace(routingPanel, buildToolbar());
        this.sessionTabs = workspace.getSessionTabBar();
        this.sessionTabs.setCloseFallbackKey(PluginSessionKeys.ROOM);
        this.scoreboardThrottleTimer = new javax.swing.Timer(220, e -> {
            if (scoreboardPending) {
                refreshScoreboardNow();
            }
        });
        this.scoreboardThrottleTimer.setRepeats(false);

        initComponents();
        appendNetworkLog("Arena Packet Runner P2P pronta. Use ESPAÇO para transmitir ou inicie uma corrida sincronizada.");
        refreshScoreboardNow();
    }

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));

        JButton syncStartBtn = new JButton("Iniciar Corrida Sincronizada (Rede)");
        syncStartBtn.setFocusPainted(false);
        syncStartBtn.setToolTipText("Abre a Tela de Pronto (Ready) e convida os colegas da rede para iniciar juntos");
        syncStartBtn.addActionListener(e -> triggerSynchronizedLobby());

        JButton quickBtn = new JButton("Voo Local (Solo)");
        quickBtn.setFocusPainted(false);
        quickBtn.setToolTipText("Decola sozinho offline (não transmite para a rede nem exibe fantasmas)");
        quickBtn.addActionListener(e -> canvas.startLocalSoloRun(controller.getCurrentSeed()));

        JCheckBox deadReckoningCheck = new JCheckBox("Interpolação UDP (Dead Reckoning)", true);
        deadReckoningCheck.setFocusPainted(false);
        deadReckoningCheck.setToolTipText("Extrapolação linear a 60 FPS usando a velocidade vertical entre pacotes UDP (20 Hz)");
        deadReckoningCheck.addActionListener(e -> {
            canvas.setDeadReckoningEnabled(deadReckoningCheck.isSelected());
            appendNetworkLog("Dead Reckoning " + (deadReckoningCheck.isSelected() ? "ATIVADO (60 FPS)" : "DESATIVADO (20 Hz bruto)"));
        });

        JButton clearGhostsBtn = new JButton("Limpar Placar");
        clearGhostsBtn.setFocusPainted(false);
        clearGhostsBtn.addActionListener(e -> {
            controller.clearGhosts();
            canvas.enterIdleSoloMode();
            appendNetworkLog("Histórico de fantasmas limpo e modo retornado para Solo.");
        });

        bar.add(syncStartBtn);
        bar.add(quickBtn);
        bar.add(deadReckoningCheck);
        bar.add(clearGhostsBtn);
        return bar;
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        draftPanel = new PeerDraftPanel(
                "Desafiar Peer por IP",
                "Informe o endereço IPv4 do colega para abrir uma sessão dedicada de Flappy Bird P2P.",
                this::commitDraftIp
        );

        JPanel gameSplitPanel = buildGameAndSidebarPanel();
        bodyCards.add(gameSplitPanel, CARD_GAME);
        bodyCards.add(draftPanel, CARD_DRAFT);

        workspace.getBodyHost().add(bodyCards, BorderLayout.CENTER);
        add(workspace, BorderLayout.CENTER);

        initSessionTabs();

        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                canvas.ensureGameLoopRunning();
                canvas.requestFocusInWindow();
            }
        });
    }

    private JPanel buildGameAndSidebarPanel() {
        scoreboardArea.setEditable(false);
        scoreboardArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        scoreboardArea.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        networkLogArea.setEditable(false);
        networkLogArea.setLineWrap(true);
        networkLogArea.setWrapStyleWord(true);
        networkLogArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        networkLogArea.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JScrollPane scoreScroll = new JScrollPane(scoreboardArea);
        ShellUi.borderlessScroll(scoreScroll);
        JPanel scoreSection = ShellUi.sidebarPanel("Placar & Telemetria UDP", scoreScroll);

        JScrollPane logScroll = new JScrollPane(networkLogArea);
        ShellUi.borderlessScroll(logScroll);
        JPanel logSection = ShellUi.sidebarPanel("Protocolo de Aplicação (Log)", logScroll);

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scoreSection, logSection);
        rightSplit.setResizeWeight(0.48);
        rightSplit.setDividerSize(5);
        rightSplit.setBorder(BorderFactory.createEmptyBorder());
        rightSplit.setPreferredSize(new Dimension(290, 440));

        JPanel container = new JPanel(new BorderLayout(0, 0));
        container.add(canvas, BorderLayout.CENTER);
        container.add(rightSplit, BorderLayout.EAST);
        return container;
    }

    private void initSessionTabs() {
        sessionTabs.addOrSelectTab(
                PluginSessionKeys.ROOM,
                SessionTabDirectionLabels.outboundTitle("Todos"),
                "Arena geral da sala (UDP multicast)",
                new JPanel(),
                false
        );
        sessionTabs.setOnTabSelected(this::onSessionTabSelected);
        sessionTabs.setOnAddPeerRequested(this::openDraftPeerTab);
        sessionTabs.setOnTabClosed(this::onSessionTabClosed);
        sessionTabs.selectTab(PluginSessionKeys.ROOM);
        bodyLayout.show(bodyCards, CARD_GAME);
    }

    private void onSessionTabSelected(String key) {
        if (key != null && !PluginSessionKeys.PEER_DRAFT.equals(key)) {
            lastSessionKey = key;
        }
        if (PluginSessionKeys.PEER_DRAFT.equals(key)) {
            bodyLayout.show(bodyCards, CARD_DRAFT);
            draftPanel.focusIpField();
            return;
        }
        bodyLayout.show(bodyCards, CARD_GAME);
        if (key == null || PluginSessionKeys.ROOM.equals(key)) {
            routingPanel.bindRoomSession();
            routingPanel.selectTransport(TransportMode.MULTICAST_UDP);
        } else if (key.startsWith("peer:")) {
            String ip = key.substring("peer:".length());
            String name = peerDisplayNames.getOrDefault(ip, ip);
            routingPanel.bindPeerSession(ip, name);
            routingPanel.selectTransport(TransportMode.UNICAST_UDP);
        }
        canvas.requestFocusInWindow();
    }

    private void onSessionTabClosed(String key) {
        if (PluginSessionKeys.PEER_DRAFT.equals(key)) {
            if (draftPanel != null) {
                draftPanel.clearIpField();
            }
            String restore = lastSessionKey != null && sessionTabs.hasTab(lastSessionKey)
                    ? lastSessionKey
                    : PluginSessionKeys.ROOM;
            sessionTabs.selectTabSilently(restore);
            onSessionTabSelected(restore);
        }
    }

    private void openDraftPeerTab() {
        String current = sessionTabs.selectedKey();
        if (current != null && !PluginSessionKeys.PEER_DRAFT.equals(current)) {
            lastSessionKey = current;
        }
        if (!sessionTabs.hasTab(PluginSessionKeys.PEER_DRAFT)) {
            sessionTabs.addOrSelectTab(
                    PluginSessionKeys.PEER_DRAFT,
                    "Novo desafio",
                    "Informe o IP do adversário",
                    new JPanel(),
                    true
            );
        } else {
            sessionTabs.selectTab(PluginSessionKeys.PEER_DRAFT);
        }
        bodyLayout.show(bodyCards, CARD_DRAFT);
        draftPanel.focusIpField();
    }

    private void commitDraftIp(String ip) {
        if (draftPanel != null) {
            draftPanel.clearIpField();
        }
        if (sessionTabs.hasTab(PluginSessionKeys.PEER_DRAFT)) {
            sessionTabs.closeTab(PluginSessionKeys.PEER_DRAFT);
        }
        openPeerTab(ip, ip);
    }

    private void openPeerTab(String ip, String displayName) {
        String canonical = PeerIpv4.normalize(ip);
        if (canonical.isEmpty()) {
            return;
        }
        String resolved = SessionPeerLabels.resolveDisplayName(connectionManager, canonical, displayName);
        peerDisplayNames.put(canonical, resolved);
        String key = PluginSessionKeys.peer(canonical);
        String tabTitle = SessionTabDirectionLabels.outboundTitle(resolved);
        if (sessionTabs.hasTab(key)) {
            sessionTabs.setTabTitle(key, tabTitle, canonical);
            sessionTabs.selectTab(key);
        } else {
            sessionTabs.addOrSelectTab(key, tabTitle, canonical, new JPanel(), true);
        }
        routingPanel.bindPeerSession(canonical, resolved);
        routingPanel.selectTransport(TransportMode.UNICAST_UDP);
        bodyLayout.show(bodyCards, CARD_GAME);
        canvas.requestFocusInWindow();
    }

    public void focusMulticastRoom() {
        sessionTabs.selectTab(PluginSessionKeys.ROOM);
        onSessionTabSelected(PluginSessionKeys.ROOM);
    }

    public void focusPeerAddress(UserPeerView peer) {
        if (peer == null || peer.isBroadcastTarget() || peer.isSelf()) {
            return;
        }
        peer.getInetAddress().ifPresent(addr -> openPeerTab(PeerIpv4.fromInetAddress(addr), peer.getDisplayName()));
    }

    private void triggerSynchronizedLobby() {
        canvas.enterReadyScreen(true);
    }

    public PluginRoutingPanel.RoutingSnapshot currentRoutingSnapshot() {
        return routingPanel.snapshot();
    }

    public void stopGameLoop() {
        if (scoreboardThrottleTimer.isRunning()) {
            scoreboardThrottleTimer.stop();
        }
        canvas.stopGameLoop();
    }

    @Override
    public void appendNetworkLog(String line) {
        Runnable task = () -> {
            String timestamp = LocalTime.now().format(TIME_FMT);
            networkLogArea.append("[" + timestamp + "] " + line + "\n");
            if (networkLogArea.getLineCount() > 140) {
                try {
                    int end = networkLogArea.getLineEndOffset(networkLogArea.getLineCount() - 120);
                    networkLogArea.replaceRange("", 0, end);
                } catch (Exception ignored) {
                }
            }
            networkLogArea.setCaretPosition(networkLogArea.getDocument().getLength());
        };
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    @Override
    public void onRemoteMatchStartRequested(String matchId, long seed, String senderName, String senderIp) {
        SwingUtilities.invokeLater(() -> {
            if (canvas.getPhase() != FlappyCanvas.GamePhase.PLAYING) {
                canvas.startSynchronizedCountdown(matchId, seed);
                appendNetworkLog("Largada sincronizada iniciada com " + senderName + " (Seed #" + seed + ")!");
            } else {
                controller.setMatchContext(matchId, seed);
                appendNetworkLog("Nova Seed #" + seed + " recebida de " + senderName + " (em voo atual).");
            }
        });
    }

    @Override
    public void onRemoteLobbyInviteReceived(String senderName, String senderIp) {
        SwingUtilities.invokeLater(() -> {
            FlappyCanvas.GamePhase currentPhase = canvas.getPhase();
            if (currentPhase == FlappyCanvas.GamePhase.IDLE
                    || currentPhase == FlappyCanvas.GamePhase.GAME_OVER) {
                canvas.enterReadyScreen(false);
                appendNetworkLog("Sala aberta por " + senderName + " — pressione ESPAÇO/R ou clique para dar PRONTO!");
            }
        });
    }

    @Override
    public void onLocalQuorumStartRequested(String matchId, long seed) {
        SwingUtilities.invokeLater(() -> {
            canvas.startSynchronizedCountdown(matchId, seed);
            appendNetworkLog("Quórum atingido! Iniciando largada sincronizada (Seed #" + seed + ").");
        });
    }

    @Override
    public void onTelemetryUpdated() {
        Runnable update = () -> {
            if (canvas.getPhase() == FlappyCanvas.GamePhase.READY) {
                canvas.repaint();
            }
            refreshScoreboardAndTelemetry();
        };
        if (SwingUtilities.isEventDispatchThread()) {
            update.run();
        } else {
            SwingUtilities.invokeLater(update);
        }
    }

    private void refreshScoreboardAndTelemetry() {
        long now = System.currentTimeMillis();
        if (now - lastScoreboardRefreshMs >= 220L) {
            refreshScoreboardNow();
        } else {
            scoreboardPending = true;
            if (!scoreboardThrottleTimer.isRunning()) {
                scoreboardThrottleTimer.start();
            }
        }
    }

    private void refreshScoreboardNow() {
        scoreboardPending = false;
        lastScoreboardRefreshMs = System.currentTimeMillis();

        StringBuilder sb = new StringBuilder(256);
        String localName = connectionManager.getUserName();
        if (localName == null || localName.isBlank()) {
            localName = "Você";
        }
        sb.append(String.format("Partida: %-6s  Seed: #%d\n",
                controller.getCurrentMatchId(), controller.getCurrentSeed() % 100000));
        sb.append(String.format("Pacotes TX: %-5d RX: %-5d\n",
                controller.getTotalPacketsSent(), controller.getTotalPacketsReceived()));
        sb.append(String.format("Perdas UDP: %-5d OOO: %-5d\n",
                controller.getTotalPacketsLost(), controller.getTotalPacketsOutOfOrder()));
        sb.append("----------------------------------\n");
        sb.append(String.format("%-12s %5s %5s %s\n", "JOGADOR", "PTS", "REC", "STATUS"));
        sb.append("----------------------------------\n");

        String localStatus = switch (canvas.getPhase()) {
            case PLAYING -> "Voando";
            case COUNTDOWN -> "Largada";
            case READY -> controller.isLocalReady() ? "Pronto!" : "Esperando";
            case GAME_OVER -> "Drop";
            case IDLE -> "Inicial";
        };
        sb.append(String.format("*%-11s %5d %5d %s\n",
                truncate(localName, 11),
                canvas.getScore(),
                canvas.getBestScore(),
                localStatus));

        for (GhostState ghost : controller.getGhosts()) {
            String status;
            if (ghost.isReady()) {
                status = "Pronto!";
            } else if (!ghost.isParticipating()) {
                status = "Sala";
            } else {
                status = ghost.isAlive() ? "Voando" : "Drop";
            }
            sb.append(String.format(" %-11s %5d %5d %s\n",
                    truncate(ghost.getPlayerName(), 11),
                    ghost.getScore(),
                    ghost.getBestScore(),
                    status));
        }

        String nextText = sb.toString();
        if (!nextText.equals(lastScoreboardText)) {
            lastScoreboardText = nextText;
            scoreboardArea.setText(nextText);
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) {
            return "";
        }
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}

