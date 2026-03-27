package cards_against_humanity.server;

import java.net.Socket;

public class ClientHandlerFactory{

    private final ClientRegistry registry;
    private final String charset;

    public ClientHandlerFactory(ClientRegistry registry, ServerConfig config) {
        this.registry = registry;
        this.charset  = config.getCharset();
    }
}