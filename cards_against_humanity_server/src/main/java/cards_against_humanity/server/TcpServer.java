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

public class TcpServer {

    private static final Logger LOGGER = Logger.getLogger(TcpServer.class.getName());

    private final ServerConfig config;
    private final ClientHandlerFactory handlerFactory;
    private final ClientRegistry registry;

    private ServerSocket serverSocket;
    private ExecutorService executor;
    private Thread acceptThread;
    private volatile boolean running = false;

    //Creates a server with default configuration
    public TcpServer(){
        this(new ServerConfig());
    }

    //Creates a server with supplied configuration
    public TcpServer(ServerConfig config){
        this.config = config;
        this.registry = new ClientRegistry();
        this.handlerFactory = new ClientHandlerFactory(registry, config);
    }



}