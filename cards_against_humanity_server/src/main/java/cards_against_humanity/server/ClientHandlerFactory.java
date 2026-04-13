package cards_against_humanity.server;

import java.net.Socket;
import cards_against_humanity.application.service.AuthService;
import cards_against_humanity.application.service.LobbyService;
import cards_against_humanity.server.event.EventBus;

/**
 * Fábrica de {@link ClientHandler}.
 *
 * <p>Centraliza a criação de handlers garantindo que todos compartilhem
 * o mesmo {@link ClientRegistry}, charset, {@link AuthService} e
 * {@link EventBus}.
 */
public class ClientHandlerFactory {

    private final ClientRegistry registry;
    private final String charset;
    private final AuthService authService;
    private final EventBus eventBus;
    private final LobbyService lobbyService;

    /**
     * Construtor completo com EventBus.
     *
     * @param registry    registro global de clientes conectados
     * @param config      configuração do servidor (fornece charset)
     * @param authService serviço de autenticação
     * @param eventBus    barramento de eventos compartilhado
     */
    public ClientHandlerFactory(ClientRegistry registry, ServerConfig config,
                                AuthService authService, EventBus eventBus, LobbyService lobbyService) {
        this.registry = registry;
        this.charset = config.getCharset();
        this.authService = authService;
        this.eventBus = eventBus;
        this.lobbyService = lobbyService;
    }

    /**
     * Construtor legado sem EventBus (compatibilidade retroativa).
     * Handlers criados por esta factory não publicarão eventos.
     */
    public ClientHandlerFactory(ClientRegistry registry, ServerConfig config,
                                AuthService authService, LobbyService lobbyService) {
        this(registry, config, authService, null, lobbyService);
    }

    /**
     * Construtor sem ServerConfig (usa charset padrão).
     */
    public ClientHandlerFactory(ClientRegistry registry, AuthService authService, LobbyService lobbyService) {
        this.registry = registry;
        this.charset = ServerConfig.DEFAULT_CHARSET;
        this.authService = authService;
        this.eventBus = null;
        this.lobbyService = lobbyService;
    }

    /**
     * Cria um novo {@link ClientHandler} para o socket aceito.
     *
     * @param socket socket TCP do cliente
     * @return handler pronto para ser submetido a um {@link java.util.concurrent.ExecutorService}
     */
    public ClientHandler create(Socket socket) {
        return new ClientHandler(socket, registry, charset, authService, eventBus, lobbyService);
    }
}