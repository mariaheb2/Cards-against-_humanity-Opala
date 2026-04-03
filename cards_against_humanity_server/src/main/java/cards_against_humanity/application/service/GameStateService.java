package cards_against_humanity.application.service;

import cards_against_humanity.domain.model.Game;
import cards_against_humanity.domain.model.Player;
import cards_against_humanity.domain.model.enums.GameState;
import cards_against_humanity.domain.repository.GameRepository;
import cards_against_humanity.domain.repository.PlayedCardRepository;
import cards_against_humanity.infrastructure.transaction.Transaction;

import java.util.List;

/**
 * Serviço responsável pela máquina de estados do jogo.
 * <p>
 * Fornece métodos para:
 * <ul>
 *   <li>Validar e executar transições entre estados ({@link GameState})</li>
 *   <li>Verificar se uma ação é permitida em determinado estado</li>
 *   <li>Consultar o estado atual de uma partida</li>
 *   <li>Determinar se todos os jogadores já jogaram</li>
 *   <li>Calcular o próximo juiz (ordem circular)</li>
 * </ul>
 *
 * <p>As regras de transição estão encapsuladas no método privado {@link #isValidTransition(GameState, GameState)}.
 *
 * @see GameService
 * @see LobbyService
 */
public class GameStateService {

    private final GameRepository gameRepository;
    private final PlayedCardRepository playedCardRepository;
    private final Transaction transaction;

    /**
     * Constrói um GameStateService com os repositórios necessários.
     *
     * @param gameRepository       repositório de partidas
     * @param playedCardRepository repositório de cartas jogadas (necessário para verificar se todos jogaram)
     */
    public GameStateService(GameRepository gameRepository,
                            PlayedCardRepository playedCardRepository) {
        this.gameRepository = gameRepository;
        this.playedCardRepository = playedCardRepository;
        this.transaction = new Transaction();
    }

    /**
     * Altera o estado de um jogo, desde que a transição seja válida.
     *
     * @param gameId   ID da partida
     * @param newState novo estado desejado
     * @return {@code true} se a mudança foi aplicada com sucesso
     * @throws IllegalArgumentException se a partida não existir
     * @throws IllegalStateException    se a transição do estado atual para {@code newState} não for permitida
     * @see #isValidTransition(GameState, GameState)
     */
    public boolean changeState(String gameId, GameState newState) {
        return transaction.execute(em -> {
            Game game = gameRepository.findById(gameId)
                    .orElseThrow(() -> new IllegalArgumentException("Game not found"));
            GameState oldState = game.getState();
            if (isValidTransition(oldState, newState)) {
                game.setState(newState);
                gameRepository.save(game);
                return true;
            }
            throw new IllegalStateException(
                    String.format("Invalid state transition from %s to %s", oldState, newState));
        });
    }

        /**
     * Verifica se uma determinada ação pode ser executada no estado atual da partida.
     *
     * <p>Ações reconhecidas:
     * <ul>
     *   <li>{@code "PLAY_CARD"} -> permitido apenas em {@code PLAYERS_PLAYING}</li>
     *   <li>{@code "SELECT_WINNER"} -> permitido apenas em {@code JUDGE_SELECTING}</li>
     *   <li>{@code "START_GAME"} -> permitido apenas em {@code WAITING_PLAYERS}</li>
     *   <li>{@code "JOIN_GAME"} -> permitido apenas em {@code WAITING_PLAYERS}</li>
     *   <li>{@code "NEW_ROUND"} -> permitido em {@code ROUND_FINISHED} ou {@code STARTING_GAME}</li>
     * </ul>
     *
     * @param gameId ID da partida
     * @param action nome da ação (case-sensitive, conforme as constantes acima)
     * @return {@code true} se a ação é permitida, {@code false} caso contrário
     *         (incluindo partida não encontrada)
     */
    public boolean isActionAllowed(String gameId, String action) {
        Game game = gameRepository.findById(gameId).orElse(null);
        if (game == null) return false;
        GameState state = game.getState();
        return switch (action) {
            case "PLAY_CARD" -> state == GameState.PLAYERS_PLAYING;
            case "SELECT_WINNER" -> state == GameState.JUDGE_SELECTING;
            case "START_GAME" -> state == GameState.WAITING_PLAYERS;
            case "JOIN_GAME" -> state == GameState.WAITING_PLAYERS;
            case "NEW_ROUND" -> state == GameState.ROUND_FINISHED || state == GameState.STARTING;
            default -> false;
        };
    }

