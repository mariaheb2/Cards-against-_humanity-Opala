package cards_against_humanity.infrastructure.persistence;

import cards_against_humanity.domain.model.Player;
import cards_against_humanity.domain.repository.PlayerRepository;
import cards_against_humanity.infrastructure.transaction.Transaction;
import java.util.List;
import java.util.Optional;

public class JpaPlayerRepository implements PlayerRepository {

    private final Transaction transaction;

    public JpaPlayerRepository() {
        this.transaction = new Transaction();
    }

    @Override
    public Player save(Player player) {
        return transaction.execute(em -> {
            if (em.contains(player)) {
                return player;
            }
            return em.merge(player);
        });
    }

    @Override
    public Optional<Player> findById(String id) {
        return transaction.execute(em -> Optional.ofNullable(em.find(Player.class, id)));
    }

    @Override
    public List<Player> findAll() {
        return transaction.execute(em ->
            em.createQuery("SELECT p FROM Player p", Player.class).getResultList()
        );
    }

    @Override
    public void delete(Player player) {
        transaction.executeVoid(em -> {
            Player managed = em.contains(player) ? player : em.merge(player);
            em.remove(managed);
        });
    }

    public void deleteById(String id) {
        transaction.executeVoid(em -> {
            Player player = em.find(Player.class, id);
            if (player != null) {
                em.remove(player);
            }
        });
    }

    @Override
    public List<Player> findByGameId(String gameId) {
        return transaction.execute(em ->
            em.createQuery("SELECT p FROM Player p WHERE p.game.id = :gameId", Player.class)
                .setParameter("gameId", gameId)
                .getResultList()
        );
    }

    @Override
    public Optional<Player> findByUserIdAndGameId(String userId, String gameId) {
        return transaction.execute(em -> {
            List<Player> players = em.createQuery(
                    "SELECT p FROM Player p WHERE p.user.id = :userId AND p.game.id = :gameId", Player.class)
                .setParameter("userId", userId)
                .setParameter("gameId", gameId)
                .getResultList();
            return players.stream().findFirst();
        });
    }
}