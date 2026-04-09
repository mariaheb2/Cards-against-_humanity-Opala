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

    // userId <-> clientId bidirectional maps
    private final Map<String, String> userToClient = new ConcurrentHashMap<>();
    private final Map<String, String> clientToUser = new ConcurrentHashMap<>();

    // Registers a newly connecte client
    public void register(String clientId, ClientHandler handler){
        clients.put(clientId, handler);
        LOGGER.info("Registered client " + clientId + " | Active connections: " + clients.size());
    }

    // Removes a client that has disconnected
    public void unregister(String clientId) {
        clients.remove(clientId);
        // Also clean up user mapping if exists
        String userId = clientToUser.remove(clientId);
        if (userId != null) {
            userToClient.remove(userId);
        }
        LOGGER.info("Unregistered client " + clientId + " | Active connections: " + clients.size());
    }

    // Associates an authenticated userId with a clientId
    public void mapUser(String userId, String clientId) {
        userToClient.put(userId, clientId);
        clientToUser.put(clientId, userId);
        LOGGER.fine("Mapped userId=" + userId + " -> clientId=" + clientId);
    }

    // Removes the userId mapping for a given clientId
    public void unmapUser(String clientId) {
        String userId = clientToUser.remove(clientId);
        if (userId != null) {
            userToClient.remove(userId);
        }
    }

    // Returns the clientId for a given authenticated userId, or null
    public String getClientIdByUserId(String userId) {
        return userToClient.get(userId);
    }

    // Returns the userId for a given clientId, or null
    public String getUserIdByClientId(String clientId) {
        return clientToUser.get(clientId);
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