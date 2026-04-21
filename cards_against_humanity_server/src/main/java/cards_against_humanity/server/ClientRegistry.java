package cards_against_humanity.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
    private final Map<String, String> userIdToUsername = new ConcurrentHashMap<>();
    private final Map<String, String> usernameToClientId = new ConcurrentHashMap<>();

    /**
     * Registra um novo cliente recém-conectado no escopo global de estado do servidor.
     *
     * @param clientId O UUID correspondente ao novo Socket.
     * @param handler A Thread correspondente dedicada.
     */
    public void register(String clientId, ClientHandler handler){
        clients.put(clientId, handler);
        LOGGER.info("Registered client " + clientId + " | Active connections: " + clients.size());
    }

    /**
     * Remove rastros ativos de clientes desconectados, liberando mapas bi-direcionais
     * de UUID de rede contra os UserIDs de banco de dados do mesmo cliente.
     *
     * @param clientId Chave da conexão a ser purgada da RAM.
     */
    public void unregister(String clientId) {
        clients.remove(clientId);
        String userId = clientToUser.remove(clientId);
        if (userId != null) {
            userToClient.remove(userId);
            String username = userIdToUsername.remove(userId);
            if (username != null) {
                usernameToClientId.remove(username);
            }
        }
        LOGGER.info("Unregistered client " + clientId);
    }

    /**
     * Associa permanentemente um UserID logado (Database) com um UUID de rede momentâneo.
     *
     * @param userId A chave primária de usuário no banco.
     * @param clientId O ID de sessão de rede temporário.
     */
    public void mapUser(String userId, String clientId) {
        userToClient.put(userId, clientId);
        clientToUser.put(clientId, userId);
        LOGGER.fine("Mapped userId=" + userId + " -> clientId=" + clientId);
    }

    /**
     * Rompe a associação entre um player do banco e sua sessão temporária.
     *
     * @param clientId O ID de sessão efêmero expirado ou desconectado.
     */
    public void unmapUser(String clientId) {
        String userId = clientToUser.remove(clientId);
        if (userId != null) {
            userToClient.remove(userId);
        }
    }

    /**
     * Procura o número final de conexão TCP atrelado a um jogador específico para o qual desejamos
     * encaminhar dados sem conhecer seu Client ID original.
     *
     * @param userId Representação permanente do Jogador.
     * @return Client ID correspondente logado ou nulo se ele caiu.
     */
    public String getClientIdByUserId(String userId) {
        return userToClient.get(userId);
    }

    /**
     * Traduz de modo reverso, devolvendo quem de fato está associado à porta de rede especificada.
     *
     * @param clientId ClientID provisório efêmero recebido.
     * @return ID do Usuário do banco original.
     */
    public String getUserIdByClientId(String clientId) {
        return clientToUser.get(clientId);
    }

    /**
     * Traduz o userId do banco para o campo puramente estético/displayável Username do lobby.
     * 
     * @param userId Chave referencial de repositório.
     * @return Nome associado ao painel desse usuário em especial.
     */
    public String getUsernameByUserId(String userId) {
        return userIdToUsername.get(userId);
    }

    /**
     * Busca qual terminal de jogo físico em UUID provisório de rede está preenchido
     * por um dado nome explícito de usuário buscando no lobby.
     *
     * @param username Vulgo/Nickname do player.
     * @return O endpoint efêmero para empurrar pop-ups.
     */
    public String getClientIdByUsername(String username) {
        return usernameToClientId.get(username);
    }

    /**
     * Injeção massiva dos 4 mapeamentos inter-dependentes de rede simultaneamente
     * após passagens nos filtros de Login.
     */
    public void setUserDetails(String userId, String username, String clientId) {
        userToClient.put(userId, clientId);
        clientToUser.put(clientId, userId);
        userIdToUsername.put(userId, username);
        usernameToClientId.put(username, clientId);
    }

    /**
     * Faz um envio cego da mensagem especificada varrendo a ConcurrentHashMap 
     * de handlers para despejar conteúdo cru a todos os terminais instanciados em simultâneo.
     *
     * @param message Mensagem stringificada.
     */
    public void broadcast(String message){
        clients.values().forEach(handler -> handler.send(message));
    }

    /**
     * Encaminha requisições privadas diretamente usando ID de Handlers únicos 
     * presentes na lista de contatos do hash.
     *
     * @param clientId Client alvo.
     * @param message Text/Json a ser despachado pro Socket particular dele.
     * @return flag indicativa se encontrou a agulha no palheiro ou não.
     */
    public boolean sendTo(String clientId, String message){
        ClientHandler handler = clients.get(clientId);
        if (handler == null) {
            return false;
        }
        handler.send(message);
        return true;
    }

    /**
     * Conta ativamente quanta lotação está comprometida em portas na RAM local.
     *
     * @return Numero de sockets sob o radar.
     */
    public int getConnectionCount(){
        return clients.size();
    }

    /**
     * Fornece um iterador fechado e não modificável contendo as estruturas das threads ativas.
     * 
     * @return Collection cravado em apenas leitura.
     */
    public Collection<ClientHandler> getClients(){
        return Collections.unmodifiableCollection(clients.values());
    }

    /**
     * Varre seus próprios logs e fabrica um empacotamento simplificado de IDs e Usernames
     * para reembalagem em arrays de rede e popular placares globais.
     */
    public List<Map<String, String>> getAllOnlineUsers() {
        List<Map<String, String>> list = new ArrayList<>();
        for (String userId : userToClient.keySet()) {
            String username = userIdToUsername.get(userId);
            if (username != null) {
                Map<String, String> user = new HashMap<>();
                user.put("id", userId);
                user.put("username", username);
                list.add(user);
            }
        }
        return list;
    }

    /**
     * Solicitação de resgate da Thread propriamente dita vinculada a uma chave específica.
     *
     * @param clientId O ID da pool.
     * @return ClientHandler associado.
     */
    public ClientHandler getHandler(String clientId) {
        return clients.get(clientId);
    }

    /**
     * Duplicata protetora e estática idêntica em comportamento do método getClients().
     */
    public Collection<ClientHandler> getAll() {
        return Collections.unmodifiableCollection(clients.values());
    }

}