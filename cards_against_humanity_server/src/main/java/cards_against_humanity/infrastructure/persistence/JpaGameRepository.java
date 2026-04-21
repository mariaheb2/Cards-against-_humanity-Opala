package cards_against_humanity.infrastructure.persistence;

import cards_against_humanity.domain.model.Game;
import cards_against_humanity.domain.model.enums.GameState;
import cards_against_humanity.domain.repository.GameRepository;
import cards_against_humanity.infrastructure.transaction.Transaction;

import java.util.List;
import java.util.Optional;

public class JpaGameRepository implements GameRepository {

    private final Transaction transaction;

    /**
     * Construtor da implementação do Repositório. Inicializa a ponte de transações locais.
     */
    public JpaGameRepository() {
        this.transaction = new Transaction();
    }

    /**
     * Atualiza um jogo modificado ou insere um novo no banco de dados.
     * Executado sob um contexto managed. Retorna a entidade acoplada.
     *
     * @param game O objeto do tipo Game com dados prontos.
     * @return Game rastreado pelo ORM.
     */
    @Override
    public Game save(Game game) {
        return transaction.execute(em -> {
            if (em.contains(game)) {
                return game; // already managed
            }
            return em.merge(game);
        });
    }

    /**
     * Consulta atômica isolada pegando um jogo baseado na sua PK UUID.
     *
     * @param id String do gameId / gameCode.
     * @return Instância Optional para prevenção amigável de Nulls.
     */
    @Override
    public Optional<Game> findById(String id) {
        return transaction.execute(em -> Optional.ofNullable(em.find(Game.class, id)));
    }

    /**
     * Requisita um apanhado contendo estritamente todas as linhas da tabela de jogos.
     *
     * @return Lista das rodadas recuperadas.
     */
    @Override
    public List<Game> findAll() {
        return transaction.execute(em ->
            em.createQuery("SELECT g FROM Game g", Game.class).getResultList()
        );
    }

    /**
     * Desintegra as restrições e relacionamentos removendo a chave instanciada do banco.
     * Realiza um merge momentâneo se o elemento se soltou do Entity context (detached) antes de apagar.
     *
     * @param game A referência.
     */
    @Override
    public void delete(Game game) {
        transaction.executeVoid(em -> {
            Game managed = em.contains(game) ? game : em.merge(game);
            em.remove(managed);
        });
    }

    /**
     * Expurga uma rodada sob comando estrito de seu ID evitando trânsito demorado do objeto inteiro em rede para remover.
     *
     * @param id UUID texto limpo da PK do jogo.
     */
    public void deleteById(String id) {
        transaction.executeVoid(em -> {
            Game game = em.find(Game.class, id);
            if (game != null) {
                em.remove(game);
            }
        });
    }

    /**
     * Filtra eficientemente e converte as buscas atrelando o Enum de situação
     * à filtragem no SELECT da query do banco.
     *
     * @param state O Status atual, por exemplo PLAYERS_PLAYING.
     * @return Jogos detectados no status apontado.
     */
    @Override
    public List<Game> findByState(GameState state) {
        return transaction.execute(em ->
            em.createQuery("SELECT g FROM Game g WHERE g.state = :state", Game.class)
                .setParameter("state", state)
                .getResultList()
        );
    }

    /**
     * Identificando relações profundas, varre a Join table g.players em cruzamento
     * atrás da sala on-line em que certo Player encontra-se associado.
     *
     * @param playerId UUID de banco do Player listado num jogo.
     * @return O respectivo palco da partida sob Optional de uso seguro.
     */
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