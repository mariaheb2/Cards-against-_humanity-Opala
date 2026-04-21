document.addEventListener('DOMContentLoaded', async () => {
    // ── Elementos DOM ──────────────────────────────────────────────────────────
    const usernameSpan = document.getElementById('lobby-username');
    const onlineList = document.getElementById('online-list');
    const gameCodeDisplay = document.getElementById('game-code-display');
    const roomsList = document.getElementById('rooms-list');
    const cardTextArea = document.getElementById('card-text');
    const cardCharCount = document.getElementById('card-char-count');
    const cardFeedback = document.getElementById('card-feedback');
    const joinRequestModal = document.getElementById('join-request-modal');
    const requesterNameEl = document.getElementById('requester-name');
    const toast = document.getElementById('toast');

    // Pedido pendente de aprovação (guardado para usar nos botões do modal)
    let pendingRequestId = null;
    // Tipo de carta selecionado
    let selectedCardType = 'QUESTION';
    // Flag: auto-refresh de salas
    let roomsRefreshTimer = null;

    // ── Conexão WebSocket ──────────────────────────────────────────────────────
    if (!window.gameClient.ws || window.gameClient.ws.readyState !== WebSocket.OPEN) {
        try {
            await window.gameClient.connect();
        } catch (err) {
            alert('Erro de conexão com o servidor');
            window.location.href = '/login.html';
            return;
        }
    }

    // Restaura sessão
    window.gameClient.restoreSession();
    usernameSpan.innerText = window.gameClient.username || localStorage.getItem('username');

    // Solicita lista de usuários e salas ao entrar no lobby
    window.gameClient.send('LIST_USERS', {});
    window.gameClient.send('LIST_OPEN_ROOMS', {});

    // Auto-refresh de salas a cada 10 segundos
    roomsRefreshTimer = setInterval(() => {
        window.gameClient.send('LIST_OPEN_ROOMS', {});
    }, 10000);

    // ── Listener de mensagens do servidor ──────────────────────────────────────
    window.addEventListener('gameMessage', (event) => {
        const msg = event.detail;
        switch (msg.type) {

            case 'USER_LIST':
                renderOnlineUsers(msg.payload.users);
                break;

            // Salas abertas
            case 'OPEN_ROOMS':
                renderOpenRooms(msg.payload.rooms);
                break;

            // Criação de partida bem-sucedida
            case 'GAME_CREATED': {
                console.log('GAME_CREATED recebido:', msg.payload);
                const gameId = msg.payload.gameId || msg.payload.gameCode;
                window.location.replace(`/game.html?gameCode=${encodeURIComponent(gameId)}`);
                break;
            }

            case 'GAME_CODE': {
                const code = msg.payload.gameId || msg.payload.gameCode;
                gameCodeDisplay.innerText = code;
                gameCodeDisplay.style.display = 'block';
                window.location.replace(`/game.html?gameCode=${encodeURIComponent(code)}`);
                break;
            }

            case 'PLAYER_JOINED': {
                if (msg.payload && msg.payload.gameId) {
                    window.location.replace(`/game.html?gameCode=${encodeURIComponent(msg.payload.gameId)}`);
                }
                break;
            }

            // Carta criada com sucesso
            case 'CARD_CREATED':
                showCardFeedback(`✅ Carta criada: "${msg.payload.text}" (${msg.payload.cardType})`, 'success');
                cardTextArea.value = '';
                cardCharCount.textContent = '0/300';
                break;

            // Pop-up de aprovação de entrada (o DONO da sala recebe)
            case 'JOIN_REQUEST':
                pendingRequestId = msg.payload.requestId;
                requesterNameEl.textContent = msg.payload.requesterName;
                joinRequestModal.style.display = 'flex';
                break;

            // Entrada aceita pelo dono — redireciona o solicitante para a sala
            case 'JOIN_ACCEPTED':
                hideModal();
                showToast('🎉 Entrada aceita! Entrando na sala...');
                clearInterval(roomsRefreshTimer);
                setTimeout(() => {
                    const gId = msg.payload.gameId || msg.payload.gameCode;
                    window.location.replace(`/game.html?gameCode=${encodeURIComponent(gId)}`);
                }, 800);
                break;

            // Entrada rejeitada pelo dono
            case 'JOIN_REJECTED':
                showToast('❌ O criador da sala recusou sua entrada.', 'error');
                break;

            case 'ERROR':
                showToast('⚠️ ' + msg.payload.message, 'error');
                break;
        }
    });

    // Criar Partida 
    document.getElementById('create-game-btn').addEventListener('click', () => {
        const maxPlayers = parseInt(document.getElementById('max-players').value);
        const targetScore = parseInt(document.getElementById('target-score').value);
        window.gameClient.send('CREATE_GAME', { maxPlayers, targetScore });
    });

    // Entrar por código 
    document.getElementById('join-game-btn').addEventListener('click', () => {
        const gameCode = document.getElementById('join-code').value.trim();
        if (!gameCode) return;
        window.gameClient.send('JOIN_GAME', { gameCode, gameId: gameCode });
    });

    // Refresh manual de salas 
    document.getElementById('refresh-rooms-btn').addEventListener('click', () => {
        window.gameClient.send('LIST_OPEN_ROOMS', {});
    });

    // Criar Carta 

    // Contador de caracteres
    cardTextArea.addEventListener('input', () => {
        const len = cardTextArea.value.length;
        cardCharCount.textContent = `${len}/300`;
    });

    // Seleção de tipo de carta (toggle)
    document.getElementById('card-type-question').addEventListener('click', () => {
        selectedCardType = 'QUESTION';
        document.getElementById('card-type-question').classList.add('active');
        document.getElementById('card-type-answer').classList.remove('active');
    });
    document.getElementById('card-type-answer').addEventListener('click', () => {
        selectedCardType = 'ANSWER';
        document.getElementById('card-type-answer').classList.add('active');
        document.getElementById('card-type-question').classList.remove('active');
    });

    // Botão criar carta
    document.getElementById('create-card-btn').addEventListener('click', () => {
        const text = cardTextArea.value.trim();
        if (!text) {
            showCardFeedback('⚠️ Por favor, digite o texto da carta.', 'error');
            return;
        }
        window.gameClient.send('CREATE_CARD', { text, cardType: selectedCardType });
        showCardFeedback('⏳ Criando carta...', 'info');
    });

    // Aprovação de entrada (modal) 

    document.getElementById('approve-join-btn').addEventListener('click', () => {
        if (!pendingRequestId) return;
        window.gameClient.send('APPROVE_JOIN', { requestId: pendingRequestId });
        hideModal();
        pendingRequestId = null;
        // Atualiza a lista de salas (a sala agora pode ter +1 jogador)
        window.gameClient.send('LIST_OPEN_ROOMS', {});
    });

    document.getElementById('reject-join-btn').addEventListener('click', () => {
        if (!pendingRequestId) return;
        window.gameClient.send('REJECT_JOIN', { requestId: pendingRequestId });
        hideModal();
        pendingRequestId = null;
    });

    // Fecha o modal clicando fora do box
    joinRequestModal.addEventListener('click', (e) => {
        if (e.target === joinRequestModal) {
            // Não fecha automaticamente — o dono deve tomar uma decisão
        }
    });

    // Funções de renderização 

    /**
     * Atualiza a listagem visual do painel de jogadores on-line conectados globalmente no Socket.
     * @param {Array} users Conjunto de dicionários dos jogadores.
     */
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

    /**
     * Compila e injeta na tela cards HTML de salas que aguardam players no Lobby principal.
     * Adiciona interceptação de cliques aos botões daquelas não cheias.
     * @param {Array} rooms Dados descritivos de salas abertas extraídos do payload.
     */
    function renderOpenRooms(rooms) {
        roomsList.innerHTML = '';
        if (!rooms || rooms.length === 0) {
            roomsList.innerHTML = '<p class="rooms-empty">Nenhuma sala disponível no momento.</p>';
            return;
        }
        rooms.forEach(room => {
            const div = document.createElement('div');
            div.className = 'room-item';
            const isFull = room.playerCount >= room.maxPlayers;

            div.innerHTML = `
                <div class="room-info">
                    <span class="room-owner">👤 ${escapeHtml(room.ownerName)}</span>
                    <span class="room-players ${isFull ? 'full' : ''}">
                        ${room.playerCount}/${room.maxPlayers} jogadores
                    </span>
                    <span class="room-score">🏆 Meta: ${room.targetScore} pts</span>
                </div>
                <button class="btn-join-room" data-game-id="${room.gameId}" ${isFull ? 'disabled' : ''}>
                    ${isFull ? 'Cheia' : 'Entrar'}
                </button>
            `;
            roomsList.appendChild(div);
        });

        // Adiciona listeners nos botões de entrada
        roomsList.querySelectorAll('.btn-join-room:not(:disabled)').forEach(btn => {
            btn.addEventListener('click', () => {
                const gameId = btn.dataset.gameId;
                window.gameClient.send('REQUEST_JOIN', { gameId });
                btn.textContent = 'Aguardando...';
                btn.disabled = true;
                showToast('⏳ Pedido de entrada enviado. Aguarde a aprovação do criador.');
            });
        });
    }

    /**
     * Anima feedback visual rápido (Pill temporizado) para os formulários de criação de Cartas Customizadas.
     * @param {string} message Mensagem descritiva a ser lida.
     * @param {string} type Tipo de renderização (info, success, error)
     */
    function showCardFeedback(message, type = 'info') {
        cardFeedback.textContent = message;
        cardFeedback.className = `card-feedback card-feedback-${type}`;
        cardFeedback.style.display = 'block';
        if (type === 'success' || type === 'error') {
            setTimeout(() => { cardFeedback.style.display = 'none'; }, 4000);
        }
    }

    /**
     * Exibe os Toasts padronizados por tela. Usado para pop-ups de ingresso (Join).
     * @param {string} message Texto exibido flutuante.
     * @param {string} type Tipo gráfico de alerta.
     */
    function showToast(message, type = 'info') {
        toast.textContent = message;
        toast.className = `toast toast-${type}`;
        toast.style.display = 'block';
        toast.style.opacity = '1';
        clearTimeout(toast._timer);
        toast._timer = setTimeout(() => {
            toast.style.opacity = '0';
            setTimeout(() => { toast.style.display = 'none'; }, 400);
        }, 3500);
    }

    function hideModal() {
        joinRequestModal.style.display = 'none';
    }

    function escapeHtml(str) {
        const div = document.createElement('div');
        div.appendChild(document.createTextNode(str || ''));
        return div.innerHTML;
    }
});