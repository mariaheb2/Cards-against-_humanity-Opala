package cards_against_humanity.infrastructure.persistence;

import cards_against_humanity.domain.model.PlayedCard;
import cards_against_humanity.domain.repository.PlayedCardRepository;
import cards_against_humanity.infrastructure.transaction.Transaction;
import java.util.List;
import java.util.Optional;

public class JpaPlayedCardRepository implements PlayedCardRepository {

    private final Transaction transaction;

    /**
     * Instancia o provedor de conexões do Repositório de submissões de cartas.
     */
    public JpaPlayedCardRepository() {
        this.transaction = new Transaction();
    }

    /**
     * Salva o ato de um jogador colocar uma carta branca na mesa para a rodada.
     * Retorna o wrapper ORM seguro em merge ou tracking.
     *
     * @param playedCard A jogada gerada no GameService.
     * @return O registro persistido da ação.
     */
    @Override
    public PlayedCard save(PlayedCard playedCard) {
        return transaction.execute(em -> {
            if (em.contains(playedCard)) {
                return playedCard;
            }
            return em.merge(playedCard);
        });
    }

    /**
     * Busca uma carta jogada (Table) específica através da PK associada.
     *
     * @param id String do UUID da rodada.
     * @return Envelope Optional para contornar retornos nulos.
     */
    @Override
    public Optional<PlayedCard> findById(String id) {
        return transaction.execute(em -> Optional.ofNullable(em.find(PlayedCard.class, id)));
    }

    /**
     * Adquire da base uma lista histórica completa global de jogadas em todas as salas já feitas.
     *
     * @return Resumo irrestrito de jogadas na tabela final.
     */
    @Override
    public List<PlayedCard> findAll() {
        return transaction.execute(em ->
            em.createQuery("SELECT pc FROM PlayedCard pc", PlayedCard.class).getResultList()
        );
    }

    /**
     * Permite apagar registros de jogadas se baseando em um model em memória instanciado.
     * Mescla-o à persistence context para poder realizar o remove final no flush de tabelas.
     *
     * @param playedCard Jogada que será anulada permanentemente.
     */
    @Override
    public void delete(PlayedCard playedCard) {
        transaction.executeVoid(em -> {
            PlayedCard managed = em.contains(playedCard) ? playedCard : em.merge(playedCard);
            em.remove(managed);
        });
    }

    /**
     * Forma de deletar via query string direta economizando etapas de recuperação.
     *
     * @param id String crua de identificação do played_card.
     */
    public void deleteById(String id) {
        transaction.executeVoid(em -> {
            PlayedCard playedCard = em.find(PlayedCard.class, id);
            if (playedCard != null) {
                em.remove(playedCard);
            }
        });
    }

    /**
     * Restringe as respostas brancas na mesa para apenas aquelas lançadas pertencentes
     * à Sala de jogo (Game) especificada no parâmetro. Usado para agrupar opções do Tsar.
     *
     * @param gameId Sala alvo a ser filtrada nos relacionamentos estruturais.
     * @return Lista das cartas brancas sobre a mesa aguardando juízo.
     */
    @Override
    public List<PlayedCard> findByGameId(String gameId) {
        return transaction.execute(em ->
            em.createQuery("SELECT pc FROM PlayedCard pc WHERE pc.game.id = :gameId", PlayedCard.class)
                .setParameter("gameId", gameId)
                .getResultList()
        );
    }

    /**
     * Detecta dentro de uma sala exata (gameId) a presença de uma carta de rodada marcada como flag Vencedora.
     * O Tsar comuta booleanamente esse fator que reflete nesse filter no momento da consagração.
     *
     * @param gameId ID do parent (Sala/Jogo).
     * @return A carta eleita pelo Tsar blindada no tipo Optional.
     */
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