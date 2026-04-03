package cards_against_humanity.infrastructure.persistence;

import cards_against_humanity.domain.model.Card;
import cards_against_humanity.domain.model.enums.CardType;
import cards_against_humanity.domain.repository.CardRepository;
import cards_against_humanity.infrastructure.transaction.Transaction;
import java.util.List;
import java.util.Optional;

public class JpaCardRepository implements CardRepository {

    private final Transaction transaction;

    public JpaCardRepository() {
        this.transaction = new Transaction();
    }

    @Override
    public Card save(Card card) {
        transaction.executeVoid(em -> em.persist(card));
        return card;
    }

    @Override
    public Optional<Card> findById(String id) {
        return transaction.execute(em -> Optional.ofNullable(em.find(Card.class, id)));
    }

    @Override
    public List<Card> findAll() {
        return transaction.execute(em ->
            em.createQuery("SELECT c FROM Card c", Card.class).getResultList()
        );
    }

    @Override
    public void delete(Card card) {
        transaction.executeVoid(em -> {
            Card managed = em.contains(card) ? card : em.merge(card);
            em.remove(managed);
        });
    }

    @Override
    public List<Card> findByType(CardType type) {
        return transaction.execute(em ->
            em.createQuery("SELECT c FROM Card c WHERE c.type = :type", Card.class)
                .setParameter("type", type)
                .getResultList()
        );
    }

    @Override
    public List<Card> findRandomAnswers(int limit) {
        return transaction.execute(em ->
            em.createQuery("SELECT c FROM Card c WHERE c.type = :type ORDER BY RANDOM()", Card.class)
                .setParameter("type", CardType.ANSWER)
                .setMaxResults(limit)
                .getResultList()
        );
    }

    @Override
    public List<Card> findRandomQuestions(int limit) {
        return transaction.execute(em ->
            em.createQuery("SELECT c FROM Card c WHERE c.type = :type ORDER BY RANDOM()", Card.class)
                .setParameter("type", CardType.QUESTION)
                .setMaxResults(limit)
                .getResultList()
        );
    }
}