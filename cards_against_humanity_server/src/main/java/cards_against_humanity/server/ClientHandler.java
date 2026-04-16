package cards_against_humanity.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cards_against_humanity.domain.model.Card;
import cards_against_humanity.domain.model.Game;
import cards_against_humanity.domain.model.Player;
import cards_against_humanity.domain.model.User;
import cards_against_humanity.domain.model.enums.CardType;
import cards_against_humanity.domain.model.enums.MessageType;
import cards_against_humanity.application.service.AuthService;
import cards_against_humanity.application.service.LobbyService;
import cards_against_humanity.network.dto.LoginRequest;
import cards_against_humanity.network.dto.RegisterRequest;
import cards_against_humanity.server.event.EventBus;
import cards_against_humanity.server.event.EventType;
import cards_against_humanity.server.event.GameEvent;

public class ClientHandler implements Runnable {

    private final AuthService authService;
    private String authenticatedUserId;
    private final LobbyService lobbyService;
    private String currentGameCode;
    private final PendingJoinRegistry pendingJoinRegistry;

    /** EventBus compartilhado por todos os handlers; pode ser {@code null} se não injetado. */
    private final EventBus eventBus;

    private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());

    // Unique ID for each connection
    private final String clientId;

    private final Socket socket;
    private final ClientRegistry registry;
    private final String charset;

    private PrintWriter out;
    private BufferedReader in;

    /** Construtor completo – usado pelo {@link ClientHandlerFactory}. */
    public ClientHandler(Socket socket, ClientRegistry registry, String charset,
                         AuthService authService, EventBus eventBus, LobbyService lobbyService,
                         PendingJoinRegistry pendingJoinRegistry) {
        this.clientId = UUID.randomUUID().toString();
        this.socket = socket;
        this.registry = registry;
        this.charset = charset;
        this.authService = authService;
        this.eventBus = eventBus;
        this.authenticatedUserId = null;
        this.lobbyService = lobbyService;
        this.pendingJoinRegistry = pendingJoinRegistry;
    }

    /** Construtor legado sem PendingJoinRegistry (compatibilidade retroativa). */
    public ClientHandler(Socket socket, ClientRegistry registry, String charset,
                         AuthService authService, EventBus eventBus, LobbyService lobbyService) {
        this(socket, registry, charset, authService, eventBus, lobbyService, new PendingJoinRegistry());
    }

    /** Construtor legado sem EventBus (compatibilidade retroativa). */
    public ClientHandler(Socket socket, ClientRegistry registry, String charset, AuthService authService, LobbyService lobbyService) {
        this(socket, registry, charset, authService, null, lobbyService, new PendingJoinRegistry());
    }

    /** Construtor mínimo sem AuthService nem EventBus. */
    public ClientHandler(Socket socket, ClientRegistry registry, String charset) {
        this(socket, registry, charset, null, null, null, new PendingJoinRegistry());
    }

    @Override
    public void run() {
        LOGGER.info("[" + clientId + "] Connected from " + socket.getInetAddress().getHostAddress() + ":"
                + socket.getPort());
        try {
            openStreams();
            registry.register(clientId, this);
            publishLifecycle(EventType.CLIENT_CONNECTED, null);
            sendWelcome();
            readLoop();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "[" + clientId + "] I/O error: " + e.getMessage(), e);
        } finally {
            publishLifecycle(EventType.CLIENT_DISCONNECTED, null);
            if (pendingJoinRegistry != null) {
                pendingJoinRegistry.removeByClientId(clientId);
            }
            cleanup();
        }
    }

    private void openStreams() throws IOException {
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), charset));
    }

    // Sends a raw JSON string to the client
    public void send(String message) {
        if (out != null && !socket.isClosed()) {
            out.println(message);
        }
    }

    // Greetings to new clients
    private void sendWelcome() {
        send("{\"type\":\"CONNECTED\",\"payload\":{\"clientId\":\"" + clientId + "\"}}");
        LOGGER.fine("[" + clientId + "] Welcome message sent.");
    }

    public String getClientId() {
        return clientId;
    }

    public boolean isConnected() {
        return socket != null && !socket.isClosed() && socket.isConnected();
    }

    // Runs until the client closes the connection or an error occurs
    private void readLoop() throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            LOGGER.fine("[" + clientId + "] << " + line);
            handleMessage(line);
        }
        LOGGER.info("[" + clientId + "] Connection closed.");
    }

    protected void handleMessage(String rawMessage) {
        LOGGER.fine("[" + clientId + "] Received: " + rawMessage);
        try {
            JsonObject json = JsonParser.parseString(rawMessage).getAsJsonObject();
            String typeStr = json.get("type").getAsString();
            MessageType type = MessageType.valueOf(typeStr);
            JsonObject payload = json.getAsJsonObject("payload");

            switch (type) {
                case REGISTER:
                    handleRegister(payload);
                    break;
                case LOGIN:
                    handleLogin(payload);
                    break;
                case RESTORE_SESSION:
                    handleRestoreSession(payload);
                    break;
                case LIST_USERS:
                    handleListUsers();
                    break;
                case CREATE_GAME:
                    handleCreateGame(payload);
                    break;
                case JOIN_GAME:
                    handleJoinGame(payload);
                    break;
                case GET_GAME_INFO:
                    handleGetGameInfo(payload);
                    break;
                case LEAVE_GAME:
                    handleLeaveGame(payload);
                    break;
                case CREATE_CARD:
                    handleCreateCard(payload);
                    break;
                case LIST_OPEN_ROOMS:
                    handleListOpenRooms();
                    break;
                case REQUEST_JOIN:
                    handleRequestJoin(payload);
                    break;
                case APPROVE_JOIN:
                    handleApproveJoin(payload);
                    break;
                case REJECT_JOIN:
                    handleRejectJoin(payload);
                    break;
                case START_GAME:
                    if (authenticatedUserId == null) {
                        sendError("Not authenticated. Please login first.");
                    } else {
                        dispatchGameEvent(type, payload);
                    }
                    break;
                default:
                    // Para mensagens de jogo, exige autenticação
                    if (authenticatedUserId == null) {
                        sendError("Not authenticated. Please login first.");
                    } else {
                        dispatchGameEvent(type, payload);
                    }
            }
        } catch (Exception e) {
            LOGGER.warning("[" + clientId + "] Error processing message: " + e.getMessage());
            sendError("Invalid message format or internal error: " + e.getMessage());
        }
    }

    /**
     * Converte um {@link MessageType} de jogo em um {@link EventType} e o publica
     * no {@link EventBus}.  Se o EventBus não estiver disponível, faz echo do
     * payload como fallback para não quebrar clientes existentes.
     *
     * @param type    tipo da mensagem recebida
     * @param payload JSON payload extraído da mensagem
     */
    private void dispatchGameEvent(MessageType type, JsonObject payload) {
        EventType eventType = toEventType(type);

        if (eventType == null) {
            LOGGER.warning("[" + clientId + "] Unhandled message type: " + type);
            send("{\"type\":\"ERROR\",\"payload\":{\"message\":\"Unknown message type: " + type + "\"}}");
            return;
        }

        String gameId = payload != null && payload.has("gameId")
                ? payload.get("gameId").getAsString()
                : null;

        GameEvent event = new GameEvent(eventType, clientId, gameId, payload);

        if (eventBus != null) {
            LOGGER.fine("[" + clientId + "] Publishing event: " + eventType);
            eventBus.publish(event);
        } else {
            // Sem EventBus configurado: modo echo para compatibilidade em testes
            LOGGER.warning("[" + clientId + "] EventBus not configured – echoing message.");
            send("{\"type\":\"ECHO\",\"payload\":" + (payload != null ? payload : "{}") + "}");
        }
    }

    /**
     * Mapeia {@link MessageType} para {@link EventType}.
     *
     * @param type tipo da mensagem de rede
     * @return EventType correspondente, ou {@code null} se não mapeado
     */
    private EventType toEventType(MessageType type) {
        return switch (type) {
            case CREATE_GAME    -> EventType.CREATE_GAME;
            case JOIN_GAME      -> EventType.JOIN_GAME;
            case START_GAME     -> EventType.START_GAME;
            case PLAY_CARD      -> EventType.PLAY_CARD;
            case SELECT_WINNER  -> EventType.SELECT_WINNER;
            default             -> null;
        };
    }

    /**
     * Publica um evento de ciclo de vida da conexão (CONNECTED / DISCONNECTED).
     * É seguro chamar mesmo quando o EventBus não está configurado.
     */
    private void publishLifecycle(EventType type, String gameId) {
        if (eventBus == null) return;
        JsonObject meta = new JsonObject();
        meta.addProperty("clientId", clientId);
        eventBus.publish(new GameEvent(type, clientId, gameId, meta));
    }

    private void handleRegister(JsonObject payload) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(payload.get("username").getAsString());
        request.setEmail(payload.get("email").getAsString());
        request.setPassword(payload.get("password").getAsString());

        try {
            String userId = authService.register(request);
            JsonObject responsePayload = new JsonObject();
            responsePayload.addProperty("userId", userId);
            send(MessageType.REGISTER_SUCCESS, responsePayload);
        } catch (IllegalArgumentException e) {
            sendError(e.getMessage());
        }
    }

    private void handleLogin(JsonObject payload) {
        LoginRequest request = new LoginRequest();
        request.setEmail(payload.get("email").getAsString());
        request.setPassword(payload.get("password").getAsString());

        try {
            User user = authService.login(request);
            this.authenticatedUserId = user.getId();
            // Register the userId <-> clientId mapping for game event routing
            registry.setUserDetails(user.getId(), user.getUsername(), clientId);
            JsonObject responsePayload = new JsonObject();
            responsePayload.addProperty("userId", user.getId());
            responsePayload.addProperty("username", user.getUsername());
            send(MessageType.LOGIN_SUCCESS, responsePayload);
        } catch (IllegalArgumentException e) {
            send(MessageType.LOGIN_ERROR, createErrorPayload(e.getMessage()));
        }
    }

    // Métodos auxiliares de envio
    private void send(MessageType type, JsonObject payload) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", type.name());
        msg.add("payload", payload);
        send(msg.toString());
    }

    private void sendError(String message) {
        JsonObject payload = new JsonObject();
        payload.addProperty("message", message);
        send(MessageType.ERROR, payload);
    }

    private JsonObject createErrorPayload(String message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("message", message);
        return obj;
    }

    private void handleRestoreSession(JsonObject payload) {
        String userId = payload.get("userId").getAsString();
        String username = payload.get("username").getAsString();
        boolean valid = authService.validateUserById(userId, username);
        if (!valid) {
            sendError("Invalid session. Please login again.");
            return;
        }
        this.authenticatedUserId = userId;
        registry.setUserDetails(userId, username, clientId);
        JsonObject response = new JsonObject();
        response.addProperty("status", "SESSION_RESTORED");
        response.addProperty("userId", userId);
        response.addProperty("username", username);
        send(MessageType.RESTORE_SESSION, response);
    }

    private void handleListUsers() {
        JsonArray usersArray = new JsonArray();
        for (Map<String, String> user : registry.getAllOnlineUsers()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", user.get("id"));
            obj.addProperty("username", user.get("username"));
            usersArray.add(obj);
        }
        JsonObject payload = new JsonObject();
        payload.add("users", usersArray);
        send(MessageType.USER_LIST, payload);
    }

    private void handleCreateGame(JsonObject payload) {
        int maxPlayers = payload.get("maxPlayers").getAsInt();
        int targetScore = payload.has("targetScore") ? payload.get("targetScore").getAsInt() : 8;
        Game game = lobbyService.createGame(authenticatedUserId, maxPlayers, targetScore);
        this.currentGameCode = game.getId();
        JsonObject resp = new JsonObject();
        resp.addProperty("gameCode", this.currentGameCode);
        send(MessageType.GAME_CREATED, resp);
    }

    private void handleJoinGame(JsonObject payload) {
        // Aceita o código tanto em 'gameCode' quanto em 'gameId'
        String gameId = payload.has("gameCode")
                ? payload.get("gameCode").getAsString()
                : payload.get("gameId").getAsString();
        try {
            Player player = lobbyService.joinGame(authenticatedUserId, gameId);
            this.currentGameCode = gameId;

            // 1. Responde ao jogador que entrou com o código da sala
            JsonObject resp = new JsonObject();
            resp.addProperty("gameCode", gameId);
            resp.addProperty("gameId", gameId);
            send(MessageType.GAME_CODE, resp);

            // 2. Broadcast PLAYER_JOINED para TODOS os jogadores na sala
            JsonObject joinedPayload = new JsonObject();
            joinedPayload.addProperty("gameId", gameId);
            joinedPayload.addProperty("playerId", player.getId());
            joinedPayload.addProperty("username", player.getUser().getUsername());
            broadcastToGame(gameId, MessageType.PLAYER_JOINED, joinedPayload);

            LOGGER.info("[" + clientId + "] User " + authenticatedUserId + " joined game " + gameId);
        } catch (Exception e) {
            sendError(e.getMessage());
        }
    }

    private void handleGetGameInfo(JsonObject payload) {
        // Aceita tanto 'gameCode' quanto 'gameId'
        String gameCode = payload.has("gameCode")
                ? payload.get("gameCode").getAsString()
                : (payload.has("gameId") ? payload.get("gameId").getAsString() : null);
        if (gameCode == null) {
            sendError("gameCode or gameId is required");
            return;
        }
        Game game = lobbyService.getGameById(gameCode);
        if (game == null) {
            sendError("Sala não encontrada");
            return;
        }

        // Busca lista completa de jogadores
        java.util.List<Player> players = lobbyService.getPlayersInGame(gameCode);

        JsonArray playersArray = new JsonArray();
        for (Player p : players) {
            JsonObject pObj = new JsonObject();
            pObj.addProperty("playerId", p.getId());
            pObj.addProperty("username", p.getUser().getUsername());
            playersArray.add(pObj);
        }

        JsonObject resp = new JsonObject();
        resp.addProperty("gameId", gameCode);
        resp.addProperty("playerCount", players.size());
        resp.addProperty("maxPlayers", game.getMaxPlayers());
        resp.addProperty("isOwner", game.getOwnerId().equals(authenticatedUserId));
        resp.add("players", playersArray);
        send(MessageType.GAME_UPDATE, resp);
    }

    private void handleLeaveGame(JsonObject payload) {
        String gameCode = payload.get("gameCode").getAsString();
        lobbyService.leaveGame(authenticatedUserId, gameCode);
        send(MessageType.GAME_UPDATE, new JsonObject()); // ou apenas confirmação
    }

    // handleStartGame removido para delegar o START_GAME ao GameEventHandler,
    // garantindo que os usuários recebam NEW_ROUND apropriadamente.

    // ─── Criação de cartas customizadas ────────────────────────────────────────

    /**
     * Trata CREATE_CARD: cria uma carta customizada (QUESTION ou ANSWER) no banco.
     *
     * Payload esperado: { text: string, cardType: "QUESTION" | "ANSWER" }
     */
    private void handleCreateCard(JsonObject payload) {
        if (authenticatedUserId == null) {
            sendError("Not authenticated. Please login first.");
            return;
        }
        try {
            String text = payload.get("text").getAsString();
            String cardTypeStr = payload.has("cardType") ? payload.get("cardType").getAsString() : "ANSWER";
            CardType cardType = CardType.valueOf(cardTypeStr.toUpperCase());
            Card card = lobbyService.createCard(authenticatedUserId, text, cardType);

            JsonObject resp = new JsonObject();
            resp.addProperty("cardId", card.getId());
            resp.addProperty("text", card.getText());
            resp.addProperty("cardType", card.getType().name());
            send(MessageType.CARD_CREATED, resp);
            LOGGER.info("[" + clientId + "] Card created: [" + cardType + "] " + text);
        } catch (Exception e) {
            sendError("Erro ao criar carta: " + e.getMessage());
        }
    }

    // ─── Listagem de salas abertas ──────────────────────────────────────────────

    /**
     * Trata LIST_OPEN_ROOMS: retorna todas as salas no estado WAITING_PLAYERS.
     */
    private void handleListOpenRooms() {
        try {
            java.util.List<Game> games = lobbyService.listActiveGames();
            JsonArray roomsArray = new JsonArray();
            for (Game g : games) {
                java.util.List<Player> players = lobbyService.getPlayersInGame(g.getId());
                JsonObject roomObj = new JsonObject();
                roomObj.addProperty("gameId", g.getId());
                roomObj.addProperty("playerCount", players.size());
                roomObj.addProperty("maxPlayers", g.getMaxPlayers());
                roomObj.addProperty("targetScore", g.getTargetScore());
                // Incluir nome do criador da sala, se disponível
                String ownerName = registry.getUsernameByUserId(g.getOwnerId());
                roomObj.addProperty("ownerName", ownerName != null ? ownerName : "Desconhecido");
                roomsArray.add(roomObj);
            }
            JsonObject resp = new JsonObject();
            resp.add("rooms", roomsArray);
            send(MessageType.OPEN_ROOMS, resp);
        } catch (Exception e) {
            sendError("Erro ao listar salas: " + e.getMessage());
        }
    }

    // ─── Fluxo de aprovação de entrada em sala ─────────────────────────────────

    /**
     * Trata REQUEST_JOIN: o jogador solicitante pede para entrar via lista de salas.
     * Envia um pop-up (JOIN_REQUEST) para o dono da sala com um requestId.
     *
     * Payload esperado: { gameId: string }
     */
    private void handleRequestJoin(JsonObject payload) {
        if (authenticatedUserId == null) {
            sendError("Not authenticated. Please login first.");
            return;
        }
        try {
            String gameId = payload.get("gameId").getAsString();
            Game game = lobbyService.getGameById(gameId);
            if (game == null) {
                sendError("Sala não encontrada");
                return;
            }

            // Registra o pedido pendente
            String requestId = pendingJoinRegistry.register(clientId, authenticatedUserId, gameId);

            // Encontra o clientId do dono da sala
            String ownerClientId = registry.getClientIdByUserId(game.getOwnerId());
            if (ownerClientId == null) {
                sendError("O criador da sala não está online. Tente entrar pelo código.");
                pendingJoinRegistry.resolve(requestId); // limpa o registro
                return;
            }

            // Obtém nome do solicitante
            String requesterName = registry.getUsernameByUserId(authenticatedUserId);
            if (requesterName == null) requesterName = "Jogador";

            // Envia pop-up para o dono da sala
            JsonObject joinRequestPayload = new JsonObject();
            joinRequestPayload.addProperty("requestId", requestId);
            joinRequestPayload.addProperty("requesterUserId", authenticatedUserId);
            joinRequestPayload.addProperty("requesterName", requesterName);
            joinRequestPayload.addProperty("gameId", gameId);

            JsonObject joinRequestMsg = new JsonObject();
            joinRequestMsg.addProperty("type", MessageType.JOIN_REQUEST.name());
            joinRequestMsg.add("payload", joinRequestPayload);
            registry.sendTo(ownerClientId, joinRequestMsg.toString());

            LOGGER.info("[" + clientId + "] JOIN request sent to owner " + ownerClientId
                    + " for game " + gameId + " (requestId=" + requestId + ")");
        } catch (Exception e) {
            sendError("Erro ao solicitar entrada: " + e.getMessage());
        }
    }

    /**
     * Trata APPROVE_JOIN: o dono da sala aprova a entrada do solicitante.
     * Chama joinGame e faz broadcast de PLAYER_JOINED.
     *
     * Payload esperado: { requestId: string }
     */
    private void handleApproveJoin(JsonObject payload) {
        if (authenticatedUserId == null) {
            sendError("Not authenticated.");
            return;
        }
        try {
            String requestId = payload.get("requestId").getAsString();
            PendingJoinRegistry.PendingRequest req = pendingJoinRegistry.resolve(requestId);
            if (req == null) {
                sendError("Requisição não encontrada ou já expirada.");
                return;
            }

            // Verifica que quem está aprovando é realmente o dono da sala
            Game game = lobbyService.getGameById(req.gameId());
            if (game == null) {
                sendError("Sala não encontrada.");
                return;
            }
            if (!game.getOwnerId().equals(authenticatedUserId)) {
                sendError("Apenas o criador pode aprovar entradas.");
                return;
            }

            // Processa a entrada do jogador
            Player player = lobbyService.joinGame(req.requesterUserId(), req.gameId());

            // Notifica o solicitante que foi aceito (inclui gameId para redirecionar)
            JsonObject acceptedPayload = new JsonObject();
            acceptedPayload.addProperty("gameId", req.gameId());
            acceptedPayload.addProperty("gameCode", req.gameId());
            JsonObject acceptedMsg = new JsonObject();
            acceptedMsg.addProperty("type", MessageType.JOIN_ACCEPTED.name());
            acceptedMsg.add("payload", acceptedPayload);
            registry.sendTo(req.requesterClientId(), acceptedMsg.toString());

            // Broadcast PLAYER_JOINED para todos na sala
            JsonObject joinedPayload = new JsonObject();
            joinedPayload.addProperty("gameId", req.gameId());
            joinedPayload.addProperty("playerId", player.getId());
            joinedPayload.addProperty("username", player.getUser().getUsername());
            broadcastToGame(req.gameId(), MessageType.PLAYER_JOINED, joinedPayload);

            LOGGER.info("[" + clientId + "] Approved join for user " + req.requesterUserId()
                    + " in game " + req.gameId());
        } catch (Exception e) {
            sendError("Erro ao aprovar entrada: " + e.getMessage());
        }
    }

    /**
     * Trata REJECT_JOIN: o dono da sala rejeita a entrada do solicitante.
     *
     * Payload esperado: { requestId: string }
     */
    private void handleRejectJoin(JsonObject payload) {
        if (authenticatedUserId == null) {
            sendError("Not authenticated.");
            return;
        }
        try {
            String requestId = payload.get("requestId").getAsString();
            PendingJoinRegistry.PendingRequest req = pendingJoinRegistry.resolve(requestId);
            if (req == null) {
                sendError("Requisição não encontrada ou já expirada.");
                return;
            }

            // Notifica o solicitante que foi rejeitado
            JsonObject rejectedPayload = new JsonObject();
            rejectedPayload.addProperty("gameId", req.gameId());
            rejectedPayload.addProperty("message", "O criador da sala recusou sua entrada.");
            JsonObject rejectedMsg = new JsonObject();
            rejectedMsg.addProperty("type", MessageType.JOIN_REJECTED.name());
            rejectedMsg.add("payload", rejectedPayload);
            registry.sendTo(req.requesterClientId(), rejectedMsg.toString());

            LOGGER.info("[" + clientId + "] Rejected join for user " + req.requesterUserId()
                    + " in game " + req.gameId());
        } catch (Exception e) {
            sendError("Erro ao rejeitar entrada: " + e.getMessage());
        }
    }

    /**
     * Envia uma mensagem para todos os jogadores de uma sala.
     * Percorre a lista de jogadores via LobbyService e envia para cada clientId
     * mapeado no registry.
     */
    private void broadcastToGame(String gameId, MessageType type, JsonObject payload) {
        java.util.List<Player> players = lobbyService.getPlayersInGame(gameId);
        JsonObject msg = new JsonObject();
        msg.addProperty("type", type.name());
        msg.add("payload", payload);
        String json = msg.toString();
        for (Player p : players) {
            String targetClientId = registry.getClientIdByUserId(p.getUser().getId());
            if (targetClientId != null) {
                registry.sendTo(targetClientId, json);
                LOGGER.fine("[broadcastToGame] Sent " + type + " to client " + targetClientId);
            }
        }
    }

    private void cleanup() {
        registry.unregister(clientId);
        try {
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "[" + clientId + "] Error closing socket", e);
        }
        LOGGER.info("[" + clientId + "] Resources released.");
    }

}
