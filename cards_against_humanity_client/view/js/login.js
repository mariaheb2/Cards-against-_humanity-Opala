document.addEventListener('DOMContentLoaded', async () => {
    const statusDiv = document.getElementById('status');
    const loginBtn = document.getElementById('login-btn');
    const emailInput = document.getElementById('login-email');
    const passwordInput = document.getElementById('login-password');

    try {
        await window.gameClient.connect();
        console.log('Conectado ao servidor');
    } catch (err) {
        statusDiv.innerText = 'Erro de conexão.';
        return;
    }

    window.addEventListener('gameMessage', (event) => {
        const msg = event.detail;
        console.log('Mensagem recebida:', msg);
        if (msg.type === 'LOGIN_SUCCESS') {
            window.gameClient.userId = msg.payload.userId;
            window.gameClient.username = msg.payload.username;
            localStorage.setItem('userId', msg.payload.userId);
            localStorage.setItem('username', msg.payload.username);
            statusDiv.innerText = 'Login bem-sucedido! Redirecionando...';
            setTimeout(() => {
                window.location.href = '/sobre.html';
            }, 1500);
        } else if (msg.type === 'LOGIN_ERROR') {
            statusDiv.innerText = msg.payload.message || 'Erro no login';
        } else if (msg.type === 'ERROR') {
            statusDiv.innerText = msg.payload.message;
        }
    });

    loginBtn.addEventListener('click', () => {
        const email = emailInput.value.trim();
        const password = passwordInput.value.trim();
        if (!email || !password) {
            statusDiv.innerText = 'Preencha e-mail e senha';
            return;
        }
        // Força o tipo LOGIN
        window.gameClient.send('LOGIN', { email, password });
    });
});