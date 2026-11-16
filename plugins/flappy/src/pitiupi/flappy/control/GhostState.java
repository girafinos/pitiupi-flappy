package pitiupi.flappy.control;

import java.awt.Color;

public class GhostState {

    private final String instanceId;
    private String playerName;
    private String playerIp;
    private final Color color;

    private int lastSeqNum = -1;
    private float rawBirdY = 220f;
    private float rawVelocityY = 0f;
    private float rawWorldX = 0f;

    private float renderBirdY = 220f;
    private float renderVelocityY = 0f;
    private float renderWorldX = 0f;

    private int score = 0;
    private int bestScore = 0;
    private boolean alive = false;
    private boolean participating = false;
    private boolean ready = false;
    private long lastUpdateMillis = System.currentTimeMillis();

    private int packetsReceived = 0;
    private int packetsLost = 0;
    private int packetsOutOfOrder = 0;

    private static final Color[] PALETTE = {
        new Color(220, 220, 220),
        new Color(180, 180, 180),
        new Color(200, 200, 200),
        new Color(160, 160, 160),
        new Color(235, 235, 235),
        new Color(190, 190, 190)
    };

    public GhostState(String instanceId, String playerName, String playerIp, int colorIndex) {
        this.instanceId = instanceId;
        this.playerName = playerName;
        this.playerIp = playerIp;
        this.color = PALETTE[Math.abs(colorIndex) % PALETTE.length];
    }

    public boolean applyNetworkState(
            String name,
            String ip,
            int seqNum,
            float birdY,
            float velocityY,
            float worldX,
            int score,
            boolean alive
    ) {
        this.playerName = name != null && !name.isBlank() ? name : this.playerName;
        this.playerIp = ip != null && !ip.isBlank() ? ip : this.playerIp;

        if (seqNum <= 3 && this.lastSeqNum > 5) {
            this.lastSeqNum = -1;
        }

        if (this.lastSeqNum >= 0 && seqNum <= this.lastSeqNum) {
            this.packetsOutOfOrder++;
            return false;
        }

        if (this.lastSeqNum >= 0 && seqNum > this.lastSeqNum + 1) {
            int gap = seqNum - this.lastSeqNum - 1;
            if (gap < 100) {
                this.packetsLost += gap;
            }
        }

        this.lastSeqNum = seqNum;
        this.packetsReceived++;
        this.rawBirdY = birdY;
        this.rawVelocityY = velocityY;
        this.rawWorldX = worldX;

        this.renderBirdY = birdY;
        this.renderVelocityY = velocityY;
        this.renderWorldX = worldX;

        this.score = score;
        if (score > this.bestScore) {
            this.bestScore = score;
        }
        this.participating = true;
        this.alive = alive;
        this.lastUpdateMillis = System.currentTimeMillis();
        return true;
    }

    public void stepDeadReckoning(float gravity, float speedX, float minY, float maxY, float dtScale) {
        if (!participating || !alive) {
            return;
        }
        if (System.currentTimeMillis() - lastUpdateMillis > 500) {
            return;
        }
        renderBirdY += renderVelocityY * dtScale;
        renderVelocityY += gravity * dtScale;
        if (renderBirdY < minY) {
            renderBirdY = minY;
            renderVelocityY = 0;
        } else if (renderBirdY > maxY) {
            renderBirdY = maxY;
            renderVelocityY = 0;
        }
        renderWorldX += speedX * dtScale;
    }

    public void resetForNewMatch() {
        this.lastSeqNum = -1;
        this.score = 0;
        this.alive = false;
        this.participating = false;
        this.ready = false;
        this.rawBirdY = 220f;
        this.renderBirdY = 220f;
        this.rawVelocityY = 0f;
        this.renderVelocityY = 0f;
        this.rawWorldX = 0f;
        this.renderWorldX = 0f;
    }

    public void touchLastUpdate() {
        this.lastUpdateMillis = System.currentTimeMillis();
    }

    public boolean isParticipating() {
        return participating;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getPlayerIp() {
        return playerIp;
    }

    public Color getColor() {
        return color;
    }

    public float getBirdY(boolean deadReckoning) {
        return deadReckoning ? renderBirdY : rawBirdY;
    }

    public float getWorldX(boolean deadReckoning) {
        return deadReckoning ? renderWorldX : rawWorldX;
    }

    public float getVelocityY(boolean deadReckoning) {
        return deadReckoning ? renderVelocityY : rawVelocityY;
    }

    public int getScore() {
        return score;
    }

    public int getBestScore() {
        return bestScore;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public void setFinalScore(int finalScore) {
        this.score = finalScore;
        if (finalScore > this.bestScore) {
            this.bestScore = finalScore;
        }
    }

    public long getLastUpdateMillis() {
        return lastUpdateMillis;
    }

    public int getPacketsReceived() {
        return packetsReceived;
    }

    public int getPacketsLost() {
        return packetsLost;
    }

    public int getPacketsOutOfOrder() {
        return packetsOutOfOrder;
    }
}

