package cards_against_humanity.server;

import java.net.Socket;
import cards_against_humanity.application.service.AuthService;

public class ClientHandlerFactory {

    private final ClientRegistry registry;
    private final String charset;
    private final AuthService authService;

    // Creates a factory that propagates the charset from the server configuration
    // to every handler it produces
    public ClientHandlerFactory(ClientRegistry registry, ServerConfig config, AuthService authService) {
        this.registry = registry;
        this.charset = config.getCharset();
        this.authService = authService;
    }

    // Creates a factory using the default charset
    public ClientHandlerFactory(ClientRegistry registry, AuthService authService) {
        this.registry = registry;
        this.charset = ServerConfig.DEFAULT_CHARSET;
        this.authService = authService;
    }

    // Creates a new handler for the given accepted socket
    public ClientHandler create(Socket socket) {
        return new ClientHandler(socket, registry, charset, authService);
    }

}