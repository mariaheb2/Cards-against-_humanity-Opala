window.gameClient = {
    ws: null,
    authenticated: false,
    userId: null,
    username: null,

    logout() {
        if (this.ws) {
            this.ws.close();
        }
        this.authenticated = false;
        this.userId = null;
        this.username = null;
        localStorage.removeItem('userId');
        localStorage.removeItem('username');
    },

    restoreSession() {
        const userId = localStorage.getItem('userId');
        const username = localStorage.getItem('username');

        if (userId && username) {
            this.userId = userId;
            this.username = username;
            this.authenticated = true;

            this.send('RESTORE_SESSION', {
                userId,
                username
            });
        }
    },

    connect() {
    // Evita múltiplas conexões
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        return Promise.resolve();
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}`;

    return new Promise((resolve, reject) => {
        this.ws = new WebSocket(wsUrl);

        this.ws.onopen = () => {
            console.log('WebSocket conectado');
            resolve();
        };

        this.ws.onerror = (err) => {
            console.error('WebSocket erro', err);
            reject(err);
        };

        this.ws.onclose = () => {
            console.log('WebSocket fechado');
            this.authenticated = false;
        };

        this.ws.onmessage = (event) => {
            const msg = JSON.parse(event.data);
            this.handleMessage(msg);
        };
        });
    },

    send(type, payload) {
        const message = JSON.stringify({ type, payload });
        console.log('Enviando mensagem:', message);
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(message);
        }
    },

    handleMessage(msg) {
         console.log('Mensagem recebida:', msg);

        // Tratamento específico para restauração de sessão
        if (msg.type === 'RESTORE_SESSION') {
            console.log('Sessão restaurada com sucesso');
            this.authenticated = true;
            // Dispara evento específico para quem quiser reagir à restauração
            const sessionEvent = new CustomEvent('sessionRestored', { detail: msg.payload });
            window.dispatchEvent(sessionEvent);
        }

        // Dispara evento genérico para todas as mensagens 
        const event = new CustomEvent('gameMessage', { detail: msg });
        window.dispatchEvent(event);
    },

    // Métodos auxiliares para autenticação
    register(username, email, password) {
        this.send('REGISTER', { username, email, password });
    },

    login(email, password) {
        this.send('LOGIN', { email, password });
    }
};