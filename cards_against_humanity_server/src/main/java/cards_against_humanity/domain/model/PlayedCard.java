package cards_against_humanity.domain.model;

import jakarta.persistence.*;

@Entity
@Table(name = "played_cards")
public class PlayedCard extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Column(nullable = false)
    private boolean winner;

    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    protected PlayedCard() {
        // JPA
    }

    public PlayedCard(Player player, Card card, Game game) {
        this.player = player;
        this.card = card;
        this.game = game;
        this.winner = false;
        this.roundNumber = 0; 
    }

    // Getters
    public Player getPlayer() {
        return player;
    }

    public Card getCard() {
        return card;
    }

    public Game getGame() {
        return game;
    }

    public boolean isWinner() {
        return winner;
    }

    public void setWinner(boolean winner) {
        this.winner = winner;
    }

    public int getRoundNumber() { 
        return roundNumber; 
    }
    public void setRoundNumber(int roundNumber) { 
        this.roundNumber = roundNumber; 
    }
}