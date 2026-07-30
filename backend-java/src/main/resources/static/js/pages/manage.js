// ==================== 工具管理模块 ====================

async function renderManagePage() {
    try {
        const data = await api('/api/tools/my');
        const rows = (data.tools || []).map(tool => `
            <tr>
                <td class="p-4">${tool.name}</td>
                <td class="p-4">${tool.type}</td>
                <td class="p-4">${tool.downloads + (tool.calls||0)}</td>
                <td class="p-4"><span class="px-2 py-1 text-xs rounded-full ${tool.status === 'online' ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}">${tool.status === 'online' ? '在线' : '已下线'}</span></td>
                <td class="p-4 space-x-2">
                    <button onclick='editTool(${JSON.stringify(tool).replace(/'/g,"\\'")})' class="text-indigo-500 hover:underline inline-flex items-center gap-1"><i class="fas fa-edit"></i>修改</button>
                    ${tool.status === 'online' ? `<button onclick="offlineTool(${tool.id})" class="text-red-500 hover:underline inline-flex items-center gap-1"><i class="fas fa-arrow-down"></i>下线</button>` : `<button onclick="onlineTool(${tool.id})" class="text-green-500 hover:underline inline-flex items-center gap-1"><i class="fas fa-arrow-up"></i>上线</button>`}
                    <button onclick="switchPage('detail', ${tool.id})" class="text-gray-500 hover:underline inline-flex items-center gap-1"><i class="fas fa-chart-pie"></i>统计</button>
                </td>
            </tr>`).join('');
        return `
        <div class="page">
            <div class="flex justify-between items-center mb-6">
                <h1 class="text-3xl font-bold">工具管理</h1>
                ${hasRole('author') ? `<button class="bg-indigo-500 text-white font-bold py-2 px-4 rounded-lg hover:bg-indigo-600 transition flex items-center gap-2" onclick="switchPage('uploadTool')">
                    <i class="fas fa-plus"></i> 上传新工具
                </button>` : ''}
            </div>
            <div class="bg-white rounded-lg shadow-md overflow-hidden">
                <table class="w-full text-left">
                    <thead class="bg-gray-50"><tr><th class="p-4 font-semibold">工具名称</th><th class="p-4 font-semibold">类型</th><th class="p-4 font-semibold">下载量</th><th class="p-4 font-semibold">状态</th><th class="p-4 font-semibold">操作</th></tr></thead>
                    <tbody class="divide-y divide-gray-200">${rows || '<tr><td colspan="5" class="p-4 text-center text-gray-400">暂无工具</td></tr>'}</tbody>
                </table>
            </div>
        </div>`;
    } catch(e) { return `<div class="page"><p class="text-red-500">${e.message}</p></div>`; }
}

