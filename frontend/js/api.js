// ==================== API 请求模块 ====================

const API_BASE = window.API_BASE || '';
let authToken = localStorage.getItem('token') || null;
let currentUser = JSON.parse(localStorage.getItem('user') || 'null');
let downloadsChartInstance = null;
let currentResultFile = '';
let currentPage = 'home';
let currentParam = null;
let pageHistory = [];

// 通用 API 请求函数
async function api(path, options = {}) {
    const headers = options.headers || {};
    if (authToken) headers['Authorization'] = `Bearer ${authToken}`;
    if (options.json) {
        headers['Content-Type'] = 'application/json';
        options.body = JSON.stringify(options.json);
    }
    delete options.json;
    options.headers = headers;
    const res = await fetch(`${API_BASE}${path}`, options);
    const data = await res.json();
    if (!res.ok) {
        if (res.status === 401) { logout(); }
        throw new Error(data.error || '请求失败');
    }
    return data;
}

// 下载文件
async function downloadFile(url, filename) {
    const res = await fetch(url, {
        headers: { 'Authorization': `Bearer ${authToken}` }
    });
    if (!res.ok) throw new Error('下载失败');
    const blob = await res.blob();
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = filename;
    a.click();
    URL.revokeObjectURL(a.href);
}
