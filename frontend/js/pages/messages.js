// ==================== 消息中心模块 ====================

async function renderMessagesPage() {
    try {
        const data = await api('/api/messages');
        const msgs = (data.messages || []).map(msg => `
            <li class="p-4 flex justify-between items-center hover:bg-gray-50 cursor-pointer" onclick="showMessageDetail(${msg.id}, '${msg.type}', '${(msg.title||'').replace(/'/g,"\\'")}', '${(msg.content||'').replace(/'/g,"\\'")}', '${(msg.from_user||'').replace(/'/g,"\\'")}', '${msg.status}', '${msg.created_at||''}', '${(msg.reply_content||'').replace(/'/g,"\\'")}')">
                <div class="flex items-center gap-4">
                    ${msg.status === '未读' ? '<div class="w-2.5 h-2.5 bg-indigo-500 rounded-full"></div>' : '<div class="w-2.5 h-2.5"></div>'}
                    <div>
                        <span class="px-2 py-0.5 text-xs rounded font-medium ${msg.type === '问题反馈' ? 'bg-yellow-100 text-yellow-800' : msg.type === '问题答复' ? 'bg-blue-100 text-blue-800' : 'bg-purple-100 text-purple-800'}">${msg.type}</span>
                        <p class="font-semibold mt-1">${msg.title}</p>
                        <p class="text-sm text-gray-500">来自: ${msg.from_user}</p>
                    </div>
                </div>
                <div class="text-sm text-gray-400">${msg.created_at || ''}</div>
            </li>`).join('');
        return `
        <div class="page">
            <div class="flex justify-between items-center mb-6">
                <h1 class="text-3xl font-bold">消息中心</h1>
                <button onclick="showFeedbackForm()" class="bg-indigo-500 text-white font-bold py-2 px-4 rounded-lg hover:bg-indigo-600 transition flex items-center gap-2">
                    <i class="fas fa-plus"></i> 问题反馈
                </button>
            </div>
            <div id="feedback-form-container"></div>
            <div class="bg-white rounded-lg shadow-md">
                <ul class="divide-y divide-gray-200">${msgs || '<li class="p-4 text-center text-gray-400">暂无消息</li>'}</ul>
            </div>
        </div>`;
    } catch(e) { return `<div class="page"><p class="text-red-500">${e.message}</p></div>`; }
}

// 显示消息详情
async function showMessageDetail(id, type, title, content, from, status, date, reply) {
    if (status === '未读') {
        try { await api(`/api/messages/${id}/read`, { method: 'POST' }); loadUnreadCount(); } catch(e) {}
    }
    const modal = document.createElement('div');
    modal.className = 'fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50';
    modal.onclick = (e) => { if (e.target === modal) modal.remove(); };
    let replyHtml = '';
    if (type === '问题反馈' && (currentUser?.role === 'admin' || currentUser?.role === 'author')) {
        replyHtml = `<div class="mt-4 border-t pt-4"><textarea id="reply-content" class="w-full border rounded-lg p-2" rows="2" placeholder="输入回复内容..."></textarea>
            <button onclick="replyMessage(${id})" class="mt-2 bg-indigo-500 text-white px-4 py-2 rounded-lg">发送回复</button></div>`;
    }
    if (reply) {
        replyHtml += `<div class="mt-4 bg-gray-50 p-3 rounded"><p class="text-sm font-semibold text-gray-600">回复:</p><p class="text-sm">${reply}</p></div>`;
    }
    modal.innerHTML = `<div class="bg-white rounded-lg p-6 max-w-lg w-full mx-4">
        <div class="flex justify-between items-center mb-4"><h2 class="text-xl font-bold">${title}</h2><button onclick="this.closest('.fixed').remove()" class="text-gray-400 hover:text-gray-600"><i class="fas fa-times"></i></button></div>
        <span class="px-2 py-0.5 text-xs rounded font-medium ${type === '问题反馈' ? 'bg-yellow-100 text-yellow-800' : type === '问题答复' ? 'bg-blue-100 text-blue-800' : 'bg-purple-100 text-purple-800'}">${type}</span>
        <p class="text-gray-500 text-sm mt-2">来自: ${from} | ${date}</p>
        <p class="mt-4">${content || '无内容'}</p>
        ${replyHtml}
    </div>`;
    document.body.appendChild(modal);
}

// 回复消息
async function replyMessage(msgId) {
    const content = document.getElementById('reply-content')?.value?.trim();
    if (!content) return showToast('请输入回复内容', 'error');
    try {
        await api(`/api/messages/${msgId}/reply`, { method: 'POST', json: { reply: content } });
        showToast('回复成功');
        document.querySelector('.fixed')?.remove();
        switchPage('messages');
    } catch(e) { showToast(e.message, 'error'); }
}

// 显示反馈表单
function showFeedbackForm() {
    const container = document.getElementById('feedback-form-container');
    container.innerHTML = `
    <div class="bg-white rounded-lg shadow-md p-6 mb-6">
        <h3 class="font-bold mb-4">提交问题反馈</h3>
        <input id="fb-title" class="w-full border rounded-lg px-3 py-2 mb-3" placeholder="标题">
        <textarea id="fb-content" class="w-full border rounded-lg px-3 py-2 mb-3" rows="3" placeholder="详细描述您遇到的问题..."></textarea>
        <div class="flex gap-2">
            <button onclick="submitFeedback()" class="bg-indigo-500 text-white px-4 py-2 rounded-lg">提交</button>
            <button onclick="document.getElementById('feedback-form-container').innerHTML=''" class="bg-gray-200 px-4 py-2 rounded-lg">取消</button>
        </div>
    </div>`;
}

// 提交反馈
async function submitFeedback() {
    const title = document.getElementById('fb-title')?.value?.trim();
    const content = document.getElementById('fb-content')?.value?.trim();
    if (!title || !content) return showToast('请填写标题和内容', 'error');
    try {
        await api('/api/messages/create', { method: 'POST', json: { type: '问题反馈', title, content } });
        showToast('反馈已提交');
        document.getElementById('feedback-form-container').innerHTML = '';
        switchPage('messages');
    } catch(e) { showToast(e.message, 'error'); }
}
