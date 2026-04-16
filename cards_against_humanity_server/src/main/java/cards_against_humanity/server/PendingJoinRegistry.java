package cards_against_humanity.server;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro em memória de pedidos de entrada em sala pendentes de aprovação.
 *
 * <p>Quando um jogador solicita entrar em uma sala via lista de salas abertas
 * ({@code REQUEST_JOIN}), um {@code requestId} único é gerado e associado ao
 * {@code clientId} do solicitante e ao {@code gameId} da sala. O dono da sala
 * responde com {@code APPROVE_JOIN} ou {@code REJECT_JOIN} passando o
 * {@code requestId}, que é resolvido aqui para obter os dados necessários à
 * continuação do fluxo.
 */
public class PendingJoinRegistry {

    /** Estrutura de dados de uma solicitação pendente. */
    public record PendingRequest(String requesterClientId, String requesterUserId, String gameId) {}

    /** Mapa requestId → dados da solicitação. Thread-safe. */
    private final Map<String, PendingRequest> pending = new ConcurrentHashMap<>();

    /**
     * Registra um novo pedido de entrada e retorna o ID único para esta solicitação.
     *
     * @param requesterClientId clientId do jogador que quer entrar
     * @param requesterUserId   userId do jogador que quer entrar
     * @param gameId            ID da sala a que deseja entrar
     * @return {@code requestId} único que identifica esta solicitação
     */
    public String register(String requesterClientId, String requesterUserId, String gameId) {
        String requestId = UUID.randomUUID().toString();
        pending.put(requestId, new PendingRequest(requesterClientId, requesterUserId, gameId));
        return requestId;
    }

    /**
     * Resolve e remove um pedido pendente.
     *
     * @param requestId ID da solicitação a resolver
     * @return os dados da solicitação, ou {@code null} se não existir/já resolvida
     */
    public PendingRequest resolve(String requestId) {
        return pending.remove(requestId);
    }

    /**
     * Remove todos os pedidos pendentes associados a um determinado clientId
     * (chamado na desconexão do cliente).
     *
     * @param clientId clientId a limpar
     */
    public void removeByClientId(String clientId) {
        pending.entrySet().removeIf(e -> e.getValue().requesterClientId().equals(clientId));
    }
}
