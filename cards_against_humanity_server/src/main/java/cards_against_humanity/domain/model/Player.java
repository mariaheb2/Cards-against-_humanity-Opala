package cards_against_humanity.domain.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "players")
public class Player extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Column
    private int score;

    @Column(name = "is_judge", nullable = false)
    private boolean isJudge;

    @ManyToMany
    @JoinTable(
        name = "player_hand",
        joinColumns = @JoinColumn(name = "player_id"),
        inverseJoinColumns = @JoinColumn(name = "card_id")
    )
    private List<Card> hand = new ArrayList<>();

    protected Player() {
        // JPA
    }

    public Player(User user, Game game) {
        this.user = user;
        this.game = game;
        this.score = 0;
        this.isJudge = false;
    }

    // Getters e setters
    public User getUser() {
        return user;
    }

    public Game getGame() {
        return game;
    }

    void setGame(Game game) {
        this.game = game;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public boolean isJudge() {
        return isJudge;
    }

    public void setJudge(boolean judge) {
        isJudge = judge;
    }

    public List<Card> getHand() {
        return hand;
    }

    // Métodos de domínio
    public void addCardToHand(Card card) {
        hand.add(card);
    }

    public void removeCardFromHand(Card card) {
        hand.remove(card);
    }

    public void addPoints(int points) {
        this.score += points;
    }
}