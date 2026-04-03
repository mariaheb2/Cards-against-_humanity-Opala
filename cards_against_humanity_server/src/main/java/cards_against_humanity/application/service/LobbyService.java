package cards_against_humanity.application.service;

import cards_against_humanity.domain.model.Game;
import cards_against_humanity.domain.model.Player;
import cards_against_humanity.domain.model.User;
import cards_against_humanity.domain.model.enums.GameState;
import cards_against_humanity.domain.repository.GameRepository;
import cards_against_humanity.domain.repository.PlayerRepository;
import cards_against_humanity.domain.repository.UserRepository;
import cards_against_humanity.infrastructure.transaction.Transaction;

import java.util.List;

import org.hibernate.Remove;
/**
 * Serviço responsável pelas operações de lobby:
 * <ul>
 *   <li>Criação de novas partidas</li>
 *   <li>Entrada e saída de jogadores</li>
 *   <li>Verificação de condições para início</li>
 *   <li>Início do jogo (delega para {@link GameService})</li>
 * </ul>
 *
 * <p>Todas as operações são transacionais e utilizam os repositórios injetados.
 *
 * @see GameService
 * @see GameStateService
 */
public class LobbyService {

    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;
    private final UserRepository userRepository;
    private final GameService gameService;
    private final Transaction transaction;

    /**
     * Constrói um novo LobbyService com as dependências necessárias.
     *
     * @param gameRepository   repositório de partidas
     * @param playerRepository repositório de jogadores
     * @param userRepository   repositório de usuários
     * @param gameService      serviço de orquestração do jogo (para iniciar rodadas)
     */
    public LobbyService(GameRepository gameRepository,
                        PlayerRepository playerRepository,
                        UserRepository userRepository,
                        GameService gameService) {
        this.gameRepository = gameRepository;
        this.playerRepository = playerRepository;
        this.userRepository = userRepository;
        this.gameService = gameService;
        this.transaction = new Transaction();
    }

    /**
     * Cria uma nova partida com as configurações especificadas.
     * O criador é automaticamente adicionado como primeiro jogador.
     *
     * <p>A partida é criada no estado {@code WAITING_PLAYERS}.
     *
     * @param userId       ID do usuário que está criando a partida
     * @param maxPlayers   número máximo de jogadores (deve ser ≥ 2)
     * @param targetScore  pontuação necessária para vencer (padrão 6)
     * @return o ID da partida recém-criada
     * @throws IllegalArgumentException se o usuário não existir
     * @throws IllegalStateException    se o usuário já estiver em outra partida ativa
     */
    public String createGame(String userId, int maxPlayers, int targetScore) {
        return transaction.execute(em -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            // Verifica se usuário já está em um jogo ativo (não finalizado)
            boolean alreadyInGame = gameRepository.findByPlayerId(userId).isPresent();
            if (alreadyInGame) {
                throw new IllegalStateException("User is already in an active game");
            }

            Game game = new Game(maxPlayers, targetScore);
            game.setState(GameState.WAITING_PLAYERS);
            gameRepository.save(game);

            Player player = new Player(user, game);
            playerRepository.save(player);

            return game.getId();
        });
    }

    /**
     * Adiciona um jogador a uma partida existente que esteja no estado {@code WAITING_PLAYERS}.
     *
     * <p>Verifica se a partida não está cheia, se o usuário existe e se já não faz parte dela.
     *
     * @param userId ID do usuário que deseja entrar
     * @param gameId ID da partida alvo
     * @return o objeto {@link Player} recém-criado e persistido
     * @throws IllegalArgumentException se a partida ou o usuário não forem encontrados
     * @throws IllegalStateException    se a partida já tiver iniciado, estiver cheia,
     *                                  ou o usuário já estiver na partida
     */
    public Player joinGame(String userId, String gameId) {
        return transaction.execute(em -> {
            Game game = gameRepository.findById(gameId)
                    .orElseThrow(() -> new IllegalArgumentException("Game not found"));

            if (game.getState() != GameState.WAITING_PLAYERS) {
                throw new IllegalStateException("Game already started or finished");
            }

            if (game.getPlayers().size() >= game.getMaxPlayers()) {
                throw new IllegalStateException("Game is full");
            }

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            // Verifica se usuário já está neste game
            boolean alreadyInThisGame = game.getPlayers().stream()
                    .anyMatch(p -> p.getUser().getId().equals(userId));
            if (alreadyInThisGame) {
                throw new IllegalStateException("User already joined this game");
            }

            Player player = new Player(user, game);
            playerRepository.save(player);
            return player;
        });
    }

    /**
     * Remove um jogador da partida atual.
     *
     * <p>Se a partida ficar vazia após a remoção, ela é deletada.
     * Se a partida já estiver em andamento, o abandono é registrado (mas a partida continua).
     *
     * @param playerId ID do jogador (entidade {@link Player}) que está saindo
     * @throws IllegalArgumentException se o jogador não existir
     */
    public void leaveGame(String playerId) {
        transaction.executeVoid(em -> {
            Player player = playerRepository.findById(playerId)
                    .orElseThrow(() -> new IllegalArgumentException("Player not found"));
            Game game = player.getGame();

            playerRepository.delete(player);

            if(game.getPlayers().equals(2)){
                gameRepository.delete(game);
            } else if (game.getPlayers().isEmpty()) {
                gameRepository.delete(game);
            }
        });
    }

    /**
     * Verifica se uma partida pode ser iniciada.
     * As condições são:
     * <ul>
     *   <li>Partida existe e está no estado {@code WAITING_PLAYERS}</li>
     *   <li>Número de jogadores ≥ 3 (mínimo para o jogo)</li>
     * </ul>
     *
     * @param gameId ID da partida
     * @return {@code true} se pode iniciar, {@code false} caso contrário
     */
    public boolean canStartGame(String gameId) {
        return transaction.execute(em -> {
            Game game = gameRepository.findById(gameId).orElse(null);
            return game != null &&
                   game.getState() == GameState.WAITING_PLAYERS &&
                   game.getPlayers().size() >= 3;
        });
    }

    /**
     * Inicia o jogo para a partida especificada.
     *
     * <p>Altera o estado para {@code STARTING_GAME} e então delega para
     * {@link GameService#startNewRound(String)} para iniciar a primeira rodada.
     *
     * @param gameId ID da partida
     * @throws IllegalStateException se as condições de {@link #canStartGame(String)} não forem satisfeitas
     */
    public void startGame(String gameId) {
        transaction.executeVoid(em -> {
            if (!canStartGame(gameId)) {
                throw new IllegalStateException("Game cannot be started: not enough players or wrong state");
            }
            Game game = gameRepository.findById(gameId).get();
            game.setState(GameState.STARTING);
            gameRepository.save(game);
        });
        // Inicia a primeira rodada fora da transação para evitar longa duração
        gameService.startNewRound(gameId);
    }

    /**
     * Retorna a lista de jogadores associados a uma partida.
     *
     * @param gameId ID da partida
     * @return lista de {@link Player} (pode estar vazia se a partida não existir ou não tiver jogadores)
     */
    public List<Player> getPlayersInGame(String gameId) {
        return playerRepository.findByGameId(gameId);
    }
}