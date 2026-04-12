document.addEventListener('DOMContentLoaded', async () => {
    const statusDiv = document.getElementById('status');
    const registerBtn = document.getElementById('register-btn');
    const usernameInput = document.getElementById('reg-username');
    const emailInput = document.getElementById('reg-email');
    const passwordInput = document.getElementById('reg-password');

    // Conecta WebSocket
    try {
        await window.gameClient.connect();
        console.log('Conectado ao servidor');
    } catch (err) {
        statusDiv.innerText = 'Erro de conexão com o servidor.';
        statusDiv.style.color = '#e74c3c';
        return;
    }

    window.addEventListener('gameMessage', (event) => {
        const msg = event.detail;
        if (msg.type === 'REGISTER_SUCCESS') {
            statusDiv.innerText = 'Cadastro realizado! Faça login.';
            statusDiv.style.color = '#2ecc71';
            setTimeout(() => {
                window.location.href = '/login.html';
            }, 2000);
        } else if (msg.type === 'ERROR') {
            statusDiv.innerText = msg.payload.message || 'Erro no cadastro';
            statusDiv.style.color = '#e74c3c';
        }
    });

    registerBtn.addEventListener('click', () => {
        const username = usernameInput.value.trim();
        const email = emailInput.value.trim();
        const password = passwordInput.value.trim();
        if (!username || !email || !password) {
            statusDiv.innerText = 'Preencha todos os campos';
            statusDiv.style.color = '#e74c3c';
            return;
        }
        statusDiv.innerText = 'Enviando...';
        window.gameClient.register(username, email, password);
    });
});