// ==================== 首页模块 ====================

async function renderHomePage() {
    try {
        const [dashData, usageData] = await Promise.all([
            api('/api/stats/dashboard'),
            api('/api/stats/recent_usage').catch(() => ({ tools: [] }))
        ]);
        const cats = ['规划', '建设', '优化', '维护', '客服'];
        const catCards = cats.map(name => {
            const s = dashData.categories[name] || { count: 0, downloads: 0, calls: 0 };
            return `<div class="stat-card bg-white p-6 rounded-lg shadow-md transition-all">
                <div class="w-10 h-10 flex items-center justify-center rounded-full bg-gray-100 mb-3">
                    <i class="fas ${categoryIcons[name]} text-xl ${categoryColors[name]}"></i>
                </div>
                <h3 class="text-gray-500 text-sm font-medium">${name}类工具总数</h3>
                <p class="text-3xl font-bold mt-2">${s.count}</p>
                <div class="mt-4 space-y-2">
                    <div class="flex justify-between items-center">
                        <span class="text-xs text-gray-500">今日下载/调用</span>
                        <span class="text-sm font-medium">${s.downloads + s.calls}</span>
                    </div>
                    <div class="flex justify-between items-center">
                        <span class="text-xs text-gray-500">累计下载/调用</span>
                        <span class="text-sm font-medium">${s.downloads + s.calls}</span>
                    </div>
                </div>
            </div>`;
        }).join('');

        const recentTools = (usageData.tools || []).slice(0, 4);
        const recentCards = recentTools.length > 0
            ? recentTools.map(createToolCard).join('')
            : '<p class="col-span-full text-gray-400">暂无使用记录</p>';

        const hotList = (dashData.hot_tools || []).slice(0, 5).map((t, i) => `
            <div class="flex items-center justify-between p-2 rounded hover:bg-gray-50 cursor-pointer" onclick="switchPage('detail', ${t.id})">
                <div class="flex items-center gap-4">
                    <span class="font-bold text-lg w-6 text-center ${i < 3 ? 'text-indigo-500' : 'text-gray-400'}">${i + 1}</span>
                    <div><p class="font-semibold">${t.name}</p><p class="text-xs text-gray-500">${t.author_name}</p></div>
                </div>
                <div class="flex items-center gap-1 text-sm text-gray-600"><i class="fas fa-download"></i><span>${t.downloads}</span></div>
            </div>`).join('');

        const authorList = (dashData.author_stats || []).slice(0, 5).map((a, i) => `
            <div class="flex items-center justify-between p-2 rounded hover:bg-gray-50">
                <div class="flex items-center gap-4">
                    <span class="font-bold text-lg w-6 text-center ${i < 3 ? 'text-indigo-500' : 'text-gray-400'}">${i + 1}</span>
                    <div class="flex items-center gap-3">
                        <div class="w-8 h-8 rounded-full bg-indigo-100 text-indigo-600 flex items-center justify-center font-bold text-sm">${(a.author_name||'?').charAt(0)}</div>
                        <p class="font-semibold">${a.author_name}</p>
                    </div>
                </div>
                <div class="text-sm text-gray-600">贡献 <strong>${a.tool_count}</strong> 个工具</div>
            </div>`).join('');

        return `
        <div class="page pt-6">
            <h1 class="text-3xl font-bold mb-2">欢迎回来！${currentUser ? currentUser.nickname || currentUser.username : ''}</h1>
            <p class="text-gray-500 mb-8">开始新的一天，让高效工具助您一臂之力。</p>
            <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 xl:grid-cols-5 gap-6 mb-8">${catCards}</div>
            <h2 class="text-2xl font-bold mb-4">最近使用</h2>
            <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">${recentCards}</div>
            <div class="grid grid-cols-1 lg:grid-cols-2 gap-8">
                <div>
                    <h2 class="text-2xl font-bold mb-4">工具热度榜</h2>
                    <div class="bg-white rounded-lg shadow-md p-4 space-y-3">${hotList || '<p class="text-gray-400">暂无数据</p>'}</div>
                </div>
                <div>
                    <h2 class="text-2xl font-bold mb-4">作者贡献榜</h2>
                    <div class="bg-white rounded-lg shadow-md p-4 space-y-3">${authorList || '<p class="text-gray-400">暂无数据</p>'}</div>
                </div>
            </div>
        </div>`;
    } catch(e) {
        return `<div class="page"><p class="text-red-500">加载失败: ${e.message}</p></div>`;
    }
}
