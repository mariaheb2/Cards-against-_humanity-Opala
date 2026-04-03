package cards_against_humanity.domain.model;

import jakarta.persistence.*;

@Entity
@Table(name = "played_cards")
public class PlayedCard extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Column(nullable = false)
    private boolean winner;

    protected PlayedCard() {
        // JPA
    }

    public PlayedCard(Player player, Card card, Game game) {
        this.player = player;
        this.card = card;
        this.game = game;
        this.winner = false;
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
}