// 上传新工具页面
function renderUploadToolPage() {
    return `
    <div class="page">
        <h1 class="text-3xl font-bold mb-6">上传新工具</h1>
        <div class="bg-white rounded-lg shadow-md p-8 max-w-2xl mx-auto">
            <form class="space-y-6" onsubmit="submitNewTool(event)">
                <div>
                    <label class="block text-sm font-medium text-gray-700">工具名称</label>
                    <input type="text" id="new-tool-name" required placeholder="例如：项目周报自动生成器" class="mt-1 block w-full rounded-md bg-gray-100 border-transparent focus:border-indigo-500 focus:bg-white focus:ring-0">
                </div>
                <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                    <div>
                        <label class="block text-sm font-medium text-gray-700">工具分类</label>
                        <select id="new-tool-category" class="mt-1 block w-full rounded-md bg-gray-100 border-transparent focus:border-indigo-500 focus:bg-white focus:ring-0">
                            <option>规划</option><option>建设</option><option>优化</option><option>维护</option><option>客服</option>
                        </select>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700">工具类型</label>
                        <select id="new-tool-type" class="mt-1 block w-full rounded-md bg-gray-100 border-transparent focus:border-indigo-500 focus:bg-white focus:ring-0">
                            <option value="excel">Excel (VBA)</option><option value="python">Python</option><option value="bat">Bat</option><option value="bash">Bash</option>
                        </select>
                    </div>
                </div>
                <div>
                    <label class="block text-sm font-medium text-gray-700">关键字</label>
                    <input type="text" id="new-tool-keywords" placeholder="多个关键字用逗号分隔" class="mt-1 block w-full rounded-md bg-gray-100 border-transparent focus:border-indigo-500 focus:bg-white focus:ring-0">
                </div>
                <div>
                    <label class="block text-sm font-medium text-gray-700">所属部门</label>
                    <input type="text" id="new-tool-dept" placeholder="例如：研发部" class="mt-1 block w-full rounded-md bg-gray-100 border-transparent focus:border-indigo-500 focus:bg-white focus:ring-0">
                </div>
                <div>
                    <label class="block text-sm font-medium text-gray-700">功能介绍</label>
                    <textarea id="new-tool-desc" class="mt-1 block w-full rounded-md bg-gray-100 border-transparent focus:border-indigo-500 focus:bg-white focus:ring-0" rows="4" placeholder="详细描述工具的功能、使用方法等。"></textarea>
                </div>
                <div>
                    <label class="block text-sm font-medium text-gray-700">使用说明</label>
                    <textarea id="new-tool-instructions" class="mt-1 block w-full rounded-md bg-gray-100 border-transparent focus:border-indigo-500 focus:bg-white focus:ring-0" rows="3" placeholder="图文/视频使用说明"></textarea>
                </div>
                <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                    <div><label class="block text-sm font-medium text-gray-700">联系邮箱</label>
                        <input type="email" id="new-tool-email" class="mt-1 block w-full rounded-md bg-gray-100 border-transparent focus:border-indigo-500 focus:bg-white focus:ring-0"></div>
                    <div><label class="block text-sm font-medium text-gray-700">联系电话</label>
                        <input type="tel" id="new-tool-phone" class="mt-1 block w-full rounded-md bg-gray-100 border-transparent focus:border-indigo-500 focus:bg-white focus:ring-0"></div>
                </div>
                <div>
                    <label class="block text-sm font-medium text-gray-700">上传模板文件</label>
                    <div class="mt-1 flex justify-center px-6 pt-5 pb-6 border-2 border-gray-300 border-dashed rounded-md">
                        <div class="space-y-1 text-center">
                            <i class="fas fa-cloud-upload-alt text-4xl text-gray-400"></i>
                            <div class="flex text-sm text-gray-600">
                                <label class="relative cursor-pointer bg-white rounded-md font-medium text-indigo-600 hover:text-indigo-500">
                                    <span id="file-label">选择一个文件</span>
                                    <input id="new-tool-file" type="file" class="sr-only" onchange="document.getElementById('file-label').textContent=this.files[0]?.name||'选择一个文件'" accept=".xlsx,.csv,.json,.zip,.py,.sh,.bat,.txt,.xls">
                                </label>
                                <p class="pl-1">或拖拽到这里</p>
                            </div>
                            <p class="text-xs text-gray-500">支持 .py, .sh, .xlsx, .zip 等</p>
                        </div>
                    </div>
                </div>
                <div class="text-right">
                    <button type="submit" class="bg-indigo-600 text-white font-semibold py-2 px-6 rounded-lg hover:bg-indigo-700 inline-flex items-center gap-2"><i class="fas fa-check-circle"></i>提交审核</button>
                </div>
            </form>
        </div>
    </div>`;
}

