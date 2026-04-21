/**
 * Hook universal engatilhado com a DOM de qualquer view. 
 * Garante que a malha de conexão se estabeleça para despachar Handlers sem delay visual.
 */
document.addEventListener('DOMContentLoaded', async () => {

    // Se auth.js não estiver carregado ainda, sai
    if (!window.gameClient) return;

    try {
        await window.gameClient.connect();
        console.log('Conexão global ativa');

        // restaura sessão automaticamente
        window.gameClient.restoreSession();

    } catch (err) {
        console.error('Erro ao conectar:', err);
    }
});