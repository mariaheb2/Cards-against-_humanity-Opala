package cards_against_humanity.network.dto;

public class JoinGameRequest {

    private String userId;
    private String gameId;

    public JoinGameRequest() {
    }

    public JoinGameRequest(String userId, String gameId) {
        this.userId = userId;
        this.gameId = gameId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }
}
