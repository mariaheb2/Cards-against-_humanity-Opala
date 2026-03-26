# Protocol

## Authentication

### Register

Request:

```
{
  "type": "REGISTER",
  "payload": {
    "username": "string",
    "email": "string",
    "password": "string"
  }
}
```

Response:

```
{
  "type": "REGISTER_SUCCESS",
  "payload": {
    "userId": "string"
  }
}
```

### LOGIN

Request:

```
{
"type": "LOGIN",
"payload": {
    "email": "string",
    "password": "string"
   }
}
```

Response:

```
{
"type": "LOGIN_SUCCESS",
"payload": {
   "userId": "string",
   "username": "string"
  }
}
```

```
{
  "type": "LOGIN_ERROR",
  "payload": {
    "message": "Invalid credentials"
  }
}
```

## Game

### CREATE_GAME

Request:

```
{
  "type": "CREATE_GAME",
  "payload": {
    "userId": "string",
    "maxPlayers": 6,
    "gameName": "",
    "gamePassword": 65943
  }
}
```

Response:

```
{
  "type": "GAME_CREATED",
  "payload": {
    "gameId": "string"
  }
}
```

### Join game

Request:

```
{
  "type": "JOIN_GAME",
  "payload": {
    "userId": "string",
    "gameId": "string"
  }
}
```


Response:

```
{
  "type": "PLAYER_JOINED",
  "payload": {
    "gameId": "string",
    "player": {}
  }
}
```

### PLAY_CARD

Request:

```
{
"type": "PLAY_CARD",
"payload": {
    "gameId": "string",
    "cardId": "string"
   }
}
```

Response:

```
{
"type": "PLAYER_PLAYED",
"payload": {
    "playerId": "string"
   }
}
```

### Start game

Request:

```
{
  "type": "START_GAME",
  "payload": {
    "gameId": "string"
  }
}
```

Response:

```
{
  "type": "GAME_STARTED",
  "payload": {
    "gameId": "string"
  }
}
```

### New Round

Request:

```
{
  "type": "NEW_ROUND",
  "payload": {
    "questionCard": {}
  }
}
```

### Select winner

Request:

```
{
  "type": "SELECT_WINNER",
  "payload": {
    "gameId": "string",
    "cardId": "string"
  }
}
```

Response:

```
{
  "type": "ROUND_RESULT",
  "payload": {
    "winner": {}
  }
}
```

