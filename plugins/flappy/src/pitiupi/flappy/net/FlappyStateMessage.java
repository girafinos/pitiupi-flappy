package pitiupi.flappy.net;

import pitiupi.models.Messages.Message;

public class FlappyStateMessage extends Message {

    private static final long serialVersionUID = 1L;

    private String matchId;
    private long seed;
    private String senderInstanceId;
    private String senderName;
    private String senderIp;
    private int seqNum;
    private float birdY;
    private float birdVelocityY;
    private float worldX;
    private int score;
    private boolean alive;
    private long sentAtMillis;

    public FlappyStateMessage() {
    }

    public FlappyStateMessage(
            String matchId,
            long seed,
            String senderInstanceId,
            String senderName,
            String senderIp,
            int seqNum,
            float birdY,
            float birdVelocityY,
            float worldX,
            int score,
            boolean alive,
            long sentAtMillis
    ) {
        this.matchId = matchId;
        this.seed = seed;
        this.senderInstanceId = senderInstanceId;
        this.senderName = senderName;
        this.senderIp = senderIp;
        this.seqNum = seqNum;
        this.birdY = birdY;
        this.birdVelocityY = birdVelocityY;
        this.worldX = worldX;
        this.score = score;
        this.alive = alive;
        this.sentAtMillis = sentAtMillis;
    }

    public String getMatchId() {
        return matchId;
    }

    public long getSeed() {
        return seed;
    }

    public String getSenderInstanceId() {
        return senderInstanceId;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getSenderIp() {
        return senderIp;
    }

    public int getSeqNum() {
        return seqNum;
    }

    public float getBirdY() {
        return birdY;
    }

    public float getBirdVelocityY() {
        return birdVelocityY;
    }

    public float getWorldX() {
        return worldX;
    }

    public int getScore() {
        return score;
    }

    public boolean isAlive() {
        return alive;
    }

    public long getSentAtMillis() {
        return sentAtMillis;
    }
}

