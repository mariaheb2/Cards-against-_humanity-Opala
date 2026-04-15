package cards_against_humanity.application.service;

import cards_against_humanity.domain.model.Card;
import cards_against_humanity.domain.model.Game;
import cards_against_humanity.domain.model.Player;
import cards_against_humanity.domain.model.User;
import cards_against_humanity.domain.model.enums.GameState;
import cards_against_humanity.domain.repository.GameRepository;
import cards_against_humanity.domain.repository.PlayerRepository;
import cards_against_humanity.domain.repository.UserRepository;
import cards_against_humanity.infrastructure.transaction.Transaction;

import java.util.List;
import java.util.logging.Logger;

/**
 * Serviço responsável pelas operações de lobby: Criação de novas partidas,
 * Entrada e saída de jogadores, Verificação de condições para início, Início do
 * jogo (delega para {@link GameService})
 * 
 * Todas as operações são transacionais e utilizam os repositórios injetados.
 *
 * @see GameService
 * @see GameStateService
 */
public class LobbyService {

    private static final Logger LOGGER = Logger.getLogger(LobbyService.class.getName());

    // Pontuação-alvo padrão
    public static final int DEFAULT_TARGET_SCORE = 8;

    // Máximo de jogadores padrão quando o cliente não especifica.
    public static final int DEFAULT_MAX_PLAYERS = 6;

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
     * @param gameService      serviço de orquestração do jogo (para iniciar
     *                         rodadas)
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
     * @param userId      ID do usuário autenticado que está criando a partida
     * @param maxPlayers  número máximo de jogadores (deve ser ≥ 2)
     * @param targetScore pontuação necessária para vencer
     * @return o objeto {@link Game} recém-criado
     * @throws IllegalArgumentException se o usuário não existir
     */
    public Game createGame(String userId, int maxPlayers, int targetScore) {
        return transaction.execute(em -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            Game game = new Game(maxPlayers, targetScore);
            game.setState(GameState.WAITING_PLAYERS);
            game.setOwnerId(userId);
            gameRepository.save(game);

            Player player = new Player(user, game);
            playerRepository.save(player);

            LOGGER.info("Game created: " + game.getId() + " by user: " + userId);
            return game;
        });
    }

    /**
     * Convenience overload que usa {@link #DEFAULT_MAX_PLAYERS} e
     * {@link #DEFAULT_TARGET_SCORE}.
     */
    public Game createGame(String userId, int maxPlayers) {
        return createGame(userId, maxPlayers > 0 ? maxPlayers : DEFAULT_MAX_PLAYERS, DEFAULT_TARGET_SCORE);
    }

    /**
     * Adiciona um jogador a uma partida existente que esteja no estado
     * {@code WAITING_PLAYERS}.
     *
     * Verifica se a partida não está cheia, se o usuário existe e se já não faz
     * parte dela.
     *
     * @param userId ID do usuário que deseja entrar
     * @param gameId ID da partida alvo
     * @return o objeto {@link Player} recém-criado e persistido
     * @throws IllegalArgumentException se a partida ou o usuário não forem
     *                                  encontrados
     * @throws IllegalStateException    se a partida já tiver iniciado, estiver
     *                                  cheia,
     *                                  ou o usuário já estiver na partida
     */
    public Player joinGame(String userId, String gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found"));

        if (game.getState() != GameState.WAITING_PLAYERS) {
            throw new IllegalStateException("Game already started or finished");
        }

        List<Player> currentPlayers = playerRepository.findByGameId(gameId);

        if (currentPlayers.size() >= game.getMaxPlayers()) {
            throw new IllegalStateException("Game is full");
        }

        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        boolean alreadyInThisGame = currentPlayers.stream()
                .anyMatch(p -> p.getUser().getId().equals(userId));
        if (alreadyInThisGame) {
            throw new IllegalStateException("User already joined this game");
        }

        // Persiste o novo jogador buscando instâncias gerenciadas dentro de uma única
        // transação
        return transaction.execute(em -> {
            User managedUser = em.find(User.class, userId);
            Game managedGame = em.find(Game.class, gameId);
            Player player = new Player(managedUser, managedGame);
            em.persist(player);
            return player;
        });
    }

    /**
     * Remove um jogador da partida atual.
     *
     * Se a partida ficar vazia após a remoção, ela é deletada.
     * Se a partida já estiver em andamento, o abandono é registrado (mas a partida
     * continua).
     *
     * @param playerId ID do jogador (entidade {@link Player}) que está saindo
     * @throws IllegalArgumentException se o jogador não existir
     */
    public void leaveGame(String playerId) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found"));
        String gameId = player.getGame() != null ? player.getGame().getId() : null;

        playerRepository.delete(player);

        if (gameId != null && playerRepository.findByGameId(gameId).isEmpty()) {
            gameRepository.findById(gameId).ifPresent(gameRepository::delete);
        }
    }

    /**
     * Verifica se uma partida pode ser iniciada.
     * As condições são: Partida existe e está no estado {@code WAITING_PLAYERS},
     * Número de jogadores ≥ 2 (mínimo para o jogo).
     *
     * @param gameId ID da partida
     * @return {@code true} se pode iniciar, {@code false} caso contrário
     */
    public boolean canStartGame(String gameId) {
        Game game = gameRepository.findById(gameId).orElse(null);
        if (game == null || game.getState() != GameState.WAITING_PLAYERS)
            return false;
        return playerRepository.findByGameId(gameId).size() >= 2;
    }

    /**
     * Inicia o jogo para a partida especificada.
     * 
     * Altera o estado para {@code STARTING} e então delega para
     * {@link GameService#startNewRound(String)} para iniciar a primeira rodada.
     *
     * @param gameId ID da partida
     * @return a carta-pergunta sorteada para a primeira rodada
     * @throws IllegalStateException se as condições de
     *                               {@link #canStartGame(String)} não forem
     *                               satisfeitas
     */
    public Card startGame(String gameId) {
        transaction.executeVoid(em -> {
            if (!canStartGame(gameId)) {
                throw new IllegalStateException("Game cannot be started: not enough players or wrong state");
            }
            Game game = gameRepository.findById(gameId).get();
            game.setState(GameState.STARTING);
            gameRepository.save(game);
        });

        try {
            // Inicia a primeira rodada fora da transação para evitar longa duração
            LOGGER.info("Starting first round for game: " + gameId);
            return gameService.startNewRound(gameId);
        } catch (Exception e) {
            LOGGER.severe("Failed to start first round, reverting state. Error: " + e.getMessage());
            // Reverte o estado para WAITING_PLAYERS se houver falha (ex: sem cartas)
            transaction.executeVoid(em -> {
                gameRepository.findById(gameId).ifPresent(game -> {
                    game.setState(GameState.WAITING_PLAYERS);
                    gameRepository.save(game);
                });
            });
            throw e;
        }
    }

    /**
     * Retorna a lista de jogadores associados a uma partida.
     *
     * @param gameId ID da partida
     * @return lista de {@link Player} (pode estar vazia se a partida não existir ou
     *         não tiver jogadores)
     */
    public List<Player> getPlayersInGame(String gameId) {
        return playerRepository.findByGameId(gameId);
    }

    public Game getGameInfo(String gameId) {
        return gameRepository.findById(gameId).orElse(null);
    }

    public List<Game> listActiveGames() {
        return gameRepository.findByState(GameState.WAITING_PLAYERS);
    }

    public Game getGameById(String gameId) {
        return gameRepository.findById(gameId).orElse(null);
    }

    public void leaveGame(String userId, String gameId) {
        transaction.executeVoid(em -> {
            Player player = playerRepository.findByUserIdAndGameId(userId, gameId)
                    .orElseThrow(() -> new IllegalArgumentException("Jogador não está na sala"));
            playerRepository.delete(player);
            // Se a sala ficou vazia, deletar
            if (playerRepository.findByGameId(gameId).isEmpty()) {
                gameRepository.findById(gameId).ifPresent(gameRepository::delete);
            }
        });
    }
}