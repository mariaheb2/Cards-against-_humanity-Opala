package cards_against_humanity.server.event;

// Tipos de eventos internos publicados no EventBus
public enum EventType {

    // ── Eventos originados no cliente ──────────────────────────────────────
    CREATE_GAME,
    JOIN_GAME,
    START_GAME,
    PLAY_CARD,
    SELECT_WINNER,

    // ── Eventos de resposta publicados pelos handlers de serviço ───────────
    GAME_CREATED,
    PLAYER_JOINED,
    GAME_STARTED,
    NEW_ROUND,
    CARD_PLAYED,
    ROUND_RESULT,
    GAME_FINISHED,

    // ── Eventos de ciclo de vida da conexão ────────────────────────────────
    CLIENT_CONNECTED,
    CLIENT_DISCONNECTED
}
