package cards_against_humanity.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cards_against_humanity.domain.model.enums.MessageType;
import cards_against_humanity.application.service.AuthService;
import cards_against_humanity.domain.model.User;
import cards_against_humanity.network.dto.LoginRequest;
import cards_against_humanity.network.dto.RegisterRequest;

public class ClientHandler implements Runnable {

    private final AuthService authService;
    private String authenticatedUserId;

    private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());

    // Unique ID for each connection
    private final String clientId;

    private final Socket socket;
    private final ClientRegistry registry;
    private final String charset;

    private PrintWriter out;
    private BufferedReader in;

    public ClientHandler(Socket socket, ClientRegistry registry, String charset, AuthService authService) {
        this.clientId = UUID.randomUUID().toString();
        this.socket = socket;
        this.registry = registry;
        this.charset = charset;
        this.authService = authService;
        this.authenticatedUserId = null;
    }

    public ClientHandler(Socket socket, ClientRegistry registry, String charset) {
        this.clientId = UUID.randomUUID().toString();
        this.socket = socket;
        this.registry = registry;
        this.charset = charset;
        this.authService = null;
        this.authenticatedUserId = null;
    }

    @Override
    public void run() {
        LOGGER.info("[" + clientId + "] Connected from " + socket.getInetAddress().getHostAddress() + ":"
                + socket.getPort());
        try {
            openStreams();
            registry.register(clientId, this);
            sendWelcome();
            readLoop();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "[" + clientId + "] I/O error: " + e.getMessage(), e);
        } finally {
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
                default:
                    // Para outras mensagens, exige autenticação
                    if (authenticatedUserId == null) {
                        sendError("Not authenticated. Please login first.");
                    } else {
                        // depois instaciar gameservice e delegar a lógica de cada mensagem
                        send("{\"type\":\"ECHO\",\"payload\":" + rawMessage + "}");
                    }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error processing message", e);
            sendError("Invalid message format or internal error: " + e.getMessage());
        }
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
        send(MessageType.LOGIN_ERROR, payload); // ou um tipo genérico ERROR
    }

    private JsonObject createErrorPayload(String message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("message", message);
        return obj;
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
