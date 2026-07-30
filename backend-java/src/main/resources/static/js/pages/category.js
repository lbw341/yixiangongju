// ==================== 分类页模块 ====================

async function renderCategoryPage(category) {
    try {
        const data = await api(`/api/tools?category=${encodeURIComponent(category)}`);
        const cards = data.tools.length > 0
            ? data.tools.map(createToolCard).join('')
            : '<p class="col-span-full text-center text-gray-500">该分类下暂无工具。</p>';
        return `<div class="page"><h1 class="text-3xl font-bold mb-6">${category}类工具</h1>
            <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">${cards}</div></div>`;
    } catch(e) {
        return `<div class="page"><p class="text-red-500">加载失败: ${e.message}</p></div>`;
    }
}

async function renderSearchPage(query) {
    try {
        const data = await api(`/api/tools?search=${encodeURIComponent(query)}`);
        const cards = data.tools.length > 0
            ? data.tools.map(createToolCard).join('')
            : '<p class="col-span-full text-center text-gray-500">未找到与您的搜索相关的工具。</p>';
        return `<div class="page"><h1 class="text-3xl font-bold mb-6">搜索结果: <span class="text-indigo-500">${query}</span></h1>
            <p class="mb-8 text-gray-600">共找到 ${data.tools.length} 个相关工具。</p>
            <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">${cards}</div></div>`;
    } catch(e) {
        return `<div class="page"><p class="text-red-500">搜索失败: ${e.message}</p></div>`;
    }
}
