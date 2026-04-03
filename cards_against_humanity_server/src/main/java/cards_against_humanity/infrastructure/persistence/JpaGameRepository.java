package cards_against_humanity.infrastructure.persistence;

import cards_against_humanity.domain.model.Game;
import cards_against_humanity.domain.model.enums.GameState;
import cards_against_humanity.domain.repository.GameRepository;
import cards_against_humanity.infrastructure.transaction.Transaction;

import java.util.List;
import java.util.Optional;

public class JpaGameRepository implements GameRepository {

    private final Transaction transaction;

    public JpaGameRepository() {
        this.transaction = new Transaction();
    }

    @Override
    public Game save(Game game) {
        transaction.executeVoid(em -> em.persist(game));
        return game;
    }

    @Override
    public Optional<Game> findById(String id) {
        return transaction.execute(em -> Optional.ofNullable(em.find(Game.class, id)));
    }

    @Override
    public List<Game> findAll() {
        return transaction.execute(em ->
            em.createQuery("SELECT g FROM Game g", Game.class).getResultList()
        );
    }

    @Override
    public void delete(Game game) {
        transaction.executeVoid(em -> {
            Game managed = em.contains(game) ? game : em.merge(game);
            em.remove(managed);
        });
    }

    public void deleteById(String id) {
        transaction.executeVoid(em -> {
            Game game = em.find(Game.class, id);
            if (game != null) {
                em.remove(game);
            }
        });
    }

    @Override
    public List<Game> findByState(GameState state) {
        return transaction.execute(em ->
            em.createQuery("SELECT g FROM Game g WHERE g.state = :state", Game.class)
                .setParameter("state", state)
                .getResultList()
        );
    }

    @Override
    public Optional<Game> findByPlayerId(String playerId) {
        return transaction.execute(em -> {
            List<Game> games = em.createQuery(
                    "SELECT g FROM Game g JOIN g.players p WHERE p.id = :playerId", Game.class)
                .setParameter("playerId", playerId)
                .getResultList();
            return games.stream().findFirst();
        });
    }        
}