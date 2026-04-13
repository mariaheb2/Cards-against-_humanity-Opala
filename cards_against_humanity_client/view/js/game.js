// game.js – gerencia a sala de espera
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

    // Conecta WebSocket se necessário
    if (!window.gameClient.ws || window.gameClient.ws.readyState !== WebSocket.OPEN) {
        await window.gameClient.connect();
    }
    window.gameClient.restoreSession();

    // Mostra o código da sala na tela
    document.getElementById('game-code-display').innerText = gameCode;

    let currentPlayersCount = 0;
    let maxPlayers = 0;
    let isOwner = false; // será definido quando receber a sala

    // Escuta mensagens do servidor
    window.addEventListener('gameMessage', (e) => {
        const msg = e.detail;
        switch (msg.type) {
            case 'GAME_UPDATE':
                // Atualiza informações da sala (players, maxPlayers, dono)
                currentPlayersCount = msg.payload.playerCount || 0;
                maxPlayers = msg.payload.maxPlayers || 0;
                isOwner = msg.payload.isOwner || false;
                updateUI();
                break;
            case 'PLAYER_JOINED':
                // Outro jogador entrou na sala
                currentPlayersCount++;
                updateUI();
                break;
            case 'PLAYER_LEFT':
                currentPlayersCount--;
                updateUI();
                break;
            case 'GAME_STARTED':
                // Início do jogo (você pode redirecionar para a tela de jogo real depois)
                alert('A partida vai começar!');
                // window.location.href = '/play.html';
                break;
            case 'ERROR':
                alert(msg.payload.message);
                if (msg.payload.message.includes('sala')) {
                    window.location.href = '/lobby.html';
                }
                break;
        }
    });

    // Solicita informações da sala ao servidor
    window.gameClient.send('GET_GAME_INFO', { gameCode });

    // Botão iniciar jogo (apenas para o dono da sala)
    document.getElementById('start-game-btn').addEventListener('click', () => {
        if (isOwner && currentPlayersCount >= 3) {
            window.gameClient.send('START_GAME', { gameCode });
        } else {
            alert('Apenas o criador pode iniciar e é necessário pelo menos 3 jogadores.');
        }
    });

    // Botão sair da sala
    document.getElementById('leave-room-btn').addEventListener('click', () => {
        window.gameClient.send('LEAVE_GAME', { gameCode });
        window.location.href = '/lobby.html';
    });

    function updateUI() {
        document.getElementById('current-players').innerText = currentPlayersCount;
        document.getElementById('max-players').innerText = maxPlayers;
        const startBtn = document.getElementById('start-game-btn');
        const waitingMsg = document.getElementById('waiting-message');

        if (currentPlayersCount >= 3) {
            waitingMsg.innerHTML = 'Número mínimo de jogadores atingido!';
            if (isOwner) {
                startBtn.disabled = false;
                startBtn.style.opacity = '1';
                startBtn.style.cursor = 'pointer';
            } else {
                startBtn.disabled = true;
                startBtn.style.opacity = '0.5';
            }
        } else {
            waitingMsg.innerHTML = `Aguardando mais ${3 - currentPlayersCount} jogador(es) para iniciar (mínimo 3)...`;
            startBtn.disabled = true;
            startBtn.style.opacity = '0.5';
        }
    }
});