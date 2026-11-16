package pitiupi.flappy.net;

import pitiupi.models.Messages.Message;

public class FlappyMatchMessage extends Message {

    private static final long serialVersionUID = 1L;

    private FlappyAction action;
    private String matchId;
    private long seed;
    private long sentAtMillis;
    private String senderInstanceId;
    private String senderName;
    private String senderIp;
    private int finalScore;
    private boolean ready;

    public FlappyMatchMessage() {
    }

    public FlappyMatchMessage(
            FlappyAction action,
            String matchId,
            long seed,
            long sentAtMillis,
            String senderInstanceId,
            String senderName,
            String senderIp,
            int finalScore
    ) {
        this(action, matchId, seed, sentAtMillis, senderInstanceId, senderName, senderIp, finalScore, false);
    }

    public FlappyMatchMessage(
            FlappyAction action,
            String matchId,
            long seed,
            long sentAtMillis,
            String senderInstanceId,
            String senderName,
            String senderIp,
            int finalScore,
            boolean ready
    ) {
        this.action = action;
        this.matchId = matchId;
        this.seed = seed;
        this.sentAtMillis = sentAtMillis;
        this.senderInstanceId = senderInstanceId;
        this.senderName = senderName;
        this.senderIp = senderIp;
        this.finalScore = finalScore;
        this.ready = ready;
    }

    public FlappyAction getAction() {
        return action;
    }

    public String getMatchId() {
        return matchId;
    }

    public long getSeed() {
        return seed;
    }

    public long getSentAtMillis() {
        return sentAtMillis;
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

    public int getFinalScore() {
        return finalScore;
    }

    public boolean isReady() {
        return ready;
    }
}

