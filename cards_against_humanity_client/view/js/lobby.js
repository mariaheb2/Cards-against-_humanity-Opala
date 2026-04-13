document.addEventListener('DOMContentLoaded', async () => {
    const usernameSpan = document.getElementById('lobby-username');
    const onlineList = document.getElementById('online-list');
    const gameCodeDisplay = document.getElementById('game-code-display');

    // Conecta WebSocket se não estiver conectado
    if (!window.gameClient.ws || window.gameClient.ws.readyState !== WebSocket.OPEN) {
        try {
            await window.gameClient.connect();
        } catch (err) {
            alert('Erro de conexão com o servidor');
            window.location.href = '/login.html';
            return;
        }
    }

    // Restaura sessão (envia RESTORE_SESSION)
    window.gameClient.restoreSession();

    // Exibe nome do usuário
    usernameSpan.innerText = window.gameClient.username || localStorage.getItem('username');

    // Solicita lista de usuários online ao servidor
    window.gameClient.send('LIST_USERS', {});

    // Escuta mensagens do servidor
    window.addEventListener('gameMessage', (event) => {
        const msg = event.detail;
        switch (msg.type) {
            case 'USER_LIST':
                renderOnlineUsers(msg.payload.users);
                break;

            // O servidor retorna gameId (UUID) como identificador da sala
            case 'GAME_CREATED': {
                console.log('GAME_CREATED recebido:', msg.payload);
                // O servidor envia { gameId } — usamos como gameCode na URL
                const gameId = msg.payload.gameId || msg.payload.gameCode;
                console.log('Redirecionando para game.html com gameId:', gameId);
                window.location.replace(`/game.html?gameCode=${encodeURIComponent(gameId)}`);
                break;
            }

            // Atalho usado pelo servidor em alguns fluxos
            case 'GAME_CODE': {
                const code = msg.payload.gameId || msg.payload.gameCode;
                gameCodeDisplay.innerText = code;
                window.location.replace(`/game.html?gameCode=${encodeURIComponent(code)}`);
                break;
            }

            // Quando o JOIN_GAME é aceito, o servidor faz broadcast de PLAYER_JOINED
            // e pode enviar GAME_CODE — mas também pode redirecionar direto
            case 'PLAYER_JOINED': {
                // Se veio aqui antes de redirecionar, vai para a sala
                if (msg.payload && msg.payload.gameId) {
                    window.location.replace(`/game.html?gameCode=${encodeURIComponent(msg.payload.gameId)}`);
                }
                break;
            }

            case 'ERROR':
                alert(msg.payload.message);
                break;
        }
    });

    // Criar partida
    document.getElementById('create-game-btn').addEventListener('click', () => {
        const maxPlayers = parseInt(document.getElementById('max-players').value);
        const targetScore = parseInt(document.getElementById('target-score').value);
        window.gameClient.send('CREATE_GAME', { maxPlayers, targetScore });
    });

    // Entrar em sala pelo código
    document.getElementById('join-game-btn').addEventListener('click', () => {
        const gameCode = document.getElementById('join-code').value.trim();
        if (!gameCode) return;
        // O servidor aceita gameId no campo gameCode
        window.gameClient.send('JOIN_GAME', { gameCode, gameId: gameCode });
    });

    function renderOnlineUsers(users) {
        if (!users) return;
        onlineList.innerHTML = '';
        users.forEach(user => {
            const li = document.createElement('li');
            li.className = 'online-player-item';
            const dot = document.createElement('span');
            dot.className = 'online-dot';
            li.appendChild(dot);
            li.appendChild(document.createTextNode(user.username));
            if (user.id === window.gameClient.userId) {
                li.classList.add('you');
                li.append(' (você)');
            }
            onlineList.appendChild(li);
        });
    }
});