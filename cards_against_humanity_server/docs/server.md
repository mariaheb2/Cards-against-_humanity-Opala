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

## Classes Principais

### `TcpServer`

Responsável por:
- Criar o `ServerSocket` na porta configurada
- Iniciar o **accept loop** em uma daemon thread (`tcp-accept-loop`)
- Submeter cada novo cliente ao `ExecutorService` (thread pool fixo)
- Rejeitar conexões quando o limite máximo é atingido
- Parar via `stop()` (fecha o socket e aguarda o pool encerrar)

```java
TcpServer server = new TcpServer();  // usa config.properties
server.start();
// ...
server.stop();
```

### `ServerConfig`

Carrega as configurações do classpath (`config/config.properties`). Se o arquivo não for encontrado, usa os valores padrão.

| Propriedade               | Padrão | Descrição                              |
|---------------------------|--------|----------------------------------------|
| `server.port`             | `8080` | Porta TCP                              |
| `server.max_connections`  | `50`   | Nº máximo de clientes simultâneos      |
| `server.thread_pool_size` | `50`   | Tamanho do pool de threads             |
| `server.backlog`          | `50`   | Fila de conexões pendentes (SO_BACKLOG)|
| `server.charset`          | `UTF-8`| Codificação das mensagens de texto     |

### `ClientHandler`

Implementa `Runnable`. Ciclo de vida por conexão:

1. Abre streams de I/O
2. Registra-se no `ClientRegistry`
3. Envia mensagem de boas-vindas (`CONNECTED`)
4. **Loop de leitura** — lê linhas JSON e chama `handleMessage()`
5. Em `finally`: desregistra e fecha o socket

O método `handleMessage(String rawMessage)` é o **ponto de extensão** principal — toda a lógica de negócio parte daqui.

### `ClientRegistry`

Mapa thread-safe (`ConcurrentHashMap`) de `clientId → ClientHandler`. Permite:

```java
registry.broadcast(jsonMessage);          // envia para todos
registry.sendTo(clientId, jsonMessage);   // envia para um cliente específico
registry.getConnectionCount();            // número de conexões ativas
```

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

O método `ClientHandler.handleMessage()` é o dispatcher central atual. Enquanto o router de mensagens não for implementado, adicione um `case` diretamente:

```java
// ClientHandler.java
protected void handleMessage(String rawMessage) {
    // Parse do campo "type" do JSON
    // Exemplo de switch a implementar:
    //
    // switch (type) {
    //     case "MINHA_ACAO" -> send(minhaAcaoService.handle(clientId, payload));
    //     case "REGISTER"   -> send(userService.register(clientId, payload));
    //     ...
    // }
    
    // Stub atual (ECHO) — substituir conforme serviços forem implementados
    send("{\"type\":\"ECHO\",\"payload\":" + rawMessage + "}");
}
```

> **Próximo passo arquitetural:** implementar um `MessageRouter` que extraia o campo `"type"` e despache para `AuthHandler` / `GameHandler`, mantendo `ClientHandler` livre de lógica de negócio.

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
