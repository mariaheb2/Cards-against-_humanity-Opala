package cards_against_humanity.server.event;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

// Barramento de eventos central do servidor
public class EventBus {
    private static final Logger LOGGER = Logger.getLogger(EventBus.class.getName());

    /**
     * Mapa de assinantes: EventType → lista de handlers.
     * ConcurrentHashMap garante acesso thread-safe ao mapa.
     * CopyOnWriteArrayList garante iteração segura mesmo com modificações
     * concorrentes.
     */
    private final ConcurrentHashMap<EventType, CopyOnWriteArrayList<Consumer<GameEvent>>> subscribers = new ConcurrentHashMap<>();

    /**
     * Registra um handler para o tipo de evento especificado.
     * O mesmo handler pode ser registrado múltiplas vezes para o mesmo tipo e
     * receberá múltiplas chamadas. Para evitar isso, use {@link #unsubscribe} antes
     * de re-registrar.
     *
     * @param type    tipo de evento a observar
     * @param handler callback a ser chamado quando o evento for publicado
     */
    public void subscribe(EventType type, Consumer<GameEvent> handler) {
        subscribers.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(handler);
        LOGGER.fine("Subscribed handler to " + type);
    }

    /**
     * Remove um handler previamente registrado.
     * Se o handler não estiver registrado para aquele tipo, esta operação é
     * silenciosa.
     * 
     * @param type    tipo de evento
     * @param handler handler a remover
     */
    public void unsubscribe(EventType type, Consumer<GameEvent> handler) {
        List<Consumer<GameEvent>> handlers = subscribers.get(type);
        if (handlers != null) {
            handlers.remove(handler);
            LOGGER.fine("Unsubscribed handler from " + type);
        }
    }

    /**
     * Publica um evento para todos os handlers registrados para o seu tipo.
     * Os handlers são invocados sequencialmente na thread chamadora.
     * Exceções lançadas por um handler são capturadas e registradas em log,
     * mas não impedem a entrega aos demais handlers.
     *
     * @param event o evento a ser publicado (não pode ser {@code null})
     */
    public void publish(GameEvent event) {
        if (event == null) {
            LOGGER.warning("Attempted to publish null event — ignored.");
            return;
        }
        LOGGER.fine("Publishing event: " + event);

        List<Consumer<GameEvent>> handlers = subscribers.get(event.getType());
        if (handlers == null || handlers.isEmpty()) {
            LOGGER.fine("No subscribers for event type: " + event.getType());
            return;
        }

        for (Consumer<GameEvent> handler : handlers) {
            try {
                handler.accept(event);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Exception in EventBus handler for " + event.getType(), e);
            }
        }
    }

    /**
     * Retorna o número de handlers registrados para um dado tipo de evento.
     *
     * @param type tipo de evento
     * @return número de handlers
     */
    public int subscriberCount(EventType type) {
        List<Consumer<GameEvent>> handlers = subscribers.get(type);
        return handlers == null ? 0 : handlers.size();
    }
}
