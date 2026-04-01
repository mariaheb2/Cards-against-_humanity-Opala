package cards_against_humanity.server;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ClientRegistry{
    private static final Logger LOGGER = Logger.getLogger(ClientRegistry.class.getName());

    // clientId -> handler
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();

    // Registers a newly connecte client
    public void register(String clientId, ClientHandler handler){
        clients.put(clientId, handler);
        LOGGER.info("Registered client " + clientId + " | Active connections: " + clients.size());
    }

    // Removes a client that has disconnected
    public void unregister(String clientId) {
        clients.remove(clientId);
        LOGGER.info("Unregistered client " + clientId + " | Active connections: " + clients.size());
    }

    // Broadcasts a raw JSON message to every connected client
    public void broadcast(String message){
        clients.values().forEach(handler -> handler.send(message));
    }

    // Sends a message to a specific client
    public boolean sendTo(String clientId, String message){
        ClientHandler handler = clients.get(clientId);
        if (handler == null) {
            return false;
        }
        handler.send(message);
        return true;
    }

    // Returns the number of currently connected clients
    public int getConnectionCount(){
        return clients.size();
    }

    // Returns an unmodifiable view of all active handlers
    public Collection<ClientHandler> getClients(){
        return Collections.unmodifiableCollection(clients.values());
    }

    // Returns an unmodifiable view of all active handlers.
    public Collection<ClientHandler> getAll() {
        return Collections.unmodifiableCollection(clients.values());
    }

}