package cards_against_humanity.application.handler;

import cards_against_humanity.application.service.GameService;
import cards_against_humanity.application.service.LobbyService;
import cards_against_humanity.domain.model.Card;
import cards_against_humanity.domain.model.Game;
import cards_against_humanity.domain.model.PlayedCard;
import cards_against_humanity.domain.model.Player;
import cards_against_humanity.domain.model.enums.GameState;
import cards_against_humanity.domain.repository.GameRepository;
import cards_against_humanity.domain.repository.PlayedCardRepository;
import cards_against_humanity.domain.repository.PlayerRepository;
import cards_against_humanity.server.ClientRegistry;
import cards_against_humanity.server.event.EventBus;
import cards_against_humanity.server.event.EventType;
import cards_against_humanity.server.event.GameEvent;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ponte central entre o {@link EventBus} e os serviços de domínio do jogo.
 *
 * Subscreve todos os {@link EventType} relacionados ao jogo, processa cada
 * evento chamando o serviço adequado e envia as respostas de volta para os
 * clientes TCP via {@link ClientRegistry}.
 *
 * Deve ser instanciado e registrado ({@link #register()}) uma única vez,
 * geralmente durante a inicialização do {@code TcpServer}.
 */
public class GameEventHandler {

    private static final Logger LOGGER = Logger.getLogger(GameEventHandler.class.getName());

    private final EventBus eventBus;
    private final ClientRegistry registry;
    private final LobbyService lobbyService;
    private final GameService gameService;
    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;
    private final PlayedCardRepository playedCardRepository;

    public GameEventHandler(EventBus eventBus,
            ClientRegistry registry,
            LobbyService lobbyService,
            GameService gameService,
            GameRepository gameRepository,
            PlayerRepository playerRepository,
            PlayedCardRepository playedCardRepository) {
        this.eventBus = eventBus;
        this.registry = registry;
        this.lobbyService = lobbyService;
        this.gameService = gameService;
        this.gameRepository = gameRepository;
        this.playerRepository = playerRepository;
        this.playedCardRepository = playedCardRepository;
    }

    /**
     * Subscreve este handler para todos os eventos de jogo no {@link EventBus}.
     * Deve ser chamado uma única vez durante a inicialização do servidor.
     */
    public void register() {
        eventBus.subscribe(EventType.CREATE_GAME, this::onCreateGame);
        eventBus.subscribe(EventType.JOIN_GAME, this::onJoinGame);
        eventBus.subscribe(EventType.START_GAME, this::onStartGame);
        eventBus.subscribe(EventType.PLAY_CARD, this::onPlayCard);
        eventBus.subscribe(EventType.REVEAL_CARDS, this::onRevealCards);
        eventBus.subscribe(EventType.SELECT_WINNER, this::onSelectWinner);
        LOGGER.info("GameEventHandler registered for all game events.");
    }

    /** CREATE_GAME → cria partida e responde GAME_CREATED ao criador. */
    private void onCreateGame(GameEvent event) {
        String clientId = event.getSourceClientId();
        String userId = registry.getUserIdByClientId(clientId);
        if (!assertAuthenticated(clientId, userId))
            return;

        try {
            JsonObject payload = event.getPayload();
            int maxPlayers = (payload != null && payload.has("maxPlayers"))
                    ? payload.get("maxPlayers").getAsInt()
                    : LobbyService.DEFAULT_MAX_PLAYERS;

            Game game = lobbyService.createGame(userId, maxPlayers);

            JsonObject resp = new JsonObject();
            resp.addProperty("gameId", game.getId());
            registry.sendTo(clientId, buildMessage("GAME_CREATED", resp));
            LOGGER.info("[" + clientId + "] Game created: " + game.getId());

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[" + clientId + "] Error in CREATE_GAME", e);
            sendError(clientId, e.getMessage());
        }
    }

    /** JOIN_GAME → adiciona jogador e broadcast PLAYER_JOINED a todos no jogo. */
    private void onJoinGame(GameEvent event) {
        String clientId = event.getSourceClientId();
        String userId = registry.getUserIdByClientId(clientId);
        if (!assertAuthenticated(clientId, userId))
            return;

        try {
            String gameId = event.getGameId();
            if (gameId == null && event.getPayload() != null && event.getPayload().has("gameId")) {
                gameId = event.getPayload().get("gameId").getAsString();
            }
            if (gameId == null) {
                sendError(clientId, "gameId is required");
                return;
            }

            Player player = lobbyService.joinGame(userId, gameId);

            JsonObject resp = new JsonObject();
            resp.addProperty("gameId", gameId);
            resp.addProperty("playerId", player.getId());
            resp.addProperty("username", player.getUser().getUsername());
            broadcastToGame(gameId, buildMessage("PLAYER_JOINED", resp));
            LOGGER.info("[" + clientId + "] Joined game: " + gameId);

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[" + clientId + "] Error in JOIN_GAME", e);
            sendError(clientId, e.getMessage());
        }
    }

    /**
     * START_GAME → inicia a partida, distribui cartas e broadcast:
     * 1. GAME_STARTED (broadcast a todos)
     * 2. NEW_ROUND personalizado para cada jogador (com sua própria mão)
     */
    private void onStartGame(GameEvent event) {
        String clientId = event.getSourceClientId();
        String userId = registry.getUserIdByClientId(clientId);
        if (!assertAuthenticated(clientId, userId))
            return;

        try {
            String gameId = event.getGameId();
            if (gameId == null && event.getPayload() != null && event.getPayload().has("gameId")) {
                gameId = event.getPayload().get("gameId").getAsString();
            }
            if (gameId == null) {
                sendError(clientId, "gameId is required");
                return;
            }

            // Verificar permissões e número mínimo de jogadores
            Game preGame = gameRepository.findById(gameId).orElseThrow(() -> new IllegalArgumentException("Game not found"));
            if (!preGame.getOwnerId().equals(userId)) {
                sendError(clientId, "Apenas o criador pode iniciar");
                return;
            }
            if (lobbyService.getPlayersInGame(gameId).size() < 3) {
                sendError(clientId, "Mínimo de 3 jogadores necessário");
                return;
            }

            // Inicia jogo e obtém a carta-pergunta da primeira rodada
            Card questionCard = lobbyService.startGame(gameId);

            // Re-busca o jogo com estado atualizado (juiz e round definidos)
            Game game = gameRepository.findById(gameId)
                    .orElseThrow(() -> new IllegalStateException("Game not found after start"));

            // 1. Broadcast: jogo iniciado
            JsonObject startedPayload = new JsonObject();
            startedPayload.addProperty("gameId", gameId);
            broadcastToGame(gameId, buildMessage("GAME_STARTED", startedPayload));

            // 2. Envia NEW_ROUND personalizado (mão individual de cada jogador)
            sendNewRound(game, questionCard);
            LOGGER.info("[" + clientId + "] Game started: " + gameId);

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[" + clientId + "] Error in START_GAME", e);
            sendError(clientId, e.getMessage());
        }
    }

    /**
     * PLAY_CARD → registra jogada; se todos jogaram, notifica o juiz com
     * as cartas para seleção (JUDGE_SELECTING).
     */
    private void onPlayCard(GameEvent event) {
        String clientId = event.getSourceClientId();
        String userId = registry.getUserIdByClientId(clientId);
        if (!assertAuthenticated(clientId, userId))
            return;

        try {
            String gameId = event.getGameId();
            JsonObject payload = event.getPayload();
            if (gameId == null && payload != null && payload.has("gameId")) {
                gameId = payload.get("gameId").getAsString();
            }
            if (gameId == null || payload == null || !payload.has("cardId")) {
                sendError(clientId, "gameId and cardId are required");
                return;
            }
            String cardId = payload.get("cardId").getAsString();

            // Encontra o Player pelo userId + gameId
            Player player = playerRepository.findByUserIdAndGameId(userId, gameId)
                    .orElseThrow(() -> new IllegalArgumentException("Player not in this game"));

            gameService.playCard(gameId, player.getId(), cardId);

            // Notifica todos que este jogador jogou
            JsonObject resp = new JsonObject();
            resp.addProperty("playerId", player.getId());
            resp.addProperty("username", player.getUser().getUsername());
            broadcastToGame(gameId, buildMessage("PLAYER_PLAYED", resp));

            // Verifica se todos os não-juízes já jogaram
            Game game = gameRepository.findById(gameId).orElseThrow();
            if (game.getState() == GameState.JUDGE_SELECTING) {
                sendJudgeSelecting(game);
            }
            LOGGER.info("[" + clientId + "] Card played in game: " + gameId);

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[" + clientId + "] Error in PLAY_CARD", e);
            sendError(clientId, e.getMessage());
        }
    }

    /**
     * REVEAL_CARDS → O juiz escolheu revelar as cartas.
     * Re-transmite as cartas para que os outros jogadores possam vê-las.
     */
    private void onRevealCards(GameEvent event) {
        String clientId = event.getSourceClientId();
        String userId = registry.getUserIdByClientId(clientId);
        if (!assertAuthenticated(clientId, userId))
            return;

        try {
            String gameId = event.getGameId();
            JsonObject payload = event.getPayload();
            if (gameId == null && payload != null && payload.has("gameId")) {
                gameId = payload.get("gameId").getAsString();
            }
            if (gameId == null) {
                sendError(clientId, "gameId is required");
                return;
            }

            // Apenas repassamos o CARDS_REVEALED com as cartas que o juiz enviou
            broadcastToGame(gameId, buildMessage("CARDS_REVEALED", payload));
            LOGGER.info("[" + clientId + "] Cards revealed in game: " + gameId);

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[" + clientId + "] Error in REVEAL_CARDS", e);
            sendError(clientId, e.getMessage());
        }
    }

    /**
     * SELECT_WINNER → processa a escolha do juiz; broadcast ROUND_RESULT e,
     * se o jogo terminou, GAME_FINISHED. Caso contrário, envia a próxima rodada.
     */
    private void onSelectWinner(GameEvent event) {
        String clientId = event.getSourceClientId();
        String userId = registry.getUserIdByClientId(clientId);
        if (!assertAuthenticated(clientId, userId))
            return;

        try {
            String gameId = event.getGameId();
            JsonObject payload = event.getPayload();
            if (gameId == null && payload != null && payload.has("gameId")) {
                gameId = payload.get("gameId").getAsString();
            }
            if (gameId == null || payload == null || !payload.has("playedCardId")) {
                sendError(clientId, "gameId and playedCardId are required");
                return;
            }
            String playedCardId = payload.get("playedCardId").getAsString();

            Player winner = gameService.selectWinner(gameId, playedCardId);

            PlayedCard winningCard = playedCardRepository.findById(playedCardId).orElse(null);
            String winningText = winningCard != null ? winningCard.getCard().getText() : "Carta Vencedora";

            // Re-busca o estado atual (pode ser GAME_FINISHED ou PLAYERS_PLAYING)
            Game game = gameRepository.findById(gameId).orElseThrow();
            List<Player> players = playerRepository.findByGameId(gameId);
            JsonArray scoresArray = buildScoresArray(players);

            // Broadcast resultado da rodada
            JsonObject roundResult = new JsonObject();
            roundResult.addProperty("winningCardText", winningText);
            roundResult.addProperty("winnerId", winner.getId());
            roundResult.addProperty("username", winner.getUser().getUsername());
            roundResult.addProperty("score", winner.getScore());
            roundResult.add("scores", scoresArray);
            broadcastToGame(gameId, buildMessage("ROUND_RESULT", roundResult));

            if (game.getState() == GameState.GAME_FINISHED) {
                // Jogo encerrado
                JsonObject finishedPayload = new JsonObject();
                finishedPayload.addProperty("winningCardText", winningText);
                finishedPayload.addProperty("winnerId", winner.getId());
                finishedPayload.addProperty("username", winner.getUser().getUsername());
                finishedPayload.add("finalScores", scoresArray);
                broadcastToGame(gameId, buildMessage("GAME_FINISHED", finishedPayload));
                LOGGER.info("Game finished: " + gameId + " — Winner: " + winner.getUser().getUsername());
            } else {
                // Nova rodada: chamamos de forma sequencial limpa
                Card questionCard = gameService.startNewRound(gameId);
                
                // Re-busca com o novo juiz, rodada, estado
                game = gameRepository.findById(gameId).orElseThrow();
                sendNewRound(game, questionCard);
            }

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[" + clientId + "] Error in SELECT_WINNER", e);
            sendError(clientId, e.getMessage());
        }
    }

    /**
     * Envia mensagem NEW_ROUND personalizada a cada jogador (com a mão individual)
     * e indica quem é o juiz.
     */
    private void sendNewRound(Game game, Card questionCard) {
        List<Player> players = playerRepository.findByGameId(game.getId());
        String judgeId = game.getCurrentJudge() != null ? game.getCurrentJudge().getId() : null;

        JsonObject questionObj = new JsonObject();
        questionObj.addProperty("id", questionCard.getId());
        questionObj.addProperty("text", questionCard.getText());

        for (Player player : players) {
            String playerClientId = registry.getClientIdByUserId(player.getUser().getId());
            if (playerClientId == null)
                continue; // cliente desconectado

            JsonArray handArray = new JsonArray();
            for (Card card : player.getHand()) {
                JsonObject cardObj = new JsonObject();
                cardObj.addProperty("id", card.getId());
                cardObj.addProperty("text", card.getText());
                handArray.add(cardObj);
            }

            JsonObject roundPayload = new JsonObject();
            roundPayload.addProperty("round", game.getRound());
            roundPayload.addProperty("judgeId", judgeId);
            roundPayload.addProperty("isJudge", player.isJudge());
            roundPayload.add("questionCard", questionObj);
            roundPayload.add("hand", handArray);
            registry.sendTo(playerClientId, buildMessage("NEW_ROUND", roundPayload));
        }
    }

    /**
     * Envia JUDGE_SELECTING ao juiz com as cartas jogadas nesta rodada (anônimas).
     */
    private void sendJudgeSelecting(Game game) {
        List<PlayedCard> roundCards = playedCardRepository.findByGameId(game.getId()).stream()
                .filter(pc -> pc.getRoundNumber() == game.getRound())
                .toList();

        JsonArray cardsArray = new JsonArray();
        for (PlayedCard pc : roundCards) {
            JsonObject cardObj = new JsonObject();
            cardObj.addProperty("playedCardId", pc.getId());
            cardObj.addProperty("text", pc.getCard().getText());
            cardsArray.add(cardObj);
        }

        JsonObject judgePayload = new JsonObject();
        judgePayload.addProperty("gameId", game.getId());
        judgePayload.addProperty("round", game.getRound());
        judgePayload.add("playedCards", cardsArray);

        // Broadcast para todos (inclusive o juiz vê as cartas)
        broadcastToGame(game.getId(), buildMessage("JUDGE_SELECTING", judgePayload));
    }

    /**
     * Envia uma mensagem apenas para os clientes conectados dos jogadores de um
     * jogo.
     */
    private void broadcastToGame(String gameId, String message) {
        List<Player> players = playerRepository.findByGameId(gameId);
        for (Player player : players) {
            String playerClientId = registry.getClientIdByUserId(player.getUser().getId());
            if (playerClientId != null) {
                registry.sendTo(playerClientId, message);
            }
        }
    }

    /** Constrói array JSON de pontuações para broadcast de resultado. */
    private JsonArray buildScoresArray(List<Player> players) {
        JsonArray arr = new JsonArray();
        for (Player p : players) {
            JsonObject entry = new JsonObject();
            entry.addProperty("playerId", p.getId());
            entry.addProperty("username", p.getUser().getUsername());
            entry.addProperty("score", p.getScore());
            arr.add(entry);
        }
        return arr;
    }

    /** Envia mensagem de erro ao cliente originador. */
    private void sendError(String clientId, String message) {
        JsonObject payload = new JsonObject();
        payload.addProperty("message", message != null ? message : "Internal server error");
        registry.sendTo(clientId, buildMessage("ERROR", payload));
    }

    /**
     * Verifica se o clientId está associado a um userId; envia ERROR se não
     * estiver.
     */
    private boolean assertAuthenticated(String clientId, String userId) {
        if (userId == null) {
            sendError(clientId, "Not authenticated. Please login first.");
            return false;
        }
        return true;
    }

    /** Serializa tipo + payload em JSON no formato do protocolo. */
    private String buildMessage(String type, JsonObject payload) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", type);
        msg.add("payload", payload);
        return msg.toString();
    }
}
