package cards_against_humanity.models;

import java.util.List;

import javax.smartcardio.Card;

public class Player {
    private User user;
    private int score;
    private List<Card> hand;
    private boolean isJudge;
}
