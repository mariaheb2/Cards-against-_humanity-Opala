package cards_against_humanity.domain.model.enums;

public enum MessageType {

    REGISTER,
    REGISTER_SUCCESS,

    LOGIN,
    LOGIN_SUCCESS,
    LOGIN_ERROR,

    RESTORE_SESSION,

    CREATE_GAME,
    GAME_CREATED,

    LIST_USERS,
    USER_LIST,

    INVITE_PLAYER,
    INVITE_SENT,

    GAME_CODE,

    GET_GAME_INFO,
    LEAVE_GAME,

    JOIN_GAME,
    PLAYER_JOINED,
    PLAYER_LEFT,

    START_GAME,
    GAME_STARTED,

    NEW_ROUND,

    PLAY_CARD,
    PLAYER_PLAYED,

    JUDGE_SELECTING,
    REVEAL_CARDS,
    CARDS_REVEALED,

    SELECT_WINNER,
    ROUND_RESULT,

    GAME_UPDATE,

    GAME_FINISHED,

    END_GAME,

    // ── Criação de cartas customizadas ─────────────────────────────────────
    CREATE_CARD,
    CARD_CREATED,

    // ── Salas abertas ──────────────────────────────────────────────────────
    LIST_OPEN_ROOMS,
    OPEN_ROOMS,

    // ── Fluxo de aprovação de entrada em sala ─────────────────────────────
    REQUEST_JOIN,
    JOIN_REQUEST,
    APPROVE_JOIN,
    REJECT_JOIN,
    JOIN_ACCEPTED,
    JOIN_REJECTED,

    ERROR
}