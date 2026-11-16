package pitiupi.flappy.control;

public interface FlappyUiSink {

    void appendNetworkLog(String line);

    void onRemoteMatchStartRequested(String matchId, long seed, String senderName, String senderIp);

    void onRemoteLobbyInviteReceived(String senderName, String senderIp);

    void onLocalQuorumStartRequested(String matchId, long seed);

    void onTelemetryUpdated();
}
