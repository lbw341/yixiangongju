// ==================== 应用主入口 ====================

// 导航链接
const navLinks = document.querySelectorAll('.nav-link');

// 切换页面
async function switchPage(pageId, param, fromBack) {
    if (pageId === 'manage' && !hasRole('author')) {
        showToast('只有作者和管理员可以访问工具管理', 'error');
        return;
    }
    if (pageId === 'uploadTool' && !hasRole('author')) {
        showToast('只有作者和管理员可以上传工具', 'error');
        return;
    }
    if (!fromBack) {
        pageHistory.push({ pageId: currentPage, param: currentParam });
    }
    currentPage = pageId;
    currentParam = param;
    navLinks.forEach(link => {
        const linkPage = link.getAttribute('href')?.substring(1);
        if (pageId === 'category' && ['planning','construction','maintenance','optimization','operation'].includes(linkPage)) {
            link.classList.toggle('active', link.innerText.trim() === param);
        } else if (['search','detail','uploadTool','profile','manage','messages'].includes(pageId)) {
            link.classList.remove('active');
        } else {
            link.classList.toggle('active', linkPage === pageId);
        }
    });
    const container = document.getElementById('page-container');
    container.innerHTML = '<div class="flex justify-center items-center py-20"><div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600"></div></div>';

    let html = '';
    switch(pageId) {
        case 'home': html = await renderHomePage(); break;
        case 'category': html = await renderCategoryPage(param); break;
        case 'search': html = await renderSearchPage(param); break;
        case 'detail': html = await renderDetailPage(param); break;
        case 'profile': html = await renderProfilePage(); break;
        case 'manage': html = await renderManagePage(); break;
        case 'uploadTool': html = renderUploadToolPage(); break;
        case 'messages': html = await renderMessagesPage(); break;
        default: html = await renderHomePage();
    }
    container.innerHTML = html;
    if (pageId === 'detail') setTimeout(() => initDetailChart(param), 100);
    loadUnreadCount();
}

// 执行搜索
function performSearch() {
    const query = document.getElementById('searchInput').value.trim();
    if (query) switchPage('search', query);
    else switchPage('home');
}

// 初始化事件监听
document.getElementById('searchInput').addEventListener('keypress', function(e) {
    if (e.key === 'Enter') performSearch();
});

document.getElementById('user-menu-button').addEventListener('click', function() {
    const menu = document.getElementById('user-menu');
    menu.classList.toggle('hidden');
});

document.addEventListener('click', function(e) {
    if (!e.target.closest('#user-menu-button')) {
        document.getElementById('user-menu').classList.add('hidden');
    }
});

// 启动应用
if (authToken) {
    initApp();
}
