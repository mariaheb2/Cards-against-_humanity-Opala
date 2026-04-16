document.addEventListener('DOMContentLoaded', async () => {

    // Parâmetros da URL
    const urlParams = new URLSearchParams(window.location.search);
    const gameId = urlParams.get('gameId');

    if (!gameId) {
        alert('ID do jogo não encontrado. Voltando ao lobby.');
        window.location.href = '/lobby.html';
        return;
    }

    // Estado local
    let myUserId = window.gameClient.userId || localStorage.getItem('userId');
    let myUsername = window.gameClient.username || localStorage.getItem('username');
    let isJudge = false;
    let selectedCardId = null;    // carta branca selecionada
    let currentRound = 0;
    let hasPlayed = false;    // já jogou nesta rodada
    let scores = {};       // { playerId: { username, score } }
    let playerDots = {};       // { playerId: played:bool }

    // Conectar WebSocket 
    showLoading('Conectando ao servidor...');
    try {
        if (!window.gameClient.ws || window.gameClient.ws.readyState !== WebSocket.OPEN) {
            await window.gameClient.connect();
        }
        window.gameClient.restoreSession();
        myUserId = window.gameClient.userId || localStorage.getItem('userId');
        myUsername = window.gameClient.username || localStorage.getItem('username');
    } catch (err) {
        hideLoading();
        alert('Erro ao conectar ao servidor.');
        window.location.href = '/lobby.html';
        return;
    }

    // Pequeno delay para garantir que RESTORE_SESSION chegou ao server
    await delay(400);
    hideLoading();

    // Recuperar dados da rodada inicial (da transição do game.js)
    const savedRoundData = sessionStorage.getItem('initialRoundData');
    if (savedRoundData) {
        try {
            console.log('[play.js] Recuperando dados da primeira rodada via sessionStorage');
            const parsed = JSON.parse(savedRoundData);
            onNewRound(parsed);
        } catch (e) {
            console.error('Erro ao processar initialRoundData:', e);
        }
        sessionStorage.removeItem('initialRoundData');
    }

    // Escutar mensagens do servidor 
    window.addEventListener('gameMessage', (e) => {
        const msg = e.detail;
        console.log('[play.js] msg:', msg.type, msg.payload);

        switch (msg.type) {

            // Início de nova rodada 
            case 'NEW_ROUND':
                onNewRound(msg.payload);
                break;

            // Alguém jogou uma carta 
            case 'PLAYER_PLAYED':
                onPlayerPlayed(msg.payload);
                break;

            // Todos jogaram → juiz escolhe 
            case 'JUDGE_SELECTING':
                onJudgeSelecting(msg.payload);
                break;

            // Resultado da rodada 
            case 'ROUND_RESULT':
                onRoundResult(msg.payload);
                break;

            // Respostas reveladas pelo juiz
            case 'CARDS_REVEALED':
                onCardsRevealed(msg.payload);
                break;

            // Fim de jogo 
            case 'GAME_FINISHED':
                onGameFinished(msg.payload);
                break;

            // Erros
            case 'ERROR':
                showError(msg.payload.message);
                break;
        }
    });

    // Handlers dos eventos de jogo

    function onNewRound(payload) {
        currentRound = payload.round;
        isJudge = payload.isJudge;
        hasPlayed = false;
        selectedCardId = null;
        playerDots = {};

        // Atualiza HUD
        setHUDRound(currentRound);
        setHUDJudge(payload.judgeId); // será substituído pelo username via scores

        // Mostra carta preta
        showBlackCard(payload.questionCard.text);

        // Esconde área de juiz e reexibe status
        hideJudgeArea();
        showPlayStatus();

        // Mão do jogador
        if (!isJudge) {
            renderHand(payload.hand);
            setHandHint('Selecione uma carta para jogar');
            document.getElementById('hand-label').innerHTML = 'Suas cartas';
        } else {
            renderHand([]); // juiz não joga
            setHandHint('Você é o juiz desta rodada — aguarde as respostas!');
            showJudgeBadge();
        }

        // Inicializa dots de progresso (quem jogou)
        // O payload não traz todos os jogadores — esperamos PLAYER_PLAYED
        updateStatusLabel();
    }

    function onPlayerPlayed(payload) {
        playerDots[payload.playerId] = true;
        updateStatusLabel();

        // Feedback animado
        addPlayedDot(payload.username);
    }

    function onJudgeSelecting(payload) {
        if (isJudge) {
            const area = document.getElementById('judge-area');
            const grid = document.getElementById('played-cards-grid');
            const label = document.getElementById('judge-area-label');

            label.innerHTML = '<button id="btn-reveal-cards" class="btn-primary" style="margin-top: 10px;">Revelar Respostas Anonimas</button>';
            grid.innerHTML = '';

            area.style.display = 'block';
            area.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
            document.getElementById('play-status').style.display = 'none';

            document.getElementById('btn-reveal-cards').addEventListener('click', () => {
                // Desabilita botão após click
                document.getElementById('btn-reveal-cards').disabled = true;
                document.getElementById('btn-reveal-cards').textContent = 'Revelando...';
                window.gameClient.send('REVEAL_CARDS', { 
                    gameId: payload.gameId, 
                    playedCards: payload.playedCards 
                });
            }, { once: true });
        } else {
            // Jogadores comuns veem mensagem de espera
            setHandHint('⏳ Aguardando o juiz escolher a melhor resposta...');
            disableHand();
            setStatusLabel('⚖️ Juiz avaliando as respostas...');
        }
    }

    function onCardsRevealed(payload) {
        showJudgeArea(payload.playedCards, payload.gameId);
    }

    function onRoundResult(payload) {
        // Atualiza placar
        if (payload.scores) {
            payload.scores.forEach(s => {
                scores[s.playerId] = { username: s.username, score: s.score };
            });
            renderScoreboard();
        }
        // Mostra modal de resultado
        showRoundResultModal(payload);
    }

    function onGameFinished(payload) {
        // Garante que o placar está atualizado
        if (payload.finalScores) {
            payload.finalScores.forEach(s => {
                scores[s.playerId] = { username: s.username, score: s.score };
            });
        }
        showGameFinishedModal(payload);
    }

    // Renderização: cartas da mão 

    function renderHand(hand) {
        const container = document.getElementById('hand-cards');
        container.innerHTML = '';
        selectedCardId = null;

        hand.forEach(card => {
            const div = document.createElement('div');
            div.className = 'white-card';
            div.id = `hand-card-${card.id}`;
            div.setAttribute('tabindex', '0');
            div.setAttribute('role', 'button');
            div.setAttribute('aria-label', `Carta: ${card.text}`);
            div.innerHTML = `
                <div class="white-card-text">${card.text}</div>
                <div class="white-card-footer">
                    <span>Cards Against Humanity</span>
                    <span class="white-card-icon">♠</span>
                </div>
            `;
            div.addEventListener('click', () => selectCard(card.id, div));
            div.addEventListener('keydown', (e) => {
                if (e.key === 'Enter' || e.key === ' ') selectCard(card.id, div);
            });
            container.appendChild(div);
        });
    }

    function selectCard(cardId, el) {
        if (isJudge || hasPlayed) return;

        // Des-seleciona anterior
        document.querySelectorAll('.white-card.selected').forEach(c => c.classList.remove('selected'));

        selectedCardId = cardId;
        el.classList.add('selected');

        // Mostra botão de confirmar jogada (ou auto-joga)
        playSelectedCard();
    }

    function playSelectedCard() {
        if (!selectedCardId || hasPlayed) return;
        hasPlayed = true;
        disableHand();
        setHandHint('✅ Carta enviada! Aguardando outros jogadores...');

        window.gameClient.send('PLAY_CARD', {
            gameId,
            cardId: selectedCardId
        });

        // Remove a carta jogada visualmente
        const played = document.getElementById(`hand-card-${selectedCardId}`);
        if (played) {
            played.classList.add('played');
            setTimeout(() => played.remove(), 400);
        }
    }

    // Renderização: área do juiz 

    function showJudgeArea(playedCards, gId) {
        const area = document.getElementById('judge-area');
        const grid = document.getElementById('played-cards-grid');
        const label = document.getElementById('judge-area-label');

        if (isJudge) {
            label.textContent = '⚖️ Selecione a melhor resposta';
        } else {
            label.textContent = '👀 Respostas reveladas! O juiz está selecionando a melhor...';
        }
        
        grid.innerHTML = '';

        playedCards.forEach((pc, idx) => {
            const div = document.createElement('div');
            div.className = 'white-card played-card';
            div.id = `played-card-${pc.playedCardId}`;
            div.setAttribute('tabindex', '0');
            div.setAttribute('role', 'button');
            div.setAttribute('aria-label', `Resposta ${idx + 1}: ${pc.text}`);
            div.innerHTML = `
                <div class="white-card-text">${pc.text}</div>
                <div class="white-card-footer">
                    <span>Cards Against Humanity</span>
                    <span class="white-card-icon">♠</span>
                </div>
            `;
            div.addEventListener('click', () => {
                if (!isJudge) return;
                selectWinner(pc.playedCardId, gId || gameId);
                div.classList.add('winner-selected');
            });
            div.addEventListener('keydown', (e) => {
                if ((e.key === 'Enter' || e.key === ' ') && isJudge) {
                    selectWinner(pc.playedCardId, gId || gameId);
                }
            });
            grid.appendChild(div);

            // Stagger animation
            setTimeout(() => div.classList.add('reveal'), idx * 120);
        });

        area.style.display = 'block';
        area.scrollIntoView({ behavior: 'smooth', block: 'nearest' });

        // Esconde a status bar durante seleção
        document.getElementById('play-status').style.display = 'none';
    }

    function hideJudgeArea() {
        const area = document.getElementById('judge-area');
        area.style.display = 'none';
    }

    function selectWinner(playedCardId, gId) {
        // Desabilita todas as cartas após seleção
        document.querySelectorAll('.played-card').forEach(c => {
            c.style.pointerEvents = 'none';
        });
        const label = document.getElementById('judge-area-label');
        label.textContent = '⏳ Enviando escolha...';

        window.gameClient.send('SELECT_WINNER', {
            gameId: gId || gameId,
            playedCardId
        });
    }

    // Modais

    function showRoundResultModal(payload) {
        document.getElementById('round-winner-name').textContent =
            `🎉 ${payload.username} venceu com ${payload.score} ponto(s)!`;

        // Mostrar a carta escolhida
        const winningCardArea = document.getElementById('round-winning-card');
        if (winningCardArea) {
            winningCardArea.style.display = 'block';
            winningCardArea.innerHTML = `
                <div class="white-card" style="margin: 0 auto; color: #111; pointer-events: none;">
                    <div class="white-card-text">${payload.winningCardText || '...'}</div>
                    <div class="white-card-footer">
                        <span>Cards Against Humanity</span>
                        <span class="white-card-icon">♠</span>
                    </div>
                </div>
            `;
        }

        // Mostra placar no modal
        const scoresEl = document.getElementById('round-scores');
        scoresEl.innerHTML = buildScoresHTML(payload.scores);

        document.getElementById('round-result-overlay').style.display = 'flex';

        document.getElementById('round-next-btn').onclick = () => {
            document.getElementById('round-result-overlay').style.display = 'none';
        };
    }

    function showGameFinishedModal(payload) {
        // Fecha qualquer modal de rodada aberto
        document.getElementById('round-result-overlay').style.display = 'none';

        document.getElementById('game-winner-name').textContent =
            `🏆 ${payload.username} é o vencedor!`;

        const table = document.getElementById('final-scores-table');
        table.innerHTML = buildScoresHTML(payload.finalScores || Object.values(scores).map(s => s));

        spawnConfetti();

        document.getElementById('game-finished-overlay').style.display = 'flex';

        document.getElementById('play-again-btn').onclick = () => {
            window.location.href = '/lobby.html';
        };
        document.getElementById('back-lobby-btn').onclick = () => {
            window.location.href = '/lobby.html';
        };
    }

    function buildScoresHTML(scoresArr) {
        if (!scoresArr || scoresArr.length === 0) return '';
        const sorted = [...scoresArr].sort((a, b) => (b.score || 0) - (a.score || 0));
        return `<table class="scores-table">
            <thead><tr><th>#</th><th>Jogador</th><th>Pontos</th></tr></thead>
            <tbody>
                ${sorted.map((s, i) => `
                    <tr class="${i === 0 ? 'top-score' : ''}">
                        <td>${i === 0 ? '🥇' : i + 1}</td>
                        <td>${s.username}</td>
                        <td>${s.score || 0}</td>
                    </tr>
                `).join('')}
            </tbody>
        </table>`;
    }

    // Placar lateral

    function renderScoreboard() {
        const list = document.getElementById('scoreboard-list');
        list.innerHTML = '';
        const sorted = Object.values(scores).sort((a, b) => b.score - a.score);
        if (sorted.length === 0) {
            list.innerHTML = '<li class="scoreboard-empty">Nenhum placar ainda</li>';
            return;
        }
        sorted.forEach((s, i) => {
            const li = document.createElement('li');
            li.className = 'scoreboard-item' + (i === 0 ? ' leader' : '');
            const isMe = s.username === myUsername;
            li.innerHTML = `
                <span class="sb-rank">${i === 0 ? '👑' : i + 1}</span>
                <span class="sb-name">${s.username}${isMe ? ' <em>(eu)</em>' : ''}</span>
                <span class="sb-score">${s.score}</span>
            `;
            list.appendChild(li);
        });
    }

    // HUD helpers 

    function setHUDRound(r) {
        document.getElementById('hud-round').textContent = r;
    }

    function setHUDJudge(judgeId) {
        // O judgeId vem como playerId — tentamos mapear para username via scores
        const s = scores[judgeId];
        const name = s ? s.username : (judgeId ? '...' : '―');
        document.getElementById('hud-judge').textContent = name;
    }

    function showBlackCard(text) {
        const card = document.getElementById('black-card');
        const textEl = document.getElementById('black-card-text');
        card.classList.remove('animate-in');
        void card.offsetWidth; // reflow
        textEl.textContent = text;
        card.classList.add('animate-in');
    }

    function showPlayStatus() {
        document.getElementById('play-status').style.display = 'flex';
    }

    function setStatusLabel(text) {
        document.getElementById('status-label').textContent = text;
    }

    function updateStatusLabel() {
        const played = Object.keys(playerDots).length;
        setStatusLabel(`${played} jogador(es) já jogaram — aguardando os demais...`);
    }

    function addPlayedDot(username) {
        const dots = document.getElementById('players-played-dots');
        const span = document.createElement('span');
        span.className = 'played-dot';
        span.title = username + ' jogou';
        dots.appendChild(span);
    }

    function disableHand() {
        document.querySelectorAll('.white-card').forEach(c => {
            c.style.pointerEvents = 'none';
            c.style.opacity = '0.6';
        });
    }

    function setHandHint(text) {
        document.getElementById('hand-hint').textContent = text;
    }

    function showJudgeBadge() {
        const label = document.getElementById('hand-label');
        label.innerHTML = '⚖️ <span class="judge-badge">Você é o Juiz</span>';
    }

    // Loading overlay 

    function showLoading(text) {
        const ov = document.getElementById('loading-overlay');
        document.getElementById('loading-text').textContent = text || 'Carregando...';
        ov.style.display = 'flex';
    }

    function hideLoading() {
        document.getElementById('loading-overlay').style.display = 'none';
    }

    function showError(message) {
        // Toast de erro
        const toast = document.createElement('div');
        toast.className = 'error-toast';
        toast.textContent = '⚠️ ' + message;
        document.body.appendChild(toast);
        setTimeout(() => toast.classList.add('show'), 10);
        setTimeout(() => {
            toast.classList.remove('show');
            setTimeout(() => toast.remove(), 400);
        }, 4000);
    }

    // Confetti

    function spawnConfetti() {
        const area = document.getElementById('confetti-area');
        const colors = ['#e74c3c', '#f39c12', '#2ecc71', '#3498db', '#9b59b6', '#fff'];
        for (let i = 0; i < 60; i++) {
            const div = document.createElement('div');
            div.className = 'confetti-piece';
            div.style.left = Math.random() * 100 + '%';
            div.style.background = colors[Math.floor(Math.random() * colors.length)];
            div.style.animationDelay = Math.random() * 1.5 + 's';
            div.style.animationDuration = (1.5 + Math.random() * 1.5) + 's';
            area.appendChild(div);
        }
    }

    // Utilitários

    function delay(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }
});
