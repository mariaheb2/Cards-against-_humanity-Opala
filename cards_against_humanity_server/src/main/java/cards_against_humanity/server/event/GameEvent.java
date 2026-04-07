package cards_against_humanity.server.event;

import com.google.gson.JsonObject;

// Evento imutável transportado pelo EventBus
public final class GameEvent {

    private final EventType type;
    private final String gameId;
    private final String sourceClientId;
    private final JsonObject payload;

    public GameEvent(EventType type, String sourceClientId, String gameId, JsonObject payload) {
        this.type = type;
        this.gameId = gameId;
        this.sourceClientId = sourceClientId;
        this.payload = payload;
    }

    /** Construtor de conveniência para eventos sem gameId. */
    public GameEvent(EventType type, String sourceClientId, JsonObject payload) {
        this(type, sourceClientId, null, payload);
    }

    public EventType getType() {
        return type;
    }

    public String getGameId() {
        return gameId;
    }

    public String getSourceClientId() {
        return sourceClientId;
    }

    public JsonObject getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return "GameEvent{type=" + type
                + ", gameId=" + gameId
                + ", source=" + sourceClientId + "}";
    }
}
