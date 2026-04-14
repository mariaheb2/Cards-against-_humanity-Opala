// game.js — gerencia a sala de espera
document.addEventListener('DOMContentLoaded', async () => {
    const urlParams = new URLSearchParams(window.location.search);
    const gameCode = urlParams.get('gameCode');
    console.log('URL atual:', window.location.href);
    console.log('gameCode lido:', gameCode);

    if (!gameCode) {
        console.error('gameCode não encontrado na URL');
        alert('Código da sala não informado. Voltando ao lobby.');
        window.location.href = '/lobby.html';
        return;
    }

    // Conecta / restaura sessão
    if (!window.gameClient.ws || window.gameClient.ws.readyState !== WebSocket.OPEN) {
        try {
            await window.gameClient.connect();
        } catch (err) {
            alert('Erro de conexão com o servidor. Voltando ao lobby.');
            window.location.href = '/lobby.html';
            return;
        }
    }
    window.gameClient.restoreSession();

    // Aguarda o servidor processar RESTORE_SESSION antes de pedir infos
    await delay(350);

    // Mostra o código da sala na tela
    const codeDisplay = document.getElementById('game-code-display');
    codeDisplay.innerText = gameCode;

    // Copiar código 
    document.getElementById('copy-code-btn').addEventListener('click', () => {
        navigator.clipboard.writeText(gameCode).then(() => {
            const toast = document.getElementById('copy-toast');
            toast.classList.add('show');
            setTimeout(() => toast.classList.remove('show'), 1800);
        }).catch(() => {
            // fallback para navegadores sem clipboard API
            const el = document.createElement('textarea');
            el.value = gameCode;
            document.body.appendChild(el);
            el.select();
            document.execCommand('copy');
            document.body.removeChild(el);
        });
    });

    // Estado local 
    let currentPlayersCount = 0;
    let maxPlayers = 0;
    let isOwner = false;
    const playersList = {}; // playerId → username
    let pollInterval = null;

    // Escutar mensagens do servidor 
    window.addEventListener('gameMessage', (e) => {
        const msg = e.detail;
        console.log('[game.js] msg:', msg.type, msg.payload);
        switch (msg.type) {

            case 'GAME_UPDATE':
                // Resposta ao GET_GAME_INFO — snapshot completo do estado da sala
                currentPlayersCount = msg.payload.playerCount !== undefined
                    ? msg.payload.playerCount
                    : currentPlayersCount;
                maxPlayers = msg.payload.maxPlayers || maxPlayers;
                isOwner = msg.payload.isOwner !== undefined ? msg.payload.isOwner : isOwner;
                if (msg.payload.players && Array.isArray(msg.payload.players)) {
                    syncPlayersList(msg.payload.players);
                }
                renderPlayersList();
                updateUI();
                break;

            case 'PLAYER_JOINED':
                // Broadcast quando qualquer jogador entra → atualiza todos
                if (msg.payload.playerId && msg.payload.username) {
                    playersList[msg.payload.playerId] = msg.payload.username;
                    currentPlayersCount = Object.keys(playersList).length;
                } else {
                    currentPlayersCount++;
                }
                renderPlayersList();
                updateUI();
                break;

            case 'PLAYER_LEFT':
                if (msg.payload && msg.payload.playerId) {
                    delete playersList[msg.payload.playerId];
                    currentPlayersCount = Object.keys(playersList).length;
                } else {
                    currentPlayersCount = Math.max(0, currentPlayersCount - 1);
                }
                renderPlayersList();
                updateUI();
                break;

            case 'GAME_STARTED':
                // Broadcast quando o dono inicia — redireciona TODOS
                stopPolling();
                showStartingAnimation(() => {
                    const gId = (msg.payload && msg.payload.gameId) || gameCode;
                    window.location.replace(`/play.html?gameId=${encodeURIComponent(gId)}`);
                });
                break;

            case 'NEW_ROUND':
                // O servidor envia NEW_ROUND logo após GAME_STARTED.
                // Como teremos uma transição de página que derruba o WebSocket, 
                // guardamos o payload para ser lido pelo play.js assim que conectar.
                sessionStorage.setItem('initialRoundData', JSON.stringify(msg.payload));
                break;

            case 'ERROR':
                console.error('[game.js] Erro server:', msg.payload.message);
                // Mostra como alert apenas erros criticos de sala
                if (msg.payload.message && (
                    msg.payload.message.toLowerCase().includes('not found') ||
                    msg.payload.message.toLowerCase().includes('não encontrad')
                )) {
                    alert('Sala não encontrada. Voltando ao lobby.');
                    window.location.href = '/lobby.html';
                }
                break;
        }
    });

    //  Solicita estado inicial da sala e inicia polling 
    requestGameInfo();
    // Polling a cada 5s como fallback para garantir sincronização
    pollInterval = setInterval(requestGameInfo, 5000);

    function requestGameInfo() {
        window.gameClient.send('GET_GAME_INFO', { gameCode, gameId: gameCode });
    }

    function stopPolling() {
        if (pollInterval) {
            clearInterval(pollInterval);
            pollInterval = null;
        }
    }

    // Botão iniciar jogo 
    document.getElementById('start-game-btn').addEventListener('click', () => {
        if (!isOwner) {
            alert('Apenas o criador da sala pode iniciar o jogo.');
            return;
        }
        if (currentPlayersCount < 3) {
            alert(`É necessário pelo menos 3 jogadores. Atualmente: ${currentPlayersCount}`);
            return;
        }
        const btn = document.getElementById('start-game-btn');
        btn.textContent = '⏳ Iniciando...';
        btn.disabled = true;
        window.gameClient.send('START_GAME', { gameCode, gameId: gameCode });
    });

    // ── Botão sair da sala ────────────────────────────────────────
    document.getElementById('leave-room-btn').addEventListener('click', () => {
        stopPolling();
        window.gameClient.send('LEAVE_GAME', { gameCode, gameId: gameCode });
        window.location.href = '/lobby.html';
    });

    // Helpers 

    function syncPlayersList(players) {
        // Limpa e recarrega pelo snapshot do servidor
        Object.keys(playersList).forEach(k => delete playersList[k]);
        players.forEach(p => {
            const id = p.playerId || p.id;
            if (id) playersList[id] = p.username;
        });
        currentPlayersCount = players.length;
    }

    function renderPlayersList() {
        const ul = document.getElementById('room-players-list');
        ul.innerHTML = '';
        const myId = window.gameClient.userId || localStorage.getItem('userId');
        const entries = Object.entries(playersList);
        if (entries.length === 0) {
            const li = document.createElement('li');
            li.className = 'room-player-item';
            li.style.color = '#555';
            li.style.fontSize = '0.85rem';
            li.textContent = 'Nenhum jogador ainda...';
            ul.appendChild(li);
            return;
        }
        entries.forEach(([id, name]) => {
            const li = document.createElement('li');
            li.className = 'room-player-item';
            const isMe = id === myId;
            li.innerHTML = `
                <span class="player-avatar">${name.charAt(0).toUpperCase()}</span>
                <span class="player-name">${name}${isMe ? ' <em>(você)</em>' : ''}</span>
                ${isOwner && isMe ? '<span class="owner-badge">👑 Criador</span>' : ''}
            `;
            ul.appendChild(li);
        });
    }

    function updateUI() {
        document.getElementById('current-players').innerText = currentPlayersCount;
        document.getElementById('max-players').innerText = maxPlayers || '?';

        // Barra de progresso
        const needed = maxPlayers > 0 ? maxPlayers : 3;
        const pct = Math.min(100, (currentPlayersCount / needed) * 100);
        document.getElementById('progress-bar').style.width = pct + '%';

        const startBtn = document.getElementById('start-game-btn');
        const waitingMsg = document.getElementById('waiting-message');

        if (currentPlayersCount >= 3) {
            if (isOwner) {
                waitingMsg.innerHTML = '✅ Mínimo atingido! Você pode iniciar o jogo.';
                waitingMsg.className = 'waiting-message ready';
                startBtn.disabled = false;
                startBtn.style.opacity = '1';
                startBtn.style.cursor = 'pointer';
            } else {
                waitingMsg.innerHTML = '✅ Aguardando o criador iniciar a partida...';
                waitingMsg.className = 'waiting-message ready';
                startBtn.disabled = true;
                startBtn.style.opacity = '0.5';
                startBtn.style.cursor = 'not-allowed';
            }
        } else {
            const faltam = 3 - currentPlayersCount;
            waitingMsg.innerHTML = `⏳ Aguardando mais ${faltam} jogador(es)... (mínimo 3)`;
            waitingMsg.className = 'waiting-message';
            startBtn.disabled = true;
            startBtn.style.opacity = '0.5';
            startBtn.style.cursor = 'not-allowed';
        }
    }

    function showStartingAnimation(callback) {
        const card = document.getElementById('waiting-card');
        const msg = document.getElementById('waiting-message');
        msg.innerHTML = '🚀 Iniciando partida...';
        msg.className = 'waiting-message ready';
        card.style.transform = 'scale(1.03)';
        card.style.boxShadow = '0 0 60px rgba(231,76,60,0.7)';
        setTimeout(callback, 900);
    }

    function delay(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }
});