# Cards Against Humanity — Servidor TCP

Implementação de um servidor multiplayer para o jogo **Cards Against Humanity** usando Java 17, sockets TCP e arquitetura orientada a eventos (Event Bus).

---

## Índice

- [Descrição](#descrição)
- [Arquitetura](#arquitetura)
- [Pré-requisitos](#pré-requisitos)
- [Configuração do banco de dados](#configuração-do-banco-de-dados)
- [Configuração do servidor](#configuração-do-servidor)
- [Como compilar e executar](#como-compilar-e-executar)
- [Populando as cartas](#populando-as-cartas)
- [Protocolo de comunicação](#protocolo-de-comunicação)
- [Fluxo de jogo](#fluxo-de-jogo)
- [Testando com Netcat / Telnet](#testando-com-netcat--telnet)
- [Estrutura do projeto](#estrutura-do-projeto)
- [Referência de configurações](#referência-de-configurações)

---

## Descrição

O servidor gerencia múltiplas partidas simultâneas via conexões TCP. Cada cliente se conecta, se autentica e pode criar ou entrar em uma partida. A lógica do jogo é orquestrada por um **Event Bus** interno que desacopla os handlers de conexão dos serviços de domínio.

**Funcionalidades implementadas:**

- Registro e login de usuários com senha em BCrypt
- Criação e entrada em partidas (lobby)
- Distribuição de cartas por rodada
- Seleção circular de juiz
- Jogada de cartas de resposta
- Seleção do vencedor da rodada pelo juiz
- Placar em tempo real
- Encerramento automático ao atingir pontuação alvo

---

## Arquitetura

```
Cliente TCP
    │
    ▼
ClientHandler  ──►  EventBus  ──►  GameEventHandler
    │                                   │
    │  (auth)                    ┌──────┴──────┐
    ▼                            ▼             ▼
AuthService                LobbyService   GameService
                                │               │
                                └───────┬───────┘
                                        ▼
                              Repositórios JPA (MySQL)
```

---

## Pré-requisitos

| Ferramenta | Versão mínima | Verificar |
|---|---|---|
| Java (JDK) | 17 | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| MySQL | 8.0+ | `mysql --version` |

---

## Configuração do banco de dados

> **O banco `cards_db` é criado automaticamente** pelo driver MySQL na primeira vez que o servidor inicia (`createDatabaseIfNotExist=true` no JDBC URL). As tabelas também são criadas/atualizadas automaticamente pelo Hibernate (`hbm2ddl.auto=update`).
>
> Só é necessário criar o **usuário** com as permissões adequadas.

### 1. Criar usuário no MySQL

Conecte-se ao MySQL como root:

```bash
mysql -u root -p
```

Execute:

```sql
CREATE USER IF NOT EXISTS 'game'@'localhost' IDENTIFIED BY '123';
GRANT ALL PRIVILEGES ON cards_db.* TO 'game'@'localhost';
-- Permissão para criar o banco na primeira execução:
GRANT CREATE ON *.* TO 'game'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

### 2. Ajustar credenciais (se necessário)

Edite `cards_against_humanity_server/src/main/resources/META-INF/persistence.xml`:

```xml
<property name="jakarta.persistence.jdbc.url"
          value="jdbc:mysql://localhost:3306/cards_db?createDatabaseIfNotExist=true&amp;useSSL=false&amp;serverTimezone=UTC&amp;allowPublicKeyRetrieval=true"/>
<property name="jakarta.persistence.jdbc.user"     value="game"/>
<property name="jakarta.persistence.jdbc.password" value="123"/>
```

---

## Configuração do servidor

Edite `cards_against_humanity_server/src/main/resources/config/config.properties`:

```properties
# Porta TCP do servidor
server.port=8080

# Máximo de conexões simultâneas
server.max_connections=50

# Tamanho do pool de threads (uma por cliente)
server.thread_pool_size=50

# Fila de espera de conexões
server.backlog=50

# Encoding das mensagens
server.charset=UTF-8
```

> **Pontuação-alvo padrão:** 8 pontos (configurável em `LobbyService.DEFAULT_TARGET_SCORE`).
> **Máx. jogadores padrão:** 6 (configurável em `LobbyService.DEFAULT_MAX_PLAYERS`).

---

## Como compilar e executar

Todos os comandos abaixo devem ser executados dentro do diretório do servidor:

```bash
cd cards_against_humanity_server
```

### Opção 1 — Desenvolvimento (recomendado)

Compila e executa diretamente via Maven, sem gerar JAR:

```bash
# Linux / macOS
mvn compile exec:java -Dexec.mainClass="cards_against_humanity.Main"

# Windows (PowerShell) — aspas no argumento -D são obrigatórias
mvn compile exec:java "-Dexec.mainClass=cards_against_humanity.Main"
```

### Opção 2 — Produção (JAR executável)

Gera o JAR e executa. **Requer** o plugin `maven-assembly-plugin` ou `maven-shade-plugin` para empacotar as dependências. Com o `pom.xml` atual, use:

```bash
# 1. Gerar o JAR
mvn clean package -DskipTests

# 2. Executar (incluindo dependências no classpath manualmente)
java -cp "target/cards_against_humanity_server-1.0-SNAPSHOT.jar;target/dependency/*" cards_against_humanity.Main
```

> **Dica (Windows PowerShell):** use `;` como separador de classpath.  
> **Dica (Linux/macOS):** use `:` como separador de classpath.

### Saída esperada ao iniciar

```
INFO: TCP Server started on port 8080 | Max connections: 50 | Thread pool: 50
INFO: GameEventHandler registered for all game events.
INFO: Accept loop started - waiting for connections
INFO: Server is running on port 8080. Press Ctrl+C to stop.
```

Para parar o servidor, pressione **`Ctrl+C`** — o shutdown hook garante encerramento gracioso de todas as conexões.

---

## Populando as cartas

As tabelas são criadas automaticamente, mas as cartas precisam ser inseridas manualmente (ou via script SQL) antes de iniciar uma partida.

Conecte ao MySQL e insira ao menos algumas cartas de cada tipo:

```sql
USE cards_db;

-- Cartas pergunta (QUESTION)
INSERT INTO cards (id, text, type) VALUES
  (UUID(), 'Por que cheguei atrasado: ___', 'QUESTION'),
  (UUID(), '___ é a razão pela qual choro no chuveiro.', 'QUESTION'),
  (UUID(), 'O que me mantém acordado à noite: ___', 'QUESTION'),
  (UUID(), 'Segundo os cientistas, ___ é a causa do aquecimento global.', 'QUESTION'),
  (UUID(), 'Meu plano de aposentadoria: ___', 'QUESTION');

-- Cartas resposta (ANSWER)
INSERT INTO cards (id, text, type) VALUES
  (UUID(), 'Uma batata quente', 'ANSWER'),
  (UUID(), 'Morgan Freeman narrando minha vida', 'ANSWER'),
  (UUID(), 'O Wi-Fi caindo na hora errada', 'ANSWER'),
  (UUID(), 'Aquela sensação de déjà vu', 'ANSWER'),
  (UUID(), 'Um cachorro usando óculos', 'ANSWER'),
  (UUID(), 'Acusar o estagiário', 'ANSWER'),
  (UUID(), 'Ovos mexidos às 3 da manhã', 'ANSWER'),
  (UUID(), 'Um complô do governo', 'ANSWER'),
  (UUID(), 'Cheiro de gasolina', 'ANSWER'),
  (UUID(), 'Nicolas Cage em seu melhor momento', 'ANSWER');
```

> **Mínimo recomendado:** 5+ perguntas e 10+ respostas (cada jogador recebe 5 cartas na mão).

---

## Protocolo de comunicação

Todas as mensagens são **JSON de linha única** (`\n` como terminador), no formato:

```json
{ "type": "TIPO_DA_MENSAGEM", "payload": { ... } }
```

### Autenticação

#### Registrar usuário

```json
// → Enviar
{ "type": "REGISTER", "payload": { "username": "joao2", "email": "joao2@email.com", "password": "senha123" } }

// ← Receber (sucesso)
{ "type": "REGISTER_SUCCESS", "payload": { "userId": "uuid-do-usuario" } }

// ← Receber (erro)
{ "type": "REGISTER_ERROR", "payload": { "message": "Email already in use" } }
```

#### Login

```json
// → Enviar
{ "type": "LOGIN", "payload": { "email": "joao@email.com", "password": "senha123" } }

// ← Receber (sucesso)
{ "type": "LOGIN_SUCCESS", "payload": { "userId": "uuid", "username": "joao" } }

// ← Receber (erro)
{ "type": "LOGIN_ERROR", "payload": { "message": "Invalid credentials" } }
```

### Jogo

#### Criar partida

```json
// → Enviar (maxPlayers é opcional, padrão: 6)
{ "type": "CREATE_GAME", "payload": { "maxPlayers": 4 } }

// ← Receber
{ "type": "GAME_CREATED", "payload": { "gameId": "uuid-da-partida" } }
```

#### Entrar em partida

```json
// → Enviar
{ "type": "JOIN_GAME", "payload": { "gameId": "8a21525f-6be4-4e73-8fb6-8b0399d286ef" } }

// ← Todos no jogo recebem
{ "type": "PLAYER_JOINED", "payload": { "gameId": "...", "playerId": "...", "username": "maria" } }
```

#### Iniciar jogo

```json
// → Enviar
{ "type": "START_GAME", "payload": { "gameId": "8a21525f-6be4-4e73-8fb6-8b0399d286ef" } }

// ← Todos recebem
{ "type": "GAME_STARTED", "payload": { "gameId": "..." } }

// ← Cada jogador recebe sua mão individual (NEW_ROUND)
{
  "type": "NEW_ROUND",
  "payload": {
    "round": 1,
    "judgeId": "uuid-do-juiz",
    "isJudge": false,
    "questionCard": { "id": "...", "text": "Por que cheguei atrasado: ___" },
    "hand": [
      { "id": "card-1", "text": "Uma batata quente" },
      { "id": "card-2", "text": "Morgan Freeman narrando minha vida" }
    ]
  }
}
```

#### Jogar uma carta (apenas não-juízes)

```json
// → Enviar
{ "type": "PLAY_CARD", "payload": { "gameId": "...", "cardId": "card-1" } }

// ← Todos recebem (confirmação de jogada)
{ "type": "PLAYER_PLAYED", "payload": { "playerId": "...", "username": "joao" } }

// ← Quando todos jogaram, todos recebem (cartas anônimas para o juiz escolher)
{
  "type": "JUDGE_SELECTING",
  "payload": {
    "gameId": "...",
    "round": 1,
    "playedCards": [
      { "playedCardId": "pc-uuid-1", "text": "Uma batata quente" },
      { "playedCardId": "pc-uuid-2", "text": "Morgan Freeman narrando minha vida" }
    ]
  }
}
```

#### Selecionar vencedor (apenas o juiz)

```json
// → Enviar
{ "type": "SELECT_WINNER", "payload": { "gameId": "...", "playedCardId": "pc-uuid-1" } }

// ← Todos recebem (resultado da rodada)
{
  "type": "ROUND_RESULT",
  "payload": {
    "winnerId": "...",
    "username": "maria",
    "score": 3,
    "scores": [
      { "playerId": "...", "username": "joao",  "score": 2 },
      { "playerId": "...", "username": "maria", "score": 3 }
    ]
  }
}

// ← Se alguém atingiu a pontuação alvo (8 pts por padrão):
{
  "type": "GAME_FINISHED",
  "payload": {
    "winnerId": "...",
    "username": "maria",
    "finalScores": [
      { "playerId": "...", "username": "joao",  "score": 5 },
      { "playerId": "...", "username": "maria", "score": 8 }
    ]
  }
}
```

---

## Fluxo de jogo

```
1. Jogadores se registram e fazem login
2. Um jogador cria a partida (CREATE_GAME)
3. Outros jogadores entram (JOIN_GAME)  ← mínimo 2 jogadores
4. Qualquer jogador inicia (START_GAME)
   └─ Servidor: sorteia juiz, distribui 5 cartas por jogador, envia carta-pergunta
5. Loop de rodadas:
   a. Não-juízes enviam PLAY_CARD com sua carta escolhida
   b. Quando todos jogaram → servidor envia JUDGE_SELECTING com cartas anônimas
   c. Juiz envia SELECT_WINNER com o ID da carta vencedora
   d. Servidor envia ROUND_RESULT com pontuações atualizadas
   e. Se alguém atingiu 8 pontos → GAME_FINISHED
      Senão → nova rodada com próximo juiz (rotação circular)
```

---

## Testando com Netcat / Telnet

### Linux / macOS

```bash
# Terminal 1 — Jogador 1
nc localhost 8080

# Terminal 2 — Jogador 2
nc localhost 8080
```

### Windows (PowerShell)

```powershell
# Habilitar Telnet (executar uma vez como Administrador):
Enable-WindowsOptionalFeature -Online -FeatureName TelnetClient

# Conectar:
telnet localhost 8080
```

### Sessão de exemplo completa (dois jogadores)

**Terminal 1 (Jogador 1 — criador):**
```json
← {"type":"CONNECTED","payload":{"clientId":"abc-123"}}

→ {"type":"REGISTER","payload":{"username":"joao","email":"j@j.com","password":"123"}}
← {"type":"REGISTER_SUCCESS","payload":{"userId":"user-uuid-1"}}

→ {"type":"LOGIN","payload":{"email":"j@j.com","password":"123"}}
← {"type":"LOGIN_SUCCESS","payload":{"userId":"user-uuid-1","username":"joao"}}

→ {"type":"CREATE_GAME","payload":{"maxPlayers":3}}
← {"type":"GAME_CREATED","payload":{"gameId":"game-uuid"}}
```

**Terminal 2 (Jogador 2):**
```json
← {"type":"CONNECTED","payload":{"clientId":"def-456"}}

→ {"type":"REGISTER","payload":{"username":"maria","email":"m@m.com","password":"123"}}
← {"type":"REGISTER_SUCCESS","payload":{"userId":"user-uuid-2"}}

→ {"type":"LOGIN","payload":{"email":"m@m.com","password":"123"}}
← {"type":"LOGIN_SUCCESS","payload":{"userId":"user-uuid-2","username":"maria"}}

→ {"type":"JOIN_GAME","payload":{"gameId":"game-uuid"}}
← {"type":"PLAYER_JOINED","payload":{"gameId":"game-uuid","playerId":"...","username":"maria"}}
```

**Terminal 1 (inicia o jogo):**
```json
→ {"type":"START_GAME","payload":{"gameId":"game-uuid"}}
← {"type":"GAME_STARTED","payload":{"gameId":"game-uuid"}}
← {"type":"NEW_ROUND","payload":{"round":1,"judgeId":"...","isJudge":true,"questionCard":{...},"hand":[...]}}
```

---

## Estrutura do projeto

```
cards_against_humanity_server/
├── pom.xml                                    # Dependências Maven (Java 17, Hibernate 6, MySQL)
├── src/
│   ├── main/
│   │   ├── java/cards_against_humanity/
│   │   │   ├── Main.java                      # Ponto de entrada
│   │   │   ├── application/
│   │   │   │   ├── handler/
│   │   │   │   │   └── GameEventHandler.java  # Orquestra eventos → serviços → TCP
│   │   │   │   └── service/
│   │   │   │       ├── AuthService.java       # Registro e login (BCrypt)
│   │   │   │       ├── GameService.java       # Rodadas, cartas, pontuação
│   │   │   │       └── LobbyService.java      # Criar/entrar/iniciar partidas
│   │   │   ├── domain/
│   │   │   │   ├── model/                     # Entidades JPA (Game, Player, Card, User…)
│   │   │   │   └── repository/               # Interfaces de repositório
│   │   │   ├── infrastructure/
│   │   │   │   ├── config/
│   │   │   │   │   └── JpaConfig.java        # EntityManagerFactory singleton
│   │   │   │   ├── persistence/              # Implementações JPA (MySQL)
│   │   │   │   ├── security/                 # BCrypt para senhas
│   │   │   │   └── transaction/
│   │   │   │       └── Transaction.java      # begin/commit/rollback/close
│   │   │   ├── network/
│   │   │   │   └── dto/                      # DTOs de request/response
│   │   │   └── server/
│   │   │       ├── TcpServer.java            # Accept loop + wiring
│   │   │       ├── ClientHandler.java        # Lê/escreve TCP por cliente
│   │   │       ├── ClientRegistry.java       # Mapa clientId ↔ userId
│   │   │       ├── ServerConfig.java         # Lê config.properties
│   │   │       └── event/
│   │   │           ├── EventBus.java         # Publish/subscribe thread-safe
│   │   │           ├── EventType.java        # Tipos de eventos internos
│   │   │           └── GameEvent.java        # Evento imutável
│   │   └── resources/
│   │       ├── META-INF/
│   │       │   └── persistence.xml           # Configuração JPA/Hibernate + MySQL
│   │       └── config/
│   │           └── config.properties         # Porta, threads, charset
│   └── test/                                 # Testes JUnit 5 + Mockito
```

---

## Referência de configurações

| Parâmetro | Arquivo | Padrão |
|---|---|---|
| Porta TCP | `config.properties` → `server.port` | `8080` |
| Máx. conexões | `config.properties` → `server.max_connections` | `50` |
| Thread pool | `config.properties` → `server.thread_pool_size` | `50` |
| Pontuação alvo | `LobbyService.java` → `DEFAULT_TARGET_SCORE` | `8` |
| Máx. jogadores | `LobbyService.java` → `DEFAULT_MAX_PLAYERS` | `6` |
| Cartas por mão | `GameService.java` → `refillHand()` | `5` |
| BD URL | `persistence.xml` | `localhost:3306/cards_db` |
| BD usuário | `persistence.xml` | `game` |
| BD senha | `persistence.xml` | `123` |
| Criação automática do BD | `persistence.xml` (JDBC URL) | `createDatabaseIfNotExist=true` |
| DDL automático | `persistence.xml` → `hibernate.hbm2ddl.auto` | `update` |
