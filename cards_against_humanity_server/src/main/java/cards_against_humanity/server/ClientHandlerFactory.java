package cards_against_humanity.server;

import java.net.Socket;

public class ClientHandlerFactory {

    private final ClientRegistry registry;
    private final String charset;

    // Creates a factory that propagates the charset from the server configuration
    // to every handler it produces
    public ClientHandlerFactory(ClientRegistry registry, ServerConfig config) {
        this.registry = registry;
        this.charset = config.getCharset();
    }

    // Creates a factory using the default charset
    public ClientHandlerFactory(ClientRegistry registry) {
        this.registry = registry;
        this.charset = ServerConfig.DEFAULT_CHARSET;
    }

    // Creates a new handler for the given accepted socket
    public ClientHandler create(Socket socket) {
        return new ClientHandler(socket, registry, charset);
    }

}