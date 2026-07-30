// ==================== 认证模块 ====================

// 切换登录/注册标签页
function switchLoginTab(tab) {
    document.getElementById('login-form').style.display = tab === 'login' ? 'block' : 'none';
    document.getElementById('register-form').style.display = tab === 'register' ? 'block' : 'none';
    document.getElementById('tab-login').className = `flex-1 py-2 rounded-lg font-semibold transition ${tab === 'login' ? 'bg-indigo-600 text-white' : 'text-gray-500'}`;
    document.getElementById('tab-register').className = `flex-1 py-2 rounded-lg font-semibold transition ${tab === 'register' ? 'bg-indigo-600 text-white' : 'text-gray-500'}`;
}

// 处理登录
async function handleLogin(e) {
    e.preventDefault();
    try {
        const data = await api('/api/auth/login', {
            method: 'POST',
            json: { username: document.getElementById('login-username').value, password: document.getElementById('login-password').value }
        });
        authToken = data.token;
        currentUser = data.user;
        localStorage.setItem('token', authToken);
        localStorage.setItem('user', JSON.stringify(currentUser));
        initApp();
        showToast('登录成功');
    } catch (err) { showToast(err.message, 'error'); }
}

// 检查用户名是否可用
async function checkUsername() {
    const username = document.getElementById('reg-username').value.trim();
    const checkDiv = document.getElementById('username-check');
    if (!username) {
        checkDiv.innerHTML = '';
        return;
    }
    try {
        const data = await api(`/api/auth/check_username?username=${encodeURIComponent(username)}`);
        if (data.exists) {
            checkDiv.innerHTML = '<span class="text-red-500"><i class="fas fa-times"></i> 用户名已存在</span>';
        } else {
            checkDiv.innerHTML = '<span class="text-green-500"><i class="fas fa-check"></i> 用户名可用</span>';
        }
    } catch (err) {
        checkDiv.innerHTML = '';
    }
}

// 处理注册
async function handleRegister(e) {
    e.preventDefault();
    try {
        const data = await api('/api/auth/register', {
            method: 'POST',
            json: { username: document.getElementById('reg-username').value, password: document.getElementById('reg-password').value, nickname: document.getElementById('reg-nickname').value, role: document.getElementById('reg-role').value }
        });
        authToken = data.token;
        currentUser = data.user;
        localStorage.setItem('token', authToken);
        localStorage.setItem('user', JSON.stringify(currentUser));
        initApp();
        showToast('注册成功');
    } catch (err) { showToast(err.message, 'error'); }
}

// 登出
function logout() {
    authToken = null;
    currentUser = null;
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    document.getElementById('app').style.display = 'none';
    document.getElementById('login-page').style.display = 'flex';
}

// 处理登出按钮点击
function handleLogout() { logout(); showToast('已退出登录'); }

// 初始化应用
function initApp() {
    document.getElementById('login-page').style.display = 'none';
    document.getElementById('app').style.display = 'block';
    if (currentUser) {
        document.getElementById('avatar-circle').textContent = (currentUser.nickname || currentUser.username || 'U').charAt(0).toUpperCase();
        document.getElementById('menu-manage').style.display = hasRole('author') ? 'block' : 'none';
    }
    loadUnreadCount();
    switchPage('home');
}

// 加载未读消息数
async function loadUnreadCount() {
    if (!authToken) return;
    try {
        const data = await api('/api/messages');
        const unread = data.messages.filter(m => m.status === '未读').length;
        const badge = document.getElementById('msg-badge');
        if (unread > 0) { badge.textContent = unread; badge.classList.remove('hidden'); }
        else { badge.classList.add('hidden'); }
    } catch(e) {}
}
