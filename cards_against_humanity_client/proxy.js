const WebSocket = require('ws');
const net = require('net');
const http = require('http');
const fs = require('fs');
const path = require('path');

const WS_PORT = 3000;
const TCP_HOST = 'localhost';
const TCP_PORT = 8080;
const HTTP_PORT = 8082;

// ─────────────────────────────────────────────────────────────
// Servidor HTTP para arquivos estáticos (HTML, CSS, JS)
// ─────────────────────────────────────────────────────────────
const mimeTypes = {
    '.html': 'text/html',
    '.css': 'text/css',
    '.js': 'text/javascript',
    '.json': 'application/json',
    '.png': 'image/png',
    '.jpg': 'image/jpeg',
    '.ico': 'image/x-icon'
};

const httpServer = http.createServer((req, res) => {
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

httpServer.listen(HTTP_PORT, () => {
    console.log(`📄 HTTP server serving static files at http://localhost:${HTTP_PORT}`);
});

// ─────────────────────────────────────────────────────────────
// Servidor WebSocket (proxy para TCP)
// ─────────────────────────────────────────────────────────────
const wss = new WebSocket.Server({ port: WS_PORT });

console.log(`🔌 WebSocket proxy on port ${WS_PORT} -> TCP ${TCP_HOST}:${TCP_PORT}`);

wss.on('connection', (ws, req) => {
    const clientAddr = req.socket.remoteAddress;
    console.log(`[WS] Cliente ${clientAddr} conectado`);

    let tcpSocket = null;
    let isClosing = false;

    tcpSocket = net.createConnection({ host: TCP_HOST, port: TCP_PORT }, () => {
        console.log(`[TCP] Conectado ao servidor Java para ${clientAddr}`);
    });

    // TCP -> WebSocket
    tcpSocket.on('data', (data) => {
        const messages = data.toString().split('\n');
        for (const msg of messages) {
            if (msg.trim() && ws.readyState === WebSocket.OPEN) {
                ws.send(msg);
            }
        }
    });

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

wss.on('error', (err) => {
    console.error('[WS] Erro no WebSocket:', err);
});