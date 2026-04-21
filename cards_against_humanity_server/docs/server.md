# Server — Arquitetura & Guia de Implementação

## Visão Geral

O servidor do Cards Against Humanity é um **servidor TCP multithreaded** em Java puro. Cada cliente conectado recebe uma thread dedicada e se comunica exclusivamente via **mensagens JSON delimitadas por newline** (`\n`).

```
Cliente A  ──┐
Cliente B  ──┼──►  TcpServer  ──►  ClientHandler (thread)  ──►  handleMessage()
Cliente C  ──┘
```

---

## Estrutura de Pacotes

```
cards_against_humanity/
├── Main.java                     # Ponto de entrada — inicializa o TcpServer
│
├── server/
│   ├── TcpServer.java            # Gerencia ServerSocket, pool de threads e accept loop
│   ├── ServerConfig.java         # Configurações carregadas de config.properties
│   ├── ClientHandler.java        # Thread por cliente — lê, processa e envia mensagens
│   ├── ClientHandlerFactory.java # Fábrica de ClientHandlers
│   └── ClientRegistry.java       # Registro thread-safe de clientes conectados
│
├── network/
│   ├── Message.java              # Envelope de mensagem (type + payload)
│   ├── protocol.md               # Especificação completa do protocolo JSON
│   └── dto/                      # Data Transfer Objects (Request/Response)
│
└── models/
    ├── User.java
    ├── Player.java
    ├── Game.java
    ├── Card.java
    ├── PlayedCard.java
    └── enums/
        ├── MessageType.java      # Todos os tipos de mensagem suportados
        ├── GameState.java
        └── CardType.java
```

---

## Classes e Implementações de Comunicação

### 1. `TcpServer` (Aceitação de Conexões)

Responsável por escutar e administrar a entrada de conexões físicas.
- **Como funciona:** Inicializa um `ServerSocket` em uma porta. Através da daemon thread `tcp-accept-loop`, aguarda infinitamente clientes no método `accept()`. Ao conectar, se a capacidade de rede permitir, fornece um `ClientHandler` à nova conexão rodando no pool (`ExecutorService`).
- **Linhas Relacionadas (`TcpServer.java`):**
  - **`start()` (linhas 95-107):** Inicia a escuta da porta base e levanta a thread `acceptLoop`.
  - **`acceptLoop()` (linhas 141-168):** O loop infinito executando `serverSocket.accept()`. Submete os novos clientes paralelizando-os em `executor.submit(handler)`.
  - **`rejectConnection()` (linhas 170-180):** Envia `{"type":"ERROR"}` forçadamente negando serviço caso o limite de `max_connections` seja atingido pela plataforma de entrada, fechando o stream.

### 2. `ClientHandler` (Leitura, Escrita e Roteamento)

Esta é a classe que suporta *toda a comunicação direta bidirecional* com o cliente, usando mensagens formatadas em JSON.
- **Como funciona (Leitura/Escrita):** O cliente possui uma Thread própria com acesso a objetos de I/O em formato `UTF-8`. As rotas separam-se por quebras de linha (`\n`). O `readLoop` atua iterando via rede continuamente as requisições.
- **Como funciona (Roteamento):** Intercepta o pacote JSON formatado, acessa seu campo string `"type"` num modelo unificado que propaga o pedido a canais lógicos usando o `switch` entre `GameService`, `LobbyService` e afins.
- **Linhas Relacionadas (`ClientHandler.java`):**
  - **`openStreams()` (linhas 106-109):** Inicialização dos fluxos `BufferedReader` (`in`) e `PrintWriter` (`out`).
  - **`send()` (linhas 111-116):** Usado em toda comunicação descendente. Modela e empurra usando `.println(message)` na rede do target.
  - **`readLoop()` (linhas 132-140):** Escuta síncrona bloqueante via `in.readLine()`, efetuando log persistente das instâncias que delegam mensagens.
  - **`handleMessage()` (linhas 142-209):** *Dispatch* principal. Extrai o JsonPayload e intercede por comandos (`REGISTER`, `CREATE_GAME`, `JOIN_GAME`...).
  - **`dispatchGameEvent()` (linhas 219-242):** Delegação de eventos complexos. Mensagens de fluxo de mesa em jogo (`PLAY_CARD`, `START_GAME`) perdem escopo neste controller e são enviadas ao integrador desacoplado `EventBus`, garantindo o envio a `GameEventHandler`.

### 3. Rotinas Multiplayer (Lobby & Broadcast)

- **Como funciona:** Funcionalidades de multiplayer requerem acesso `server-to-client` não-bloqueantes. Notificações globais e pedidos de autorização buscam as referências de conexão de terceiros mapeadas globalmente e encaminham informações de forma paralela.
- **Linhas Relacionadas (`ClientHandler.java`):**
  - **Notificações de Grupo (`broadcastToGame()` - linhas 643-661):** Usado no acompanhando do ciclo de vida dos jogadores (ex: `PLAYER_JOINED`). Recupera todos jogadores de um time no `LobbyService`, detecta sua conexão (`registry.getClientIdByUserId()`) e dispara mensagens em cadeia.
  - **Interpelação "Dono de Sala" (`handleRequestJoin()` - linhas 500-551):** Para entradas dinâmicas, encontra o UID do host do jogo, busca a referência Socket vinculada a si e envia um sub-payload acionando em front-end o modal de aprovação sem afetar outros usuários.

