// ==================== 个人中心模块 ====================

async function renderProfilePage() {
    if (!currentUser) return '<div class="page"><p class="text-red-500">请先登录</p></div>';
    try {
        const u = await api('/api/auth/me');
        return `
        <div class="page">
            <h1 class="text-3xl font-bold mb-6">个人中心</h1>
            <div class="bg-white rounded-lg shadow-md p-8 max-w-2xl mx-auto">
                <form class="space-y-6" onsubmit="saveProfile(event)">
                    <div class="flex items-center gap-4">
                        <div class="w-20 h-20 rounded-full bg-indigo-600 text-white flex items-center justify-center text-3xl font-bold">${(u.nickname || u.username || 'U').charAt(0).toUpperCase()}</div>
                        <div><h2 class="text-2xl font-bold">${u.nickname || u.username}</h2><p class="text-gray-500">用户ID: ${u.id}</p></div>
                    </div>
                    <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                        <div><label class="block text-sm font-medium text-gray-700 flex items-center gap-2"><i class="fas fa-user"></i>昵称</label>
                            <input type="text" id="pf-nickname" value="${u.nickname || ''}" class="mt-1 block w-full rounded-md bg-gray-100 border-transparent focus:border-indigo-500 focus:bg-white focus:ring-0"></div>
                        <div><label class="block text-sm font-medium text-gray-700 flex items-center gap-2"><i class="fas fa-building"></i>公司</label>
                            <input type="text" id="pf-company" value="${u.company || ''}" class="mt-1 block w-full rounded-md bg-gray-100 border-transparent focus:border-indigo-500 focus:bg-white focus:ring-0"></div>
                    </div>
                    <div><label class="block text-sm font-medium text-gray-700 flex items-center gap-2"><i class="fas fa-building"></i>部门</label>
                        <input type="text" id="pf-department" value="${u.department || ''}" class="mt-1 block w-full rounded-md bg-gray-100 border-transparent focus:border-indigo-500 focus:bg-white focus:ring-0"></div>
                    <div><label class="block text-sm font-medium text-gray-700 flex items-center gap-2"><i class="fas fa-file-alt"></i>个人简介</label>
                        <textarea id="pf-bio" class="mt-1 block w-full rounded-md bg-gray-100 border-transparent focus:border-indigo-500 focus:bg-white focus:ring-0" rows="3">${u.bio || ''}</textarea></div>
                    <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                        <div><label class="block text-sm font-medium text-gray-700 flex items-center gap-2"><i class="fas fa-envelope"></i>邮箱</label>
                            <input type="email" id="pf-email" value="${u.email || ''}" class="mt-1 block w-full rounded-md bg-gray-100 border-transparent focus:border-indigo-500 focus:bg-white focus:ring-0"></div>
                        <div><label class="block text-sm font-medium text-gray-700 flex items-center gap-2"><i class="fas fa-phone"></i>手机号</label>
                            <input type="tel" id="pf-phone" value="${u.phone || ''}" class="mt-1 block w-full rounded-md bg-gray-100 border-transparent focus:border-indigo-500 focus:bg-white focus:ring-0"></div>
                    </div>
                    <div class="border-t border-gray-200 pt-6">
                        <label class="block text-sm font-medium text-gray-700 flex items-center gap-2"><i class="fas fa-lock"></i>修改密码 (留空不修改)</label>
                        <input type="password" id="pf-password" placeholder="输入新密码" class="mt-1 block w-full rounded-md bg-gray-100 border-transparent focus:border-indigo-500 focus:bg-white focus:ring-0">
                    </div>
                    <div class="text-right">
                        <button type="submit" class="bg-indigo-600 text-white font-semibold py-2 px-6 rounded-lg hover:bg-indigo-700 inline-flex items-center gap-2"><i class="fas fa-save"></i>保存修改</button>
                    </div>
                </form>
            </div>
        </div>`;
    } catch(e) { return `<div class="page"><p class="text-red-500">${e.message}</p></div>`; }
}

// 保存个人资料
async function saveProfile(e) {
    e.preventDefault();
    try {
        const body = {
            nickname: document.getElementById('pf-nickname').value,
            company: document.getElementById('pf-company').value,
            department: document.getElementById('pf-department').value,
            bio: document.getElementById('pf-bio').value,
            email: document.getElementById('pf-email').value,
            phone: document.getElementById('pf-phone').value,
        };
        const pw = document.getElementById('pf-password').value;
        if (pw) body.password = pw;
        await api('/api/auth/update_profile', { method: 'PUT', json: body });
        const u = await api('/api/auth/me');
        currentUser = u;
        localStorage.setItem('user', JSON.stringify(currentUser));
        document.getElementById('avatar-circle').textContent = (u.nickname || u.username || 'U').charAt(0).toUpperCase();
        showToast('保存成功');
    } catch(err) { showToast(err.message, 'error'); }
}
