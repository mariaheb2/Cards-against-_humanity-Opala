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

import cards_against_humanity.application.handler.GameEventHandler;
import cards_against_humanity.application.service.AuthService;
import cards_against_humanity.application.service.GameService;
import cards_against_humanity.application.service.LobbyService;
import cards_against_humanity.domain.repository.CardRepository;
import cards_against_humanity.domain.repository.GameRepository;
import cards_against_humanity.domain.repository.PlayedCardRepository;
import cards_against_humanity.domain.repository.PlayerRepository;
import cards_against_humanity.domain.repository.UserRepository;
import cards_against_humanity.domain.service.auth.PasswordEncoder;
import cards_against_humanity.infrastructure.persistence.JpaCardRepository;
import cards_against_humanity.infrastructure.persistence.JpaGameRepository;
import cards_against_humanity.infrastructure.persistence.JpaPlayedCardRepository;
import cards_against_humanity.infrastructure.persistence.JpaPlayerRepository;
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

    /**
     * Cria uma instância do servidor TCP com as configurações padrão.
     */
    public TcpServer() {
        this(new ServerConfig());
    }

    /**
     * Construtor que recebe apenas ServerConfig (cria serviços padrão).
     *
     * @param config Configurações personalizadas do servidor (porta, threads, etc).
     */
    public TcpServer(ServerConfig config) {
        this(config, createDefaultAuthService());
    }

    /**
     * Construtor completo com injeção de dependência.
     * Inicializa os repositórios JPA, serviços de domínio e configura
     * os observadores de eventos de jogo (EventBus etc).
     *
     * @param config Configurações de rede e threads.
     * @param authService Serviço responsável por autenticação de usuários.
     */
    public TcpServer(ServerConfig config, AuthService authService) {
        this.config = config;
        this.registry = new ClientRegistry();
        this.eventBus = new EventBus();

        // Instancia repositórios JPA
        GameRepository gameRepository = new JpaGameRepository();
        PlayerRepository playerRepository = new JpaPlayerRepository();
        CardRepository cardRepository = new JpaCardRepository();
        PlayedCardRepository playedCardRepository = new JpaPlayedCardRepository();
        UserRepository userRepository = new JpaUserRepository();

        // Instancia serviços de domínio
        GameService gameService = new GameService(gameRepository, playerRepository, cardRepository,
                playedCardRepository);
        LobbyService lobbyService = new LobbyService(gameRepository, playerRepository, userRepository,
                cardRepository, gameService);

        // Registro compartilhado de pedidos de entrada pendentes (aprovação pelo dono da sala)
        PendingJoinRegistry pendingJoinRegistry = new PendingJoinRegistry();

        this.handlerFactory = new ClientHandlerFactory(registry, config, authService, eventBus, lobbyService,
                pendingJoinRegistry);

        // Registra o handler de eventos de jogo no EventBus
        GameEventHandler gameEventHandler = new GameEventHandler(
                eventBus, registry, lobbyService, gameService,
                gameRepository, playerRepository, playedCardRepository);
        gameEventHandler.register();
    }

    /**
     * Fabrica um serviço de autenticação padrão instanciando repositórios
     * e codificadores embutidos, caso não sejam fornecidos via injeção.
     *
     * @return Uma instância válida de {@link AuthService}.
     */
    private static AuthService createDefaultAuthService() {
        UserRepository userRepository = new JpaUserRepository();
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        return new AuthService(userRepository, passwordEncoder);
    }

    /**
     * Vincula o socket à porta e inicia o loop de aceitação em uma nova thread
     * classificada como deamon, deixando o servidor agir de forma não-bloqueante
     * enquanto atende aos pedidos concorrentes em threads auxiliares.
     *
     * @throws IOException Caso haja falha na abertura de portas de rede.
     */
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

    /**
     * Cessa com o recebimento de novas conexões e tenta encerrar todas
     * as tarefas existentes em curso no pool do executor (Graceful shutdown).
     */
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

    /**
     * O loop principal do servidor TCP executado em background. Ele aguarda a chegada
     * de clientes. Se houver vagas (Connections Limit), despacha aquele socket 
     * para o Handler e Executor. Caso contrário, ele os rejeita.
     */
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

    /**
     * Rejeita as conexões que entram caso o servidor tenha estourado o 
     * limite máximo de jogadores suportados configurado no config.
     *
     * @param clientSocket O socket da conexão prestes a ser derrubada.
     */
    private void rejectConnection(Socket clientSocket) {
        try {
            clientSocket.getOutputStream().write(
                    ("{\"type\":\"ERROR\",\"payload\":{\"message\":\"Server full\"}}\n").getBytes(config.getCharset()));
            clientSocket.close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error rejecting connection", e);
        }
    }

    /**
     * Informa se o servidor está ou não em execução no momento atual.
     *
     * @return true se ativo, do contrário false.
     */
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

    /**
     * Retorna a porta de rede TCP em que o servidor está ativamente escutando.
     *
     * @return Número da porta ou -1 caso o socket esteja fechado.
     */
    public int getActualPort() {
        if (serverSocket == null || serverSocket.isClosed()) {
            return -1;
        }
        return serverSocket.getLocalPort();
    }

}