package cards_against_humanity.models;
import cards_against_humanity.models.enums.GameState;
import java.util.List;

public class Game {
    private String id;
    private List<Player> players;
    private Player currentJudge;
    private Card currentQuestion;
    private List<PlayedCard> playedCards;
    private GameState state;
    private int round;
}
