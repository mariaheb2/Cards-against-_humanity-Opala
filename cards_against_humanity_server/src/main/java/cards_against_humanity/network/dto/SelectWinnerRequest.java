package cards_against_humanity.network.dto;

public class SelectWinnerRequest {

    private String gameId;
    private String cardId;

    public SelectWinnerRequest() {
    }

    public SelectWinnerRequest(String gameId, String cardId) {
        this.gameId = gameId;
        this.cardId = cardId;
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }
}
