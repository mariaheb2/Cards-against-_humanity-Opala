package cards_against_humanity.models.enums;

public enum MessageType {

    REGISTER,
    REGISTER_SUCCESS,

    LOGIN,
    LOGIN_SUCCESS,
    LOGIN_ERROR,

    CREATE_GAME,
    GAME_CREATED,

    JOIN_GAME,
    PLAYER_JOINED,

    START_GAME,
    GAME_STARTED,

    NEW_ROUND,

    PLAY_CARD,
    PLAYER_PLAYED,

    SELECT_WINNER,
    ROUND_RESULT,

    GAME_UPDATE,

    END_GAME
}