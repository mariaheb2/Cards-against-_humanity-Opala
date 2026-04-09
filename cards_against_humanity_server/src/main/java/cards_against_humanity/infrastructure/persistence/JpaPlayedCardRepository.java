package cards_against_humanity.infrastructure.persistence;

import cards_against_humanity.domain.model.PlayedCard;
import cards_against_humanity.domain.repository.PlayedCardRepository;
import cards_against_humanity.infrastructure.transaction.Transaction;
import java.util.List;
import java.util.Optional;

public class JpaPlayedCardRepository implements PlayedCardRepository {

    private final Transaction transaction;

    public JpaPlayedCardRepository() {
        this.transaction = new Transaction();
    }

    @Override
    public PlayedCard save(PlayedCard playedCard) {
        return transaction.execute(em -> {
            if (em.contains(playedCard)) {
                return playedCard;
            }
            return em.merge(playedCard);
        });
    }

    @Override
    public Optional<PlayedCard> findById(String id) {
        return transaction.execute(em -> Optional.ofNullable(em.find(PlayedCard.class, id)));
    }

    @Override
    public List<PlayedCard> findAll() {
        return transaction.execute(em ->
            em.createQuery("SELECT pc FROM PlayedCard pc", PlayedCard.class).getResultList()
        );
    }

    @Override
    public void delete(PlayedCard playedCard) {
        transaction.executeVoid(em -> {
            PlayedCard managed = em.contains(playedCard) ? playedCard : em.merge(playedCard);
            em.remove(managed);
        });
    }

    public void deleteById(String id) {
        transaction.executeVoid(em -> {
            PlayedCard playedCard = em.find(PlayedCard.class, id);
            if (playedCard != null) {
                em.remove(playedCard);
            }
        });
    }

    @Override
    public List<PlayedCard> findByGameId(String gameId) {
        return transaction.execute(em ->
            em.createQuery("SELECT pc FROM PlayedCard pc WHERE pc.game.id = :gameId", PlayedCard.class)
                .setParameter("gameId", gameId)
                .getResultList()
        );
    }

    @Override
    public Optional<PlayedCard> findWinnerByGameId(String gameId) {
        return transaction.execute(em -> {
            List<PlayedCard> winners = em.createQuery(
                    "SELECT pc FROM PlayedCard pc WHERE pc.game.id = :gameId AND pc.winner = true", PlayedCard.class)
                .setParameter("gameId", gameId)
                .getResultList();
            return winners.stream().findFirst();
        });
    }
}