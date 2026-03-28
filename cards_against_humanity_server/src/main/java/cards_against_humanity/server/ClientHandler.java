package cards_against_humanity.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientHandler implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());

    // Unique ID for each connection
    private final String clientId;

    private final Socket socket;
    private final ClientRegistry registry;
    private final String charset;

    private PrintWriter out;
    private BufferedReader in;

    public ClientHandler(Socket socket, ClientRegistry registry){
        this(socket, registry, ServerConfig.DEFAULT_CHARSET);
    }

    public ClientHandler(Socket socket, ClientRegistry registry, String charset){
        this.clientId = UUID.randomUUID().toString();
        this.socket = socket;
        this.registry = registry;
        this.charset = charset;
    }

    @Override
    public void run() {
        LOGGER.info("[" + clientId + "] Connected from "
                + socket.getInetAddress().getHostAddress()
                + ":" + socket.getPort());
        try{
            openStreams();
            registry.register(clientId, this);
            sendWelcome();
            readLoop();
        } catch (IOException e){
            LOGGER.log(Level.WARNING, "[" + clientId + "] I/O error: " + e.getMessage(), e);
        } finally {
            cleanup();
        }
    }

    private void openStreams() throws IOException{
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), charset));
    }

    public void send(String message){
        if (out != null && !socket.isClosed()) {
            out.println(message);
        }
    }

    private void sendWelcome() {
        send("{\"type\":\"CONNECTED\",\"payload\":{\"clientId\":\"" + clientId + "\"}}");
        LOGGER.fine("[" + clientId + "] Welcome message sent.");
    }


    public String getClientId() {
        return clientId;
    }

    // Runs until the client closes the connection or an error occurs
    private void readLoop() throws IOException{
        String line;
        while ((line = in.readLine()) != null) {
            LOGGER.fine("[" + clientId + "] << " + line);
            handleMessage(line);
        }
        LOGGER.info("[" + clientId + "] Connection closed.");
    }

    protected void handleMessage(String rawMessage){
        // TODO: deserialise JSON → Message → dispatch to GameService / AuthService
        LOGGER.info("[" + clientId + "] Received: " + rawMessage);
        send("{\"type\":\"ECHO\",\"payload\":" + rawMessage + "}");
    }
    
    
    

    private void cleanup(){
        registry.unregister(clientId);
        try{
            if (!socket.isClosed()){
                socket.close();
            }
        } catch (IOException e){
            LOGGER.log(Level.WARNING, "[" + clientId + "] Error closing socket", e);
        }
        LOGGER.info("[" + clientId + "] Resources released.");
    }


}

