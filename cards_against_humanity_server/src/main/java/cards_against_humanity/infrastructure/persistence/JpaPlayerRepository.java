package cards_against_humanity.infrastructure.persistence;

import cards_against_humanity.domain.model.Player;
import cards_against_humanity.domain.repository.PlayerRepository;
import cards_against_humanity.infrastructure.transaction.Transaction;
import java.util.List;
import java.util.Optional;

public class JpaPlayerRepository implements PlayerRepository {

    private final Transaction transaction;

    /**
     * Inicia a implementação concreta de Repositório habilitando a injeção da class Transaction.
     */
    public JpaPlayerRepository() {
        this.transaction = new Transaction();
    }

    /**
     * Salva ou atualiza os metadados de uma entidade Player transitoriamente vinculada a uma Sala.
     * Retorna a entidade incorporada ao EntityManager.
     *
     * @param player O Player mapeado.
     * @return O Player salvo sob controle orgânico do JPA.
     */
    @Override
    public Player save(Player player) {
        return transaction.execute(em -> {
            if (em.contains(player)) {
                return player;
            }
            return em.merge(player);
        });
    }

    /**
     * Localiza um Player temporário dentro da sessão através de sua chave privada de escopo.
     *
     * @param id String do Player ID.
     * @return Optional prevenindo erros caso a entidade já tenha caído.
     */
    @Override
    public Optional<Player> findById(String id) {
        return transaction.execute(em -> Optional.ofNullable(em.find(Player.class, id)));
    }

    /**
     * Despeja todas as instâncias persistentes contendo jogadores engajados em salas globalmente.
     *
     * @return List englobando todo o escopo estendido da tabela.
     */
    @Override
    public List<Player> findAll() {
        return transaction.execute(em ->
            em.createQuery("SELECT p FROM Player p", Player.class).getResultList()
        );
    }

    /**
     * Corta a existência de um jogador de um game, mesclando e dando commit na extração.
     *
     * @param player Referência instanciada que sofrerá expurgo.
     */
    @Override
    public void delete(Player player) {
        transaction.executeVoid(em -> {
            Player managed = em.contains(player) ? player : em.merge(player);
            em.remove(managed);
        });
    }

    /**
     * Variante pura da exclusão encurtada, eliminando entidades Player via string ID.
     *
     * @param id A UUID serializada da deleção.
     */
    public void deleteById(String id) {
        transaction.executeVoid(em -> {
            Player player = em.find(Player.class, id);
            if (player != null) {
                em.remove(player);
            }
        });
    }

    /**
     * Promove uma busca baseada no relacionamento de sub-chaves retornando
     * uma Array contendo estritamente participantes ativos em certa Sala (gameId).
     *
     * @param gameId ID do parent da rodada (Game).
     * @return Todos os Players populando o ambiente citado em forma List.
     */
    @Override
    public List<Player> findByGameId(String gameId) {
        return transaction.execute(em ->
            em.createQuery("SELECT p FROM Player p WHERE p.game.id = :gameId", Player.class)
                .setParameter("gameId", gameId)
                .getResultList()
        );
    }

    /**
     * Traz atômica e seletivamente um avatar do perfil User submerso num Lobby.
     * Une e confirma que a Chave do Usuário coincide com a FK do Jogo acessado.
     *
     * @param userId A Chave relacional permanente do usuário no sistema.
     * @param gameId O container / Sala onde ele encontra-se efêmeramente.
     * @return Embalagem Optional com blindagem NullPointer.
     */
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