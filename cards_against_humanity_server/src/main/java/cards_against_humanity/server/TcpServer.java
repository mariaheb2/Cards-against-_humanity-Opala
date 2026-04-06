package cards_against_humanity.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import cards_against_humanity.application.service.AuthService;
import cards_against_humanity.domain.repository.UserRepository;
import cards_against_humanity.domain.service.auth.PasswordEncoder;
import cards_against_humanity.infrastructure.persistence.JpaUserRepository;
import cards_against_humanity.infrastructure.security.BCryptPasswordEncoder;
import cards_against_humanity.server.event.EventBus;

public class TcpServer {

    private static final Logger LOGGER = Logger.getLogger(TcpServer.class.getName());

    private final ServerConfig config;
    private final ClientHandlerFactory handlerFactory;
    private final ClientRegistry registry;
    private final EventBus eventBus;

    private ServerSocket serverSocket;
    private ExecutorService executor;
    private Thread acceptThread;
    private volatile boolean running = false;

    // Creates a server with default configuration
    public TcpServer() {
        this(new ServerConfig());
    }

    // Construtor que recebe apenas ServerConfig (cria AuthService padrão)
    public TcpServer(ServerConfig config) {
        this(config, createDefaultAuthService());
    }

    // Construtor completo com injeção de dependência
    public TcpServer(ServerConfig config, AuthService authService) {
        this.config = config;
        this.registry = new ClientRegistry();
        this.eventBus = new EventBus();
        this.handlerFactory = new ClientHandlerFactory(registry, config, authService, eventBus);
    }

    private static AuthService createDefaultAuthService() {
        UserRepository userRepository = new JpaUserRepository();
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        return new AuthService(userRepository, passwordEncoder);
    }

    // Binds the server socket and starts the accept Loop in a daemon thread - the
    // server runs in background threads
    public void start() throws IOException {
        serverSocket = new ServerSocket(config.getPort(), config.getBacklog());
        executor = Executors.newFixedThreadPool(config.getThreadPoolSize());
        running = true;

        LOGGER.info("TCP Server started on port " + config.getPort() + " | Max connections: "
                + config.getMaxConnection() + " | Thread pool: " + config.getThreadPoolSize());

        acceptThread = new Thread(this::acceptLoop, "tcp-accept-loopd");
        acceptThread.setDaemon(true);
        acceptThread.start();

    }

    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        LOGGER.info("Shutting down TCP server");

        // Closes the tcp server socket to unblock accept()
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();

            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error closing server socket", e);
        }

        // Graceful executor shutdown
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                LOGGER.warning("Executor did not terminate in time, forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        LOGGER.info("TCP server stopped");
    }

    public void acceptLoop() {
        LOGGER.info("Accept loop started - waiting for connections");
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();

                if (registry.getConnectionCount() >= config.getMaxConnection()) {
                    LOGGER.warning("Connection limit reached (" + config.getMaxConnection() + "). Rejecting "
                            + clientSocket.getInetAddress().getHostAddress());
                    rejectConnection(clientSocket);
                    continue;
                }

                ClientHandler handler = handlerFactory.create(clientSocket);
                executor.submit(handler);

            } catch (SocketException e) {
                // Thrown when serverSocket.close() is called from stop()
                if (running) {
                    LOGGER.log(Level.SEVERE, "Unexpected socket error in accept loop", e);
                }
                // Intentional shutdown, ignore
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "I/O error in accept loop", e);
            }
        }
        LOGGER.info("Accept loop terminated");
    }

    // Sends a rejection message and closes a socket when the connection limit is
    // reached
    private void rejectConnection(Socket clientSocket) {
        try {
            clientSocket.getOutputStream().write(
                    ("{\"type\":\"ERROR\",\"payload\":{\"message\":\"Server full\"}}\n").getBytes(config.getCharset()));
            clientSocket.close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error rejecting connection", e);
        }
    }

    public boolean isRunning() {
        return running;
    }

    public ServerConfig getConfig() {
        return config;
    }

    public ClientRegistry getRegistry() {
        return registry;
    }

    // Retorna o EventBus compartilhado por esta instância do servidor.
    public EventBus getEventBus() {
        return eventBus;
    }

    public int getActiveConnections() {
        return registry.getConnectionCount();
    }

    // Returns the actual TCP port the server is listening on
    public int getActualPort() {
        if (serverSocket == null || serverSocket.isClosed()) {
            return -1;
        }
        return serverSocket.getLocalPort();
    }

}