# 一线工具平台

专业的项目周报生成器和技术文档生成工具平台

## 功能

- 📝 项目周报自动生成
- 📊 Excel 文件读取和处理
- 🐍 Python 代码在线执行
- 📦 压缩包批量文件处理
- 👥 用户权限管理（管理员/作者/普通用户）
- 💬 消息反馈与评价系统

## 技术栈

- **后端**: Java 21 + Spring Boot 3.2.5 + Spring Data JPA + MySQL
- **前端**: Vue 3 + Vite + Pinia + Vue Router + Tailwind CSS + Chart.js

## 快速开始

### 方式一：一键启动（推荐）

双击 `start.bat` 即可

### 方式二：手动启动

```bash
# 1. 安装前端依赖并构建
cd frontend
npm install
npm run build

# 2. 启动后端
cd ../backend-java
mvn spring-boot:run

# 3. 访问
# 浏览器打开 http://localhost:5000
```

## 前端开发

```bash
cd frontend
npm install
npm run dev    # 开发模式 (http://localhost:3000)
npm run build  # 生产构建
```

## 项目结构

```
tool-platform/
├── backend-java/              # 后端 Spring Boot 项目
├── frontend/                  # 前端 Vue 3 项目
│   ├── src/
│   │   ├── api/               # API 接口封装
│   │   ├── components/        # 公共组件
│   │   ├── composables/       # 组合式函数
│   │   ├── router/            # Vue Router 路由配置
│   │   ├── store/             # Pinia 状态管理
│   │   ├── styles/            # 全局样式
│   │   ├── views/             # 页面组件
│   │   │   ├── Home/          # 首页
│   │   │   ├── Login/         # 登录注册
│   │   │   ├── Detail/        # 工具详情
│   │   │   ├── Category/      # 分类页
│   │   │   ├── Profile/       # 个人中心
│   │   │   ├── Manage/        # 工具管理
│   │   │   ├── Messages/      # 消息中心
│   │   │   ├── ToolList/      # 全部工具
│   │   │   └── Feedback/      # 反馈评价
│   │   ├── App.vue            # 根组件
│   │   └── main.js            # 入口文件
│   ├── index.html
│   ├── vite.config.js
│   └── package.json
├── docs/                      # 文档
└── start.bat                  # 一键启动脚本
```

## 页面路由

| 路径 | 页面 | 说明 |
|------|------|------|
| `/login` | 登录/注册 | 无需认证 |
| `/home` | 首页 | 仪表盘、最近使用、排行榜 |
| `/tools` | 全部工具 | 工具列表 |
| `/tools/:id` | 工具详情 | 使用工具、上传文件、执行代码 |
| `/category/:name` | 分类页 | 按分类筛选工具 |
| `/profile` | 个人中心 | 查看和修改个人信息 |
| `/manage` | 工具管理 | 作者上传/管理工具 |
| `/messages` | 消息中心 | 查看系统消息 |
| `/feedback` | 反馈评价 | 提交反馈 |

## License

MIT