// 提交新工具
async function submitNewTool(e) {
    e.preventDefault();
    const fd = new FormData();
    fd.append('name', document.getElementById('new-tool-name').value);
    fd.append('category', document.getElementById('new-tool-category').value);
    fd.append('type', document.getElementById('new-tool-type').value);
    fd.append('keywords', document.getElementById('new-tool-keywords').value);
    fd.append('department', document.getElementById('new-tool-dept').value);
    fd.append('description', document.getElementById('new-tool-desc').value);
    fd.append('instructions', document.getElementById('new-tool-instructions').value);
    fd.append('contact_email', document.getElementById('new-tool-email').value);
    fd.append('contact_phone', document.getElementById('new-tool-phone').value);
    const file = document.getElementById('new-tool-file').files[0];
    if (file) fd.append('template_file', file);
    try {
        await api('/api/tools/create', { method: 'POST', body: fd });
        showToast('工具创建成功');
        switchPage('manage');
    } catch(err) { showToast(err.message, 'error'); }
}

// 下线工具
async function offlineTool(toolId) {
    if (!confirm('确认下线此工具？')) return;
    try {
        await api(`/api/tools/${toolId}/offline`, { method: 'POST' });
        showToast('工具已下线');
        switchPage('manage');
    } catch(e) { showToast(e.message, 'error'); }
}

// 上线工具
async function onlineTool(toolId) {
    if (!confirm('确认上线此工具？')) return;
    try {
        await api(`/api/tools/${toolId}/online`, { method: 'POST' });
        showToast('工具已上线');
        switchPage('manage');
    } catch(e) { showToast(e.message, 'error'); }
}

// 编辑工具
function editTool(tool) {
    const modal = document.createElement('div');
    modal.className = 'fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50';
    modal.onclick = (e) => { if (e.target === modal) modal.remove(); };
    modal.innerHTML = `<div class="bg-white rounded-lg p-6 max-w-lg w-full mx-4 max-h-[80vh] overflow-y-auto">
        <h2 class="text-xl font-bold mb-4">修改工具</h2>
        <form onsubmit="submitEditTool(event, ${tool.id})" class="space-y-3">
            <input id="ed-name" value="${tool.name}" class="w-full border rounded-lg px-3 py-2" placeholder="工具名称">
            <select id="ed-category" class="w-full border rounded-lg px-3 py-2">
                ${['规划','建设','优化','维护','客服'].map(c => `<option ${tool.category===c?'selected':''}>${c}</option>`).join('')}
            </select>
            <input id="ed-keywords" value="${tool.keywords||''}" class="w-full border rounded-lg px-3 py-2" placeholder="关键字">
            <input id="ed-dept" value="${tool.department||''}" class="w-full border rounded-lg px-3 py-2" placeholder="部门">
            <textarea id="ed-desc" class="w-full border rounded-lg px-3 py-2" rows="3" placeholder="描述">${tool.description||''}</textarea>
            <input id="ed-email" value="${tool.contact_email||''}" class="w-full border rounded-lg px-3 py-2" placeholder="联系邮箱">
            <input id="ed-phone" value="${tool.contact_phone||''}" class="w-full border rounded-lg px-3 py-2" placeholder="联系电话">
            <div class="flex gap-2">
                <button type="submit" class="bg-indigo-500 text-white px-4 py-2 rounded-lg">保存</button>
                <button type="button" onclick="this.closest('.fixed').remove()" class="bg-gray-200 px-4 py-2 rounded-lg">取消</button>
            </div>
        </form>
    </div>`;
    document.body.appendChild(modal);
}

// 提交编辑工具
async function submitEditTool(e, toolId) {
    e.preventDefault();
    try {
        await api(`/api/tools/${toolId}/update`, { method: 'PUT', json: {
            name: document.getElementById('ed-name').value,
            category: document.getElementById('ed-category').value,
            keywords: document.getElementById('ed-keywords').value,
            department: document.getElementById('ed-dept').value,
            description: document.getElementById('ed-desc').value,
            contact_email: document.getElementById('ed-email').value,
            contact_phone: document.getElementById('ed-phone').value
        }});
        showToast('修改成功');
        document.querySelector('.fixed')?.remove();
        switchPage('manage');
    } catch(e) { showToast(e.message, 'error'); }
}
