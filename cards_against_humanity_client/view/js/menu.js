(function() {
    // Só executa se não tiver sido inserido ainda
    if (document.getElementById('global-header')) return;

    // HTML do header fixo no topo
    const menuButton = `
        <button id="menu-toggle-btn" class="menu-toggle">☰</button>
    `;
    const logoLink = `
        <a href="/opaleiros.html" id="logo-link-bottom">
            <img src="/images/logo.png" alt="Opaleiros" id="site-logo-bottom">
        </a>
    `;

    // HTML do menu lateral (drawer)
    const drawerHTML = `
        <div id="side-drawer" class="side-drawer">
            <div class="drawer-header">
                <h3>Menu</h3>
                <button id="close-drawer-btn" class="close-drawer">✕</button>
            </div>
            <nav class="drawer-nav">
                <button id="nav-sobre-jogo" class="drawer-btn">Sobre o Jogo</button>
                <button id="nav-opaleiros" class="drawer-btn">Quem é a Opaleiros</button>
                <button id="nav-sobre-nos" class="drawer-btn">Sobre nós</button>
                <hr style="margin: 12px 0; border-color: #c0392b;">
                <button id="nav-logout" class="drawer-btn logout-btn">🚪 Sair</button>
            </nav>
        </div>
        <div id="drawer-overlay" class="drawer-overlay"></div>
    `;

    // Insere no body
    document.body.insertAdjacentHTML('afterbegin', menuButton);
    document.body.insertAdjacentHTML('beforeend', logoLink);
    document.body.insertAdjacentHTML('beforeend', drawerHTML);

    // Lógica de abrir/fechar drawer
    const menuBtn = document.getElementById('menu-toggle-btn');
    const drawer = document.getElementById('side-drawer');
    const overlay = document.getElementById('drawer-overlay');
    const closeBtn = document.getElementById('close-drawer-btn');

    function openDrawer() {
        drawer.classList.add('open');
        overlay.classList.add('active');
    }
    function closeDrawer() {
        drawer.classList.remove('open');
        overlay.classList.remove('active');
    }
    menuBtn.addEventListener('click', openDrawer);
    if (closeBtn) closeBtn.addEventListener('click', closeDrawer);
    overlay.addEventListener('click', closeDrawer);

    // Navegação dos botões do menu (redirecionamento)
    document.getElementById('nav-sobre-jogo')?.addEventListener('click', () => {
        window.location.href = '/sobre.html';
    });
    document.getElementById('nav-opaleiros')?.addEventListener('click', () => {
        window.location.href = '/opaleiros.html';
    });
    document.getElementById('nav-sobre-nos')?.addEventListener('click', () => {
        window.location.href = '/sobre-nos.html';
    });

    document.getElementById('nav-logout')?.addEventListener('click', () => {
        // Limpa localStorage
        localStorage.removeItem('userId');
        localStorage.removeItem('username');
        // Se existir uma conexão WebSocket global, fecha
        if (window.gameClient && window.gameClient.ws) {
            window.gameClient.logout();
        }
        // Redireciona para login
        window.location.href = '/login.html';
    });
})();