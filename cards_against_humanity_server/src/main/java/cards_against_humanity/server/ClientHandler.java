package cards_against_humanity.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cards_against_humanity.domain.model.enums.MessageType;
import cards_against_humanity.application.service.AuthService;
import cards_against_humanity.application.service.LobbyService;
import cards_against_humanity.domain.model.Game;
import cards_against_humanity.domain.model.Player;
import cards_against_humanity.domain.model.User;
import cards_against_humanity.network.dto.LoginRequest;
import cards_against_humanity.network.dto.RegisterRequest;
import cards_against_humanity.server.event.EventBus;
import cards_against_humanity.server.event.EventType;
import cards_against_humanity.server.event.GameEvent;;

public class ClientHandler implements Runnable {

    private final AuthService authService;
    private String authenticatedUserId;
    private final LobbyService lobbyService;
    private String currentGameCode;

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
                         AuthService authService, EventBus eventBus, LobbyService lobbyService) {
        this.clientId = UUID.randomUUID().toString();
        this.socket = socket;
        this.registry = registry;
        this.charset = charset;
        this.authService = authService;
        this.eventBus = eventBus;
        this.authenticatedUserId = null;
        this.lobbyService = lobbyService;
    }

    /** Construtor legado sem EventBus (compatibilidade retroativa). */
    public ClientHandler(Socket socket, ClientRegistry registry, String charset, AuthService authService, LobbyService lobbyService) {
        this(socket, registry, charset, authService, null, lobbyService);
    }

    /** Construtor mínimo sem AuthService nem EventBus. */
    public ClientHandler(Socket socket, ClientRegistry registry, String charset) {
        this(socket, registry, charset, null, null, null);
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
            // Parse simples para obter o tipo (pode usar Jackson depois)
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
                case START_GAME:
                    handleStartGame(payload);
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
            LOGGER.log(Level.WARNING, "Error processing message", e);
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
        String gameId = payload.get("gameCode").getAsString();
        try {
            Player player = lobbyService.joinGame(authenticatedUserId, gameId);
            this.currentGameCode = gameId;
            JsonObject resp = new JsonObject();
            resp.addProperty("gameCode", gameId);
            send(MessageType.GAME_CODE, resp);
        } catch (Exception e) {
            sendError(e.getMessage());
        }
    }

    

    private void handleGetGameInfo(JsonObject payload) {
        String gameCode = payload.get("gameCode").getAsString();
        Game game = lobbyService.getGameById(gameCode); 
        if (game == null) {
            sendError("Sala não encontrada");
            return;
        }
        JsonObject resp = new JsonObject();
        resp.addProperty("playerCount", game.getPlayers().size());
        resp.addProperty("maxPlayers", game.getMaxPlayers());
        resp.addProperty("isOwner", game.getOwnerId().equals(authenticatedUserId)); // se tiver owner
        send(MessageType.GAME_UPDATE, resp);
    }

    private void handleLeaveGame(JsonObject payload) {
        String gameCode = payload.get("gameCode").getAsString();
        lobbyService.leaveGame(authenticatedUserId, gameCode);
        send(MessageType.GAME_UPDATE, new JsonObject()); // ou apenas confirmação
    }

    private void handleStartGame(JsonObject payload) {
        String gameCode = payload.get("gameCode").getAsString();
        // Verificar se é o owner e número mínimo de jogadores
        Game game = lobbyService.getGameById(gameCode);
        if (game == null || !game.getOwnerId().equals(authenticatedUserId)) {
            sendError("Apenas o criador pode iniciar");
            return;
        }
        if (game.getPlayers().size() < 3) {
            sendError("Mínimo de 3 jogadores necessário");
            return;
        }
        // Chama o serviço de jogo para iniciar a partida
        lobbyService.startGame(gameCode);
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
