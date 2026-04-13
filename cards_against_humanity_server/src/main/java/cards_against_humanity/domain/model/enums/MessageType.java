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

    START_GAME,
    GAME_STARTED,

    NEW_ROUND,

    PLAY_CARD,
    PLAYER_PLAYED,

    JUDGE_SELECTING,

    SELECT_WINNER,
    ROUND_RESULT,

    GAME_UPDATE,

    GAME_FINISHED,

    END_GAME,

    ERROR
}