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

    connect() {
        return new Promise((resolve, reject) => {
            this.ws = new WebSocket('ws://localhost:3000');
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
        // Dispara evento global para as páginas ouvirem
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