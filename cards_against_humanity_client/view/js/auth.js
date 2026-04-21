/**
 * Gerenciador cliente-servidor nativo, mapeia a conexão WebSocket
 * e armazena globalmente o estado da abstração de sessões da aba.
 */
window.gameClient = {
    ws: null,
    authenticated: false,
    userId: null,
    username: null,

    /**
     * Desconecta fisicamente o cliente derrubando o socket
     * e expurga os tokens ou resquícios brutos do LocalStorage mitigando hijack.
     */
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

    /**
     * Tenta revalidar o handshake e refazer o link com o Lobby de forma estática
     * enviando ao Servidor as chaves armazenadas entre refreshs (F5/Reload).
     */
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

    /**
     * Instancia o core de rede do game. Prende listeners onmessage para roteamento 
     * local na Window sob CustomEvents dinâmicos.
     *
     * @returns {Promise<void>} Resolvida em sucesso de handshaking TCP.
     */
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

    /**
     * Encapsula payloads num empacotador de JSON padronizado pelo Java
     * que exige um par "type" (Ação) e "payload" (corpo).
     *
     * @param {string} type O Enumerator/Comando.
     * @param {Object} payload Configurações ou campos transmitidos em JSON array/obj
     */
    send(type, payload) {
        const message = JSON.stringify({ type, payload });
        console.log('Enviando mensagem:', message);
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(message);
        }
    },

    /**
     * Processa tudo que o servidor responder. Despacha por broadcasting de sistema (Window Event)
     * para desacoplar a rede em si da UI gráfica do jogo e lobby.
     *
     * @param {Object} msg Objeto da decodificação limpa do WebSocket cru.
     */
    handleMessage(msg) {
        console.log('Mensagem recebida:', msg);

        if (msg.type === 'RESTORE_SESSION') {
            console.log('Sessão restaurada com sucesso');
            this.authenticated = true;
            const sessionEvent = new CustomEvent('sessionRestored', { detail: msg.payload });
            window.dispatchEvent(sessionEvent);
        }

        const event = new CustomEvent('gameMessage', { detail: msg });
        window.dispatchEvent(event);
    },
    /**
     * Empacotador para o Form UI enviando flag REGISTER.
     *
     * @param {string} username Vulgo pretendido 
     * @param {string} email Identificação
     * @param {string} password Senha que passará ao hash de Auth
     */
    register(username, email, password) {
        this.send('REGISTER', { username, email, password });
    },

    /**
     * Passarela de submissão do form de autenticação padrão.
     *
     * @param {string} email 
     * @param {string} password
     */
    login(email, password) {
        this.send('LOGIN', { email, password });
    }
};