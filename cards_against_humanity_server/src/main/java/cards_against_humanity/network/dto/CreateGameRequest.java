package cards_against_humanity.network.dto;

public class CreateGameRequest {

    private String userId;
    private int maxPlayers;

    public CreateGameRequest() {
    }

    public CreateGameRequest(String userId, int maxPlayers) {
        this.userId = userId;
        this.maxPlayers = maxPlayers;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }
}
