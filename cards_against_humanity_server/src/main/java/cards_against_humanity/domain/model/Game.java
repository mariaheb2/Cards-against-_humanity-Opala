package cards_against_humanity.domain.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

import cards_against_humanity.domain.model.enums.GameState;

@Entity
@Table(name = "games")
public class Game extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GameState state;

    @Column(nullable = false)
    private int round;

    @Column(name = "max_players", nullable = false)
    private int maxPlayers;

    @Column(name = "target_score", nullable = false)
    private int targetScore;

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Player> players = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_judge_id")
    private Player currentJudge;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_question_id")
    private Card currentQuestion;

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlayedCard> playedCards = new ArrayList<>();

    protected Game() {
        // JPA
    }

    public Game(int maxPlayers, int targetScore) {
        this.state = GameState.WAITING_PLAYERS;
        this.round = 0;
        this.maxPlayers = maxPlayers;
        this.targetScore = targetScore;
    }

    // Getters e setters para campos que podem ser alterados pelo domínio
    public GameState getState() {
        return state;
    }

    public void setState(GameState state) {
        this.state = state;
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public int getTargetScore() {
        return targetScore;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public Player getCurrentJudge() {
        return currentJudge;
    }

    public void setCurrentJudge(Player currentJudge) {
        this.currentJudge = currentJudge;
    }

    public Card getCurrentQuestion() {
        return currentQuestion;
    }

    public void setCurrentQuestion(Card currentQuestion) {
        this.currentQuestion = currentQuestion;
    }

    public List<PlayedCard> getPlayedCards() {
        return playedCards;
    }

}