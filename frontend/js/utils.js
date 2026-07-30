// ==================== 工具函数模块 ====================

const categoryIcons = { '规划': 'fa-project-diagram', '建设': 'fa-hammer', '优化': 'fa-chart-line', '维护': 'fa-wrench', '客服': 'fa-cogs' };
const categoryColors = { '规划': 'text-blue-500', '建设': 'text-orange-500', '优化': 'text-green-500', '维护': 'text-purple-500', '客服': 'text-red-500' };
const toolTypeColors = {
    excel: 'bg-green-100 text-green-800',
    python: 'bg-blue-100 text-blue-800',
    bash: 'bg-gray-200 text-gray-800',
    bat: 'bg-yellow-100 text-yellow-800'
};

// 显示提示消息
function showToast(msg, type = 'success') {
    const t = document.createElement('div');
    t.className = `toast toast-${type}`;
    t.textContent = msg;
    document.body.appendChild(t);
    setTimeout(() => t.remove(), 3000);
}

// 检查用户角色
function hasRole(role) {
    if (!currentUser || !currentUser.role) return false;
    if (role === 'admin') return currentUser.role === 'admin';
    if (role === 'author') return ['author', 'admin'].includes(currentUser.role);
    return true;
}

// 创建工具卡片 HTML
function createToolCard(tool) {
    const colors = toolTypeColors[tool.type] || 'bg-gray-100 text-gray-800';
    const desc = (tool.description || '').substring(0, 40);
    return `
    <div class="bg-white rounded-lg shadow-md p-4 flex flex-col hover:shadow-xl transition-shadow cursor-pointer" onclick="switchPage('detail', ${tool.id})">
        <div class="flex justify-between items-start">
            <span class="px-2 py-1 text-xs rounded font-semibold ${colors}">${tool.type}</span>
            <div class="flex items-center gap-1 text-sm text-gray-500">
                <i class="fas fa-download"></i><span>${tool.downloads + (tool.calls||0)}</span>
            </div>
        </div>
        <h3 class="font-bold mt-3 text-lg">${tool.name}</h3>
        <p class="text-sm text-gray-500 mt-1 flex-grow">${desc}...</p>
        <div class="text-xs text-gray-400 mt-4 pt-2 border-t border-gray-100">由 ${tool.author_name} 提供</div>
    </div>`;
}

// 返回上一页
function goBack() {
    if (pageHistory.length > 0) {
        const prev = pageHistory.pop();
        currentPage = prev.pageId;
        currentParam = prev.param;
        switchPage(prev.pageId, prev.param, true);
    } else {
        switchPage('home', null, true);
    }
}
