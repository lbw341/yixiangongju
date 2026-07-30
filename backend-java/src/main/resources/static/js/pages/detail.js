// ==================== 详情页模块 ====================

async function renderDetailPage(toolId) {
    try {
        const tool = await api(`/api/tools/${toolId}`);
        const colors = toolTypeColors[tool.type] || 'bg-gray-100 text-gray-800';
        const keywords = (tool.keywords || '').split(',').filter(k => k.trim()).map(k =>
            `<span class="bg-gray-200 text-sm px-2 py-1 rounded">${k.trim()}</span>`
        ).join('');
        const reviews = (tool.reviews || []).map(r => `
            <div class="border-b border-gray-100 pb-2">
                <p class="font-semibold">${r.username}</p>
                <p class="text-sm text-gray-600">"${r.content}"</p>
                <p class="text-xs text-gray-400 mt-1">${r.created_at || ''}</p>
            </div>`).join('');
        const instructions = tool.instructions || '暂无使用说明。';

        return `
        <div class="page">
            <button onclick="goBack()" class="mb-4 inline-flex items-center gap-2 text-gray-600 hover:text-indigo-600 transition">
                <i class="fas fa-arrow-left"></i> 返回
            </button>
            <div class="flex flex-col md:flex-row justify-between items-start mb-8">
                <div>
                    <h1 class="text-4xl font-bold">${tool.name}</h1>
                    <div class="flex items-center gap-2 mt-2"><span class="px-2 py-1 text-xs rounded font-semibold ${colors}">${tool.type}</span>${keywords}</div>
                    <p class="mt-4 text-gray-500">由 <strong>${tool.author_name}</strong> (${tool.department || ''}) 提供</p>
                </div>
                <div class="flex-shrink-0 mt-4 md:mt-0">
                    <div class="text-right">
                        <p class="text-gray-500">累计下载/调用</p>
                        <p class="text-4xl font-bold text-indigo-500">${tool.downloads + (tool.calls || 0)}</p>
                    </div>
                </div>
            </div>
            <div class="grid grid-cols-1 lg:grid-cols-3 gap-8">
                <div class="lg:col-span-2 space-y-8">
                    <div class="bg-white rounded-lg shadow-md p-6">
                        <h2 class="text-2xl font-bold mb-4 flex items-center gap-2"><i class="fas fa-tools"></i>工具使用</h2>
                        <div class="mb-6">
                            <label class="block text-sm font-medium text-gray-700 mb-2">输入工作内容</label>
                            <textarea id="work-content-input" class="w-full border border-gray-300 rounded-lg p-4 resize-none" rows="8" placeholder="请输入本周工作内容，每行一项...&#10;&#10;例如：&#10;- 完成项目需求分析&#10;- 编写技术文档&#10;- 修复线上bug"></textarea>
                            <button onclick="submitWorkContent(${tool.id})" class="mt-3 w-full bg-indigo-500 text-white font-bold py-3 px-4 rounded-lg hover:bg-indigo-600 transition flex items-center justify-center gap-2">
                                <i class="fas fa-magic"></i> 生成周报
                            </button>
                        </div>
                        <div class="border-t border-gray-200 pt-4">
                            <p class="text-sm text-gray-500 mb-3">或使用文件方式：</p>
                            <div class="border rounded-lg p-4 grid grid-cols-1 md:grid-cols-2 gap-4 items-center">
                                <button onclick="downloadTemplate(${tool.id})" class="w-full bg-indigo-500 text-white font-bold py-3 px-4 rounded-lg hover:bg-indigo-600 transition flex items-center justify-center gap-2">
                                    <i class="fas fa-download"></i> 下载模板
                                </button>
                                <label class="w-full bg-white border border-gray-300 font-bold py-3 px-4 rounded-lg hover:bg-gray-50 transition flex items-center justify-center gap-2 cursor-pointer">
                                    <i class="fas fa-upload"></i> 上传文件（支持压缩包）
                                    <input type="file" class="sr-only" onchange="uploadFile(${tool.id}, this)" accept=".xlsx,.csv,.json,.zip,.py,.sh,.bat,.txt,.xls,.md,.log,.ps1" multiple>
                                </label>
                            </div>
                            <div class="mt-2 text-center">
                                <label class="text-sm text-gray-500 hover:text-indigo-600 cursor-pointer">
                                    <i class="fas fa-folder-open"></i> 或选择文件夹上传
                                    <input type="file" class="sr-only" onchange="uploadFile(${tool.id}, this)" webkitdirectory multiple>
                                </label>
                            </div>
                        </div>
                        <div id="upload-progress" class="mt-4 hidden">
                            <p class="text-sm font-medium mb-1">处理进度</p>
                            <div class="w-full bg-gray-200 rounded-full h-2.5">
                                <div id="progress-bar" class="bg-green-500 h-2.5 rounded-full progress-bar" style="width: 0%"></div>
                            </div>
                            <p id="progress-text" class="text-xs text-gray-500 mt-1">文件处理中...</p>
                        </div>
                        <div id="upload-result" class="mt-6 border-t border-gray-100 pt-4 hidden">
                            <p class="font-semibold mb-3">结果产出</p>
                            <div class="flex gap-3 mb-4">
                                <button id="preview-btn" onclick="previewResultFile()" class="bg-gray-100 text-gray-700 font-semibold py-2 px-4 rounded-lg hover:bg-gray-200 transition flex items-center gap-2">
                                    <i class="fas fa-eye"></i> 预览结果
                                </button>
                                <button id="download-btn" onclick="downloadResultFile(currentResultFile)" class="bg-indigo-500 text-white font-semibold py-2 px-4 rounded-lg hover:bg-indigo-600 transition flex items-center gap-2">
                                    <i class="fas fa-download"></i> 下载文件
                                </button>
                            </div>
                            <div id="result-name-display" class="text-sm text-gray-500">结果文件: <span id="result-name"></span></div>
                            <div id="python-output-section" class="mt-4 hidden">
                                <div class="bg-gray-50 rounded-lg border border-gray-200">
                                    <div class="bg-gray-100 px-4 py-2 border-b border-gray-200 flex justify-between items-center">
                                        <span class="text-sm font-medium text-gray-700">🐍 Python 执行结果</span>
                                        <button onclick="document.getElementById('python-output-section').style.display='none'" class="text-gray-500 hover:text-gray-700"><i class="fas fa-times"></i></button>
                                    </div>
                                    <div class="p-4">
                                        <pre id="python-output" class="whitespace-pre-wrap font-mono text-sm text-gray-800 max-h-96 overflow-y-auto"></pre>
                                    </div>
                                </div>
                            </div>
                            <div id="preview-container" class="mt-4 hidden">
                                <div class="bg-gray-50 rounded-lg border border-gray-200">
                                    <div class="bg-gray-100 px-4 py-2 border-b border-gray-200 flex justify-between items-center">
                                        <span class="text-sm font-medium text-gray-700">📄 结果预览</span>
                                        <button onclick="document.getElementById('preview-container').classList.add('hidden')" class="text-gray-500 hover:text-gray-700"><i class="fas fa-times"></i></button>
                                    </div>
                                    <div class="p-4">
                                        <pre id="preview-content" class="whitespace-pre-wrap font-mono text-sm text-gray-800 max-h-96 overflow-y-auto"></pre>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div class="bg-white rounded-lg shadow-md p-6">
                        <h2 class="text-2xl font-bold mb-4 flex items-center gap-2"><i class="fas fa-info-circle"></i>功能介绍与使用说明</h2>
                        <div class="prose max-w-none">
                            <p>${tool.description || ''}</p>
                            <div class="mt-4 whitespace-pre-wrap text-gray-600">${instructions}</div>
                        </div>
                    </div>
                </div>
                <div class="space-y-8">
                    <div class="bg-white rounded-lg shadow-md p-6">
                        <h3 class="text-lg font-bold mb-4 flex items-center gap-2"><i class="fas fa-chart-line"></i>下载趋势</h3>
                        <canvas id="downloadsChart"></canvas>
                    </div>
                    <div class="bg-white rounded-lg shadow-md p-6">
                        <h3 class="text-lg font-bold mb-4 flex items-center gap-2"><i class="fas fa-envelope"></i>联系方式</h3>
                        <div class="space-y-2 text-sm">
                            <p><strong>邮箱:</strong> <a href="mailto:${tool.contact_email || 'author@example.com'}" class="text-indigo-500">${tool.contact_email || 'author@example.com'}</a></p>
                            <p><strong>手机号:</strong> ${tool.contact_phone || '未提供'}</p>
                        </div>
                    </div>
                    <div class="bg-white rounded-lg shadow-md p-6">
                        <h3 class="text-lg font-bold mb-4 flex items-center gap-2"><i class="fas fa-comments"></i>用户评价 (${(tool.reviews || []).length})</h3>
                        <div class="space-y-4">
                            ${reviews || '<p class="text-gray-400 text-sm">暂无评价</p>'}
                            <textarea id="review-input" class="w-full mt-4 bg-gray-100 border-none rounded-lg p-2" rows="2" placeholder="写下您的评价..."></textarea>
                            <button onclick="submitReview(${tool.id})" class="w-full mt-2 bg-indigo-500 text-white font-semibold py-2 rounded-lg hover:bg-indigo-600 flex items-center justify-center gap-2"><i class="fas fa-paper-plane"></i>提交评价</button>
                        </div>
                    </div>
                </div>
            </div>
        </div>`;
    } catch(e) {
        return `<div class="page"><p class="text-red-500">加载失败: ${e.message}</p></div>`;
    }
}

