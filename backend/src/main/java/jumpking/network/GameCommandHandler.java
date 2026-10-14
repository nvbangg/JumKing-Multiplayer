package jumpking.network;

public interface GameCommandHandler {
    boolean supports(String type);
    void handle(GameConnection connection, GameEnvelope command);
    void disconnected(GameConnection connection);
}