### 4. Gestores (`ClientRegistry` & `EventBus`)

- **`ClientRegistry`:** Mapas Thread-Safe encapsulando vínculos `clientId` (Socket) x UUID do Usuário Autenticado. Ele suporta metódos que viabilizam o `broadcastToGame` provendo envios individualizados (`registry.sendTo()`).
- **`EventBus`:** Barramento logico em memória que desconecta dependencias de manipulação de rede do fluxo e processamento em loop do turno dos baralhos e jogadores.

### `ServerConfig`

Carrega as configurações do classpath (`config/config.properties`).

| Propriedade               | Padrão | Descrição                              |
|---------------------------|--------|----------------------------------------|
| `server.port`             | `8080` | Porta TCP                              |
| `server.max_connections`  | `50`   | Nº máximo de clientes simultâneos      |
| `server.thread_pool_size` | `50`   | Tamanho do pool de threads             |
| `server.backlog`          | `50`   | Fila de conexões pendentes (SO_BACKLOG)|
| `server.charset`          | `UTF-8`| Codificação das mensagens de texto     |

---

## Protocolo de Comunicação

Todas as mensagens trafegam como **JSON em uma única linha** terminada por `\n`.

**Envelope padrão:**
```json
{ "type": "TIPO_DA_MENSAGEM", "payload": { ... } }
```

Consulte [`network/protocol.md`](../src/main/java/cards_against_humanity/network/protocol.md) para a especificação completa de cada rota.

---

## Como Adicionar uma Nova Rota

> Este guia é o passo a passo para implementar uma nova funcionalidade de negócio no servidor.

### Passo 1 — Declarar o tipo de mensagem

Adicione as constantes necessárias no enum `MessageType`:

```java
// models/enums/MessageType.java
public enum MessageType {
    // ... existentes ...
    MINHA_ACAO,
    MINHA_ACAO_SUCCESS,
    MINHA_ACAO_ERROR
}
```

### Passo 2 — Definir o protocolo

Documente o request e response em `network/protocol.md`:

```
### MINHA_ACAO

Request:
{ "type": "MINHA_ACAO", "payload": { "campo": "valor" } }

Response:
{ "type": "MINHA_ACAO_SUCCESS", "payload": { "resultado": "..." } }
```

### Passo 3 — Criar o DTO (opcional, mas recomendado)

```java
// network/dto/MinhaAcaoRequest.java
public class MinhaAcaoRequest {
    private String campo;
    // getters / setters
}
```

### Passo 4 — Implementar a lógica de negócio

Crie ou atualize o **Service** correspondente (ex: `UserService`, `GameService`).

### Passo 5 — Conectar ao `handleMessage()`

O método `ClientHandler.handleMessage()` atua como dispatcher central (linhas 142-209). Adicione um `case` diretamente no bloco switch:

```java
// ClientHandler.java
protected void handleMessage(String rawMessage) {
    // ... parse do JSON ...
    switch (type) {
        // ...
        case MINHA_ACAO:
            handleMinhaAcao(payload);
            break;
    }
}

private void handleMinhaAcao(JsonObject payload) {
    // Implemente a ponte para seu Service
    // String data = payload.get("data").getAsString();
    // service.call(data);
    
    // Responder ao cliente
    // send(MessageType.MINHA_ACAO_SUCCESS, new JsonObject());
}
```

> **Dica arquitetural:** Mensagens referentes à execução interna da partida não devem ser resolvidas diretamente. Inclua-as em `toEventType()` e redirecione usando o pipeline provido para o `EventBus`.

### Passo 6 — Testar manualmente

```bash
# Iniciar o servidor
mvn compile exec:java -Dexec.mainClass="cards_against_humanity.Main"

# Em outro terminal (substitua nc por telnet se necessário)
nc localhost 8080

# Exemplo de mensagem
{"type":"REGISTER","payload":{"username":"alice","email":"a@a.com","password":"123"}}
```

---

## Fluxo de uma Conexão (do início ao fim)

```
Cliente conecta via TCP
    └─► TcpServer.acceptLoop() aceita o Socket
        └─► ClientHandlerFactory.create(socket) → novo ClientHandler
            └─► executor.submit(handler)   [thread do pool]
                └─► ClientHandler.run()
                    ├─► openStreams()          [abre BufferedReader + PrintWriter]
                    ├─► registry.register()   [registra no mapa de clientes]
                    ├─► sendWelcome()          [envia {"type":"CONNECTED","payload":{"clientId":"..."}}}]
                    └─► readLoop()             [bloqueia lendo linhas]
                        └─► handleMessage()    [processa cada mensagem recebida]
                            └─► send()         [escreve resposta JSON na stream]
                        [cliente desconecta]
                    └─► cleanup()              [desregistra + fecha socket]
```

---

## Configuração

Edite `src/main/resources/config/config.properties` para ajustar o servidor sem recompilar:

```properties
server.port=8080
server.max_connections=50
server.thread_pool_size=50
server.backlog=50
server.charset=UTF-8
```

---

## Inicialização

```java
// Main.java — já configurado
ServerConfig config = new ServerConfig();   // lê config.properties
TcpServer server = new TcpServer(config);

Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

server.start();
Thread.currentThread().join();  // mantém a JVM viva
```

Execute via IDE (run `Main`) ou pelo Maven:

```bash
mvn compile exec:java -Dexec.mainClass="cards_against_humanity.Main"
```