// 提交评价
async function submitReview(toolId) {
    if (!authToken) return showToast('请先登录', 'error');
    const content = document.getElementById('review-input')?.value?.trim();
    if (!content) return showToast('请输入评价内容', 'error');
    try {
        await api('/api/reviews', { method: 'POST', json: { tool_id: toolId, content } });
        showToast('评价提交成功');
        switchPage('detail', toolId);
    } catch(e) { showToast(e.message, 'error'); }
}

// 下载模板
async function downloadTemplate(toolId) {
    if (!authToken) return showToast('请先登录', 'error');
    try {
        const res = await fetch(`${API_BASE}/api/tools/${toolId}/download_template`, {
            headers: { 'Authorization': `Bearer ${authToken}` }
        });
        if (!res.ok) { const d = await res.json(); throw new Error(d.error); }
        const blob = await res.blob();
        const disposition = res.headers.get('Content-Disposition');
        let filename = 'template';
        if (disposition) { const m = disposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/); if (m) filename = m[1].replace(/['"]/g, ''); }
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = filename;
        a.click();
        URL.revokeObjectURL(a.href);
        showToast('模板下载成功');
    } catch(e) { showToast(e.message, 'error'); }
}

// 上传文件到工具
async function uploadFile(toolId, input) {
    if (!authToken) return showToast('请先登录', 'error');
    const files = Array.from(input.files);
    if (!files || files.length === 0) return;
    const progress = document.getElementById('upload-progress');
    const bar = document.getElementById('progress-bar');
    const text = document.getElementById('progress-text');
    const result = document.getElementById('upload-result');
    progress.classList.remove('hidden');
    result.classList.add('hidden');
    bar.style.width = '20%';
    bar.className = 'bg-green-500 h-2.5 rounded-full progress-bar';
    text.textContent = `准备上传 ${files.length} 个文件...`;
    try {
        const fd = new FormData();
        files.forEach(file => {
            fd.append('file', file);
        });
        bar.style.width = '50%';
        text.textContent = '上传中...';
        const data = await api(`/api/tools/${toolId}/upload`, { method: 'POST', body: fd });
        bar.style.width = '100%';
        text.textContent = '处理完成！';
        currentResultFile = data.result_file;
        result.classList.remove('hidden');
        document.getElementById('result-name').textContent = data.result_file;
        
        if (data.processed_files && data.processed_files.length > 0) {
            const filesHtml = data.processed_files.map(f => `<span class="inline-block bg-gray-100 text-gray-700 text-xs px-2 py-1 rounded mr-1 mb-1">${f}</span>`).join('');
            document.getElementById('processed-files-list')?.remove();
            const filesDiv = document.createElement('div');
            filesDiv.id = 'processed-files-list';
            filesDiv.innerHTML = `<p class="text-sm text-gray-500 mt-2">处理的文件:</p><div class="flex flex-wrap mt-1">${filesHtml}</div>`;
            document.getElementById('result-name-display').parentNode.appendChild(filesDiv);
        }
        
        if (data.output) {
            document.getElementById('python-output').textContent = data.output;
            document.getElementById('python-output-section').style.display = 'block';
        } else {
            document.getElementById('python-output-section').style.display = 'none';
        }
        showToast(data.message || '文件处理完成');
    } catch(e) {
        bar.style.width = '100%';
        bar.className = 'bg-red-500 h-2.5 rounded-full progress-bar';
        text.textContent = '处理失败: ' + e.message;
        showToast(e.message, 'error');
    }
    input.value = '';
}

// 提交工作内容生成周报
async function submitWorkContent(toolId) {
    if (!authToken) return showToast('请先登录', 'error');
    const content = document.getElementById('work-content-input')?.value?.trim();
    if (!content) return showToast('请输入工作内容', 'error');
    
    const progress = document.getElementById('upload-progress');
    const bar = document.getElementById('progress-bar');
    const text = document.getElementById('progress-text');
    const result = document.getElementById('upload-result');
    const previewContainer = document.getElementById('preview-container');
    
    progress.classList.remove('hidden');
    result.classList.add('hidden');
    previewContainer.classList.add('hidden');
    bar.style.width = '30%';
    bar.className = 'bg-green-500 h-2.5 rounded-full progress-bar';
    text.textContent = '处理中...';
    
    try {
        const fd = new FormData();
        fd.append('file', new Blob([content], { type: 'text/plain' }), 'work_content.txt');
        bar.style.width = '70%';
        text.textContent = '生成周报中...';
        
        const data = await api(`/api/tools/${toolId}/upload`, { method: 'POST', body: fd });
        bar.style.width = '100%';
        text.textContent = '生成完成！';
        
        currentResultFile = data.result_file;
        result.classList.remove('hidden');
        document.getElementById('result-name').textContent = data.result_file;
        showToast('周报生成完成');
    } catch(e) {
        bar.style.width = '100%';
        bar.className = 'bg-red-500 h-2.5 rounded-full progress-bar';
        text.textContent = '处理失败: ' + e.message;
        showToast(e.message, 'error');
    }
}

// 预览结果文件
async function previewResultFile() {
    if (!currentResultFile) return showToast('请先生成周报', 'error');
    if (!authToken) return showToast('请先登录', 'error');
    
    try {
        const data = await api(`/api/files/preview/${currentResultFile}`);
        const previewContent = document.getElementById('preview-content');
        const previewContainer = document.getElementById('preview-container');
        
        previewContent.textContent = data.content;
        previewContainer.classList.remove('hidden');
        previewContainer.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    } catch(e) {
        showToast(e.message, 'error');
    }
}

// 下载结果文件
async function downloadResultFile(filename) {
    try {
        const res = await fetch(`${API_BASE}/api/files/download/${filename}`, {
            headers: { 'Authorization': `Bearer ${authToken}` }
        });
        if (!res.ok) throw new Error('下载失败');
        const blob = await res.blob();
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = filename;
        a.click();
        URL.revokeObjectURL(a.href);
    } catch(e) { showToast(e.message, 'error'); }
}

// 初始化详情页图表
async function initDetailChart(toolId) {
    try {
        const data = await api(`/api/stats/tool/${toolId}`);
        const ctx = document.getElementById('downloadsChart');
        if (!ctx) return;
        if (downloadsChartInstance) downloadsChartInstance.destroy();
        const trend = data.trend || [];
        downloadsChartInstance = new Chart(ctx.getContext('2d'), {
            type: 'line',
            data: {
                labels: trend.map(t => t.date.substring(5)),
                datasets: [{
                    label: '下载/调用次数',
                    data: trend.map(t => t.count),
                    fill: true, backgroundColor: 'rgba(79,70,229,0.1)', borderColor: 'rgb(79,70,229)', tension: 0.3
                }]
            },
            options: { responsive: true, plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true } } }
        });
    } catch(e) {}
}
