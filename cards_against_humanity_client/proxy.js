/**
 * Arquivo Entrypoint de Middleware em Node.js.
 * Cumpre a dupla finalidade de "Batedor" (Servidor Estático em portas HTTP) e 
 * Proxy WebSocket <-> TCP (Converte dados de pacotes WS do Browser para Fluxos de Socket Raw ao Servidor Java).
 */
const WebSocket = require('ws');
const net = require('net');
const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = process.env.HTTP_PORT || 8082;        // porta única (HTTP + WebSocket)
const TCP_HOST = process.env.TCP_HOST || 'localhost';
const TCP_PORT = process.env.TCP_PORT || 8080;

// MIME types para arquivos estáticos
const mimeTypes = {
    '.html': 'text/html',
    '.css': 'text/css',
    '.js': 'text/javascript',
    '.json': 'application/json',
    '.png': 'image/png',
    '.jpg': 'image/jpeg',
};

/**
 * Criação da Engine leve de Servidor HTTP nativa do Node.
 * Resolve Rotas SPA (Single Page Application) e engata os mimeTypes de leitura de fs.
 */
// Cria um servidor HTTP único
const server = http.createServer((req, res) => {
    let filePath = req.url === '/' ? '/login.html' : req.url.split('?')[0];
    filePath = path.join(__dirname, 'view', filePath);
    const ext = path.extname(filePath).toLowerCase();
    const contentType = mimeTypes[ext] || 'application/octet-stream';

    fs.readFile(filePath, (err, content) => {
        if (err) {
            if (err.code === 'ENOENT') {
                res.writeHead(404, { 'Content-Type': 'text/html' });
                res.end('<h1>404 - Página não encontrada</h1>');
            } else {
                res.writeHead(500);
                res.end(`Erro interno: ${err.code}`);
            }
        } else {
            res.writeHead(200, { 'Content-Type': contentType });
            res.end(content);
        }
    });
});

/**
 * Utilizando a engine do pacote 'ws', anexa e dá "Upgrade" na rota HTTP 
 * permitindo que a mesma porta faça handshake de WebSockets WSS/WS.
 */
// Anexa o WebSocket ao mesmo servidor HTTP
const wss = new WebSocket.Server({ server });

console.log(`🔌 WebSocket proxy integrado ao servidor HTTP na porta ${PORT}`);
console.log(`🔄 Proxy TCP para ${TCP_HOST}:${TCP_PORT}`);

/**
 * Interceptador Principal de Conexão.
 * Rodado sempre que uma das abas de client HTML requisita ingressar na sala ou fazer Login.
 */
wss.on('connection', (ws, req) => {
    const clientAddr = req.socket.remoteAddress;
    console.log(`[WS] Cliente ${clientAddr} conectado`);

    let tcpSocket = null;
    let isClosing = false;

    /**
     * Túnel Backend -> Assim que há aceite de WS, instanciamos a API net (Socket Raw) 
     * e o Node tenta atrelar-se de imediato ao Thread Pool do Java em outra aba/server.
     */
    tcpSocket = net.createConnection({ host: TCP_HOST, port: TCP_PORT }, () => {
        console.log(`[TCP] Conectado ao servidor Java para ${clientAddr}`);
    });

    /**
     * Transceptor Passivo: Puxa dados brutos advindos do ClientHandler.Java
     * Quebra as mensagens lendo os End-Of-Line '\n' e despeja via protocolo WS para o Navegador do cliente.
     */
    // TCP -> WebSocket
    tcpSocket.on('data', (data) => {
        const messages = data.toString().split('\n');
        for (const msg of messages) {
            if (msg.trim() && ws.readyState === WebSocket.OPEN) {
                ws.send(msg);
            }
        }
    });

    /**
     * Transceptor Ativo: O Navegador envia Action JSONs. 
     * Nós injetamos o newline '\n' como delimitador e reescrevemos no pipe TCP conectado ao Backend Java.
     */
    // WebSocket -> TCP
    ws.on('message', (message) => {
        if (tcpSocket && !tcpSocket.destroyed) {
            tcpSocket.write(message + '\n');
        }
    });

    ws.on('close', () => {
        console.log(`[WS] Cliente ${clientAddr} desconectado`);
        if (tcpSocket && !tcpSocket.destroyed && !isClosing) {
            isClosing = true;
            tcpSocket.end();
            tcpSocket.destroy();
        }
    });

    tcpSocket.on('error', (err) => {
        console.error(`[TCP] Erro para ${clientAddr}:`, err.message);
        if (ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify({
                type: 'ERROR',
                payload: { message: 'Conexão com o servidor do jogo perdida' }
            }));
        }
    });

    tcpSocket.on('close', () => {
        console.log(`[TCP] Conexão fechada para ${clientAddr}`);
        if (ws.readyState === WebSocket.OPEN && !isClosing) {
            isClosing = true;
            ws.close();
        }
    });
});

server.listen(PORT, () => {
    console.log(` Servidor HTTP + WebSocket rodando na porta ${PORT}`);
});