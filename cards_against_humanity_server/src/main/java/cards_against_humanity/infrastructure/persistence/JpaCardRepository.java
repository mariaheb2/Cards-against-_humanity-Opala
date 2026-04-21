package cards_against_humanity.infrastructure.persistence;

import cards_against_humanity.domain.model.Card;
import cards_against_humanity.domain.model.enums.CardType;
import cards_against_humanity.domain.repository.CardRepository;
import cards_against_humanity.infrastructure.transaction.Transaction;
import java.util.List;
import java.util.Optional;

public class JpaCardRepository implements CardRepository {

    private final Transaction transaction;

    /**
     * Inicializa a transação base para repositório do deck de cartas.
     */
    public JpaCardRepository() {
        this.transaction = new Transaction();
    }

    /**
     * Persiste as Cartas Customizadas (ou semeia as do sistema base).
     *
     * @param card Carta com conteúdo de question ou answer.
     * @return O próprio conteúdo confirmado internamente gerenciado.
     */
    @Override
    public Card save(Card card) {
        transaction.executeVoid(em -> em.persist(card));
        return card;
    }

    /**
     * Encontra uma carta exata na base usando seu UUID.
     *
     * @param id String chave-primária.
     * @return Optional prevenindo estourar Exception no caso de cartas removidas.
     */
    @Override
    public Optional<Card> findById(String id) {
        return transaction.execute(em -> Optional.ofNullable(em.find(Card.class, id)));
    }

    /**
     * Resgata de uma vez só o baralho completo sem distinção de tipo (Deck unificado).
     *
     * @return Coleção contendo todo o montante das tabelas brancas/pretas.
     */
    @Override
    public List<Card> findAll() {
        return transaction.execute(em ->
            em.createQuery("SELECT c FROM Card c", Card.class).getResultList()
        );
    }

    /**
     * Remove uma carta do banco, fundindo a instância para transição antes da remoção final.
     *
     * @param card O objeto a expurgar permanentemente.
     */
    @Override
    public void delete(Card card) {
        transaction.executeVoid(em -> {
            Card managed = em.contains(card) ? card : em.merge(card);
            em.remove(managed);
        });
    }

    /**
     * Divide a listagem de cartas entre pretas (QUESTION) e brancas (ANSWER).
     *
     * @param type Enum restrito das famílias de cartas.
     * @return Conjunto compatível com a cor especificada.
     */
    @Override
    public List<Card> findByType(CardType type) {
        return transaction.execute(em ->
            em.createQuery("SELECT c FROM Card c WHERE c.type = :type", Card.class)
                .setParameter("type", type)
                .getResultList()
        );
    }

    /**
     * Traz um lote totalmente misturado de respostas da tabela para distribuição nas rodadas do jogo
     * por meio do critério de RAND do banco SQL associado aos limitadores (Limit).
     *
     * @param limit Quantas respostas no total sacar do monte cego.
     * @return A mão de cartas para o player.
     */
    @Override
    public List<Card> findRandomAnswers(int limit) {
        return transaction.execute(em ->
            em.createQuery("SELECT c FROM Card c WHERE c.type = :type ORDER BY RAND()", Card.class)
                .setParameter("type", CardType.ANSWER)
                .setMaxResults(limit)
                .getResultList()
        );
    }

    /**
     * Pesca X cartas pretas da base global aleatórias, idealmente compondo o objetivo da mesa.
     *
     * @param limit Limite superior de volume exigido.
     * @return Uma ou mais questões escolhidas de maneira equiprovável na sintaxe MySQL.
     */
    @Override
    public List<Card> findRandomQuestions(int limit) {
        return transaction.execute(em ->
            em.createQuery("SELECT c FROM Card c WHERE c.type = :type ORDER BY RAND()", Card.class)
                .setParameter("type", CardType.QUESTION)
                .setMaxResults(limit)
                .getResultList()
        );
    }
}