document.addEventListener('DOMContentLoaded', async () => {
    const statusDiv = document.getElementById('status'); 
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
            case 'GAME_CREATED':
                console.log('GAME_CREATED recebido:', msg.payload);
                const gameCode = msg.payload.gameCode;
                console.log('Redirecionando para game.html com gameCode:', gameCode);
                window.location.replace(`/game.html?gameCode=${encodeURIComponent(msg.payload.gameCode)}`);
                break;
            case 'GAME_CODE':
                gameCodeDisplay.innerText = msg.payload.gameCode;
                window.location.replace(`/game.html?gameCode=${encodeURIComponent(msg.payload.gameCode)}`);
                break;
            case 'ERROR':
                alert(msg.payload.message);
                break;
        }
    });

    // Criar partida
    document.getElementById('create-game-btn').addEventListener('click', () => {
        const maxPlayers = document.getElementById('max-players').value;
        const targetScore = document.getElementById('target-score').value;
        window.gameClient.send('CREATE_GAME', { maxPlayers, targetScore });
    });


    // Entrar em sala pelo código
    document.getElementById('join-game-btn').addEventListener('click', () => {
        const gameCode = document.getElementById('join-code').value.trim();
        if (!gameCode) return;
        window.gameClient.send('JOIN_GAME', { gameCode });
    });

    function renderOnlineUsers(users) {
        onlineList.innerHTML = '';
        users.forEach(user => {
            const li = document.createElement('li');
            li.textContent = user.username;
            if (user.id === window.gameClient.userId) li.style.fontWeight = 'bold';
            onlineList.appendChild(li);
        });
    }
});