    /**
     * Retorna o estado atual de uma partida.
     *
     * @param gameId ID da partida
     * @return o {@link GameState} atual, ou {@code null} se a partida não existir
     */
    public GameState getCurrentState(String gameId) {
        return transaction.execute(em ->
            gameRepository.findById(gameId)
                .map(Game::getState)
                .orElse(null)
        );
    }

    /**
     * Retorna o estado atual de uma partida.
     *
     * @param gameId ID da partida
     * @return o {@link GameState} atual, ou {@code null} se a partida não existir
     */
    public boolean allPlayersHavePlayed(String gameId) {
        return transaction.execute(em -> {
            Game game = gameRepository.findById(gameId).orElse(null);
            if (game == null) return false;
            long nonJudges = game.getPlayers().stream().filter(p -> !p.isJudge()).count();
            long playedCount = playedCardRepository.findByGameId(gameId).stream()
                    .filter(pc -> pc.getRoundNumber() == game.getRound())
                    .count();
            return playedCount == nonJudges;
        });
    }

    /**
     * Calcula qual será o próximo juiz da rodada, seguindo a ordem circular
     * dos jogadores na partida.
     *
     * <p>Se ainda não houver juiz atual (primeira rodada), retorna um jogador aleatório.
     *
     * @param gameId ID da partida
     * @return o {@link Player} que será o juiz na próxima rodada
     * @throws IllegalArgumentException se a partida não existir
     * @throws IllegalStateException    se a lista de jogadores estiver vazia
     */
    public Player getNextJudge(String gameId) {
        return transaction.execute(em -> {
            Game game = gameRepository.findById(gameId)
                    .orElseThrow(() -> new IllegalArgumentException("Game not found"));
            List<Player> players = game.getPlayers();
            if (players.isEmpty()) {
                throw new IllegalStateException("No players in game");
            }
            Player currentJudge = game.getCurrentJudge();
            if (currentJudge == null) {
                // Aleatório para primeira rodada
                int randomIndex = (int) (Math.random() * players.size());
                return players.get(randomIndex);
            }
            int currentIndex = players.indexOf(currentJudge);
            int nextIndex = (currentIndex + 1) % players.size();
            return players.get(nextIndex);
        });
    }

    /**
     * Define as transições de estado válidas conforme as regras do jogo.
     * <p>
     * Mapa de transições permitidas:
     * <pre>
     * WAITING_PLAYERS    -> STARTING_GAME
     * STARTING_GAME      -> ROUND_STARTED, PLAYERS_PLAYING
     * ROUND_STARTED      -> PLAYERS_PLAYING
     * PLAYERS_PLAYING    -> JUDGE_SELECTING
     * JUDGE_SELECTING    -> UPDATING_SCORE
     * UPDATING_SCORE     -> ROUND_FINISHED
     * ROUND_FINISHED     -> CHECKGAME_FINISHED
     * CHECKGAME_FINISHED -> ROUND_STARTED, GAME_FINISHED
     * GAME_FINISHED      -> (nenhuma, estado terminal)
     * </pre>
     *
     * @param from estado de origem
     * @param to   estado de destino
     * @return {@code true} se a transição for permitida, {@code false} caso contrário
     */
    private boolean isValidTransition(GameState from, GameState to) {
        return switch (from) {
            case WAITING_PLAYERS -> to == GameState.STARTING;
            case STARTING -> to == GameState.ROUND_STARTED || to == GameState.PLAYERS_PLAYING;
            case ROUND_STARTED -> to == GameState.PLAYERS_PLAYING;
            case PLAYERS_PLAYING -> to == GameState.JUDGE_SELECTING;
            case JUDGE_SELECTING -> to == GameState.UPDATING_SCORE;
            case UPDATING_SCORE -> to == GameState.ROUND_FINISHED;
            case ROUND_FINISHED -> to == GameState.CHECKGAME_FINISHED;
            case CHECKGAME_FINISHED -> to == GameState.ROUND_STARTED || to == GameState.GAME_FINISHED;
            case GAME_FINISHED -> false;
            default -> false;
        };
    }
}