# Tool Platform - 系统部署说明

## 一、环境要求

### 1.1 硬件要求

| 项目 | 最低配置 | 推荐配置 |
|------|---------|---------|
| CPU | 2 核 | 4 核+ |
| 内存 | 4 GB | 8 GB+ |
| 硬盘 | 50 GB | 100 GB+ |

### 1.2 软件要求

| 软件 | 版本要求 | 说明 |
|------|---------|------|
| 操作系统 | Windows 10/11 或 Linux (Ubuntu 20.04+) | 支持 Win/Linux |
| JDK | **21** (LTS) | 必须使用 Java 21 |
| Maven | 3.8+ | 用于构建后端项目 |
| Node.js | **18+** | 用于构建前端项目（Vite） |
| npm | 9+ | 前端包管理器 |
| MySQL | 8.0+ | 数据库 |
| Python | 3.8+ | 用于执行用户上传的 Python 代码 |
| Git | 2.30+ | 用于版本控制（可选） |

### 1.3 Python 依赖

Python 环境需要安装常用库（供用户上传的 Python 脚本使用）：

```bash
pip install pandas numpy matplotlib openpyxl requests
```

---

## 二、环境安装

### 2.1 安装 JDK 21

#### Windows 环境

1. 下载 JDK 21：访问 [Adoptium](https://adoptium.net/) 或 [Oracle](https://www.oracle.com/java/technologies/downloads/)
2. 运行安装包，默认安装到 `C:\Program Files\Eclipse Adoptium\jdk-21.x.x`
3. 配置环境变量：
   ```
   JAVA_HOME = C:\Program Files\Eclipse Adoptium\jdk-21.x.x
   Path 添加 %JAVA_HOME%\bin
   ```

#### Linux 环境 (Ubuntu)

```bash
sudo apt update
sudo apt install openjdk-21-jdk -y
# 验证
java -version
```

### 2.2 安装 Node.js 18+

#### Windows 环境

1. 下载 Node.js：访问 [Node.js 官网](https://nodejs.org/)
2. 推荐安装 LTS 版本（18.x 或 20.x）
3. 安装后自动配置环境变量
4. 验证安装：
   ```bash
   node -v
   npm -v
   ```

#### Linux 环境

```bash
# 使用 NodeSource 仓库安装
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install nodejs -y
# 验证
node -v
npm -v
```

### 2.3 安装 Maven

#### Windows 环境

1. 下载 Maven：访问 [Maven 官网](https://maven.apache.org/download.cgi)
2. 解压到 `C:\apache-maven-3.9.x`
3. 配置环境变量：
   ```
   MAVEN_HOME = C:\apache-maven-3.9.x
   Path 添加 %MAVEN_HOME%\bin
   ```

#### Linux 环境

```bash
sudo apt install maven -y
# 验证
mvn -version
```

### 2.4 安装 MySQL 8.0

#### Windows 环境

1. 下载 MySQL 8.0：访问 [MySQL 官网](https://dev.mysql.com/downloads/installer/)
2. 运行安装程序，设置 root 密码
3. 选择 "Server only" 或 "Developer default"

#### Linux 环境 (Ubuntu)

```bash
sudo apt install mysql-server -y
sudo mysql_secure_installation
```

#### 创建数据库和用户

```sql
-- 登录 MySQL
mysql -u root -p

-- 执行以下 SQL
CREATE DATABASE IF NOT EXISTS tooldb
DEFAULT CHARACTER SET utf8mb4
DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'tooluser'@'localhost' 
IDENTIFIED WITH mysql_native_password BY 'tooldb123';

GRANT ALL PRIVILEGES ON tooldb.* TO 'tooluser'@'localhost';
FLUSH PRIVILEGES;
```

### 2.5 安装 Python 3.x

#### Windows 环境

1. 下载 Python：访问 [Python 官网](https://www.python.org/downloads/)
2. 安装时勾选 "Add Python to PATH"
3. 安装常用依赖：
   ```bash
   pip install pandas numpy matplotlib openpyxl requests
   ```

#### Linux 环境

```bash
sudo apt install python3 python3-pip -y
pip3 install pandas numpy matplotlib openpyxl requests
```

---

## 三、项目构建

### 3.1 获取项目代码

```bash
# 克隆项目（如果已推送到 GitHub）
git clone https://github.com/你的用户名/tool-platform.git
cd tool-platform
```

### 3.2 项目结构说明

```
tool-platform/
├── backend-java/              # 后端 Spring Boot 项目
│   ├── src/
│   │   └── main/
│   │       ├── java/         # Java 源码
│   │       │   └── com/toolplatform/
│   │       │       ├── controller/   # 控制器层（认证/工具/消息/反馈/评价/文件/统计）
│   │       │       ├── service/      # 业务层
│   │       │       │   ├── ToolService.java      # 工具业务（Python 执行/文件处理）
│   │       │       │   └── MessageService.java   # 消息业务（创建/回复/已读）
│   │       │       ├── repository/   # 数据访问层（JPA Repositories）
│   │       │       ├── entity/       # 实体类（User/Tool/Message/Feedback/Review 等）
│   │       │       ├── config/       # 配置类（CORS/前端转发/JWT）
│   │       │       ├── dto/          # 数据传输对象
│   │       │       └── util/         # 工具类（JwtUtil）
│   │       └── resources/
│   │           ├── application.properties  # 应用配置
│   │           └── static/           # 前端构建产物（自动生成，Vite 输出）
│   ├── uploads/               # 用户上传文件存储
│   │   ├── templates/         # 模板文件
│   │   └── results/           # 处理结果
│   └── pom.xml                # Maven 配置
├── frontend/                  # 前端 Vue 3 + Vite 项目
│   ├── src/
│   │   ├── api/               # API 接口封装（request.js：统一请求/错误处理）
│   │   ├── components/        # 公共组件（ToolCard 工具卡片、Toast 提示等）
│   │   ├── composables/       # 组合式函数（useToast 消息提示）
│   │   ├── router/            # Vue Router 路由配置（含路由守卫）
│   │   ├── store/             # Pinia 状态管理
│   │   │   └── modules/
│   │   │       └── user.js    # 用户状态（token、role、nickname、未读消息数）
│   │   │   ├── views/             # 页面组件
│   │   │   │   ├── Home/          # 首页（仪表盘）
│   │   │   │   ├── Login/         # 登录/注册
│   │   │   │   ├── Detail/        # 工具详情（使用/执行 Python）
│   │   │   │   ├── Category/      # 分类页
│   │   │   │   ├── Profile/       # 个人中心
│   │   │   │   ├── Manage/        # 工具管理（作者）
│   │   │   │   ├── Messages/      # 消息中心（查看/回复/标记已读）
│   │   │   │   ├── ToolList/      # 全部工具
│   │   │   │   ├── ToolUpload/    # 上传工具（作者）
│   │   │   │   ├── Feedback/      # 反馈与评价
│   │   │   │   └── Layout/        # 布局组件（侧边栏/顶栏/未读铃铛）
│   │   ├── App.vue            # 根组件
│   │   └── main.js            # 入口文件
│   ├── index.html
│   ├── vite.config.js         # 配置：build.outDir → backend static/
│   └── package.json
├── database/                  # 数据库脚本（可选）
├── docs/                      # 文档
│   └── DEPLOY.md              # 本部署文档
├── start.bat                  # ⭐ 一键启动（双击即可）
└── README.md
```

### 3.3 配置文件说明

编辑 `backend-java/src/main/resources/application.properties`：

```properties
# 服务器端口
server.port=5000

# 数据库连接（根据实际环境修改）
spring.datasource.url=jdbc:mysql://localhost:3306/tooldb?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.username=tooluser
spring.datasource.password=tooldb123

# JPA 配置
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false

# 文件上传配置
spring.servlet.multipart.enabled=true
spring.servlet.multipart.max-file-size=100MB
spring.servlet.multipart.max-request-size=100MB

# JWT 配置
jwt.secret=ToolPlatformSecretKey2024ForJWTTokenGenerationAndValidation
jwt.expiration=604800000

# 上传路径
upload.template-dir=uploads/templates
upload.result-dir=uploads/results
upload.max-size=104857600
```

### 3.4 构建前端

```bash
cd frontend

# 安装依赖
npm install

# 开发模式（可选，用于本地开发调试）
npm run dev
# 访问 http://localhost:3000

# 生产构建（构建产物直接输出到后端 static 目录）
npm run build
# 产物自动输出到 backend-java/src/main/resources/static/
# Spring Boot 可直接加载，无需手动复制
```

### 3.5 构建后端

```bash
cd backend-java

# 清理并编译
mvn clean compile

# 打包为 jar 文件
mvn clean package -DskipTests

# 打包完成后，jar 文件位于：
# backend-java/target/tool-platform-1.0.0.jar
```

### 3.6 同步前端到后端

前端构建产物已直接输出到后端的 `static` 目录（由 `vite.config.js` 的 `build.outDir` 配置），**无需手动复制**。

如需手动构建：
```bash
cd frontend
npm run build
# 产物自动输出到 ../backend-java/src/main/resources/static/
```

---

## 四、部署运行

### 4.0 一键启动（推荐）

**双击项目根目录的 `start.bat`** 即可：
- 自动检测 JDK / Maven / Node.js 环境
- 自动安装前端依赖并构建（首次运行）
- 构建产物直接输出到后端 static 目录（无需手动复制）
- 自动编译后端项目
- 清理旧进程并启动后端服务
- **智能等待**：循环检测端口 5000 是否在监听，就绪后自动打开浏览器
- 自动打开浏览器访问 `http://localhost:5000`

### 4.1 开发模式运行

#### 后端

```bash
cd backend-java
mvn spring-boot:run
# 访问 http://localhost:5000
```

#### 前端（独立开发）

```bash
cd frontend
npm run dev
# 访问 http://localhost:3000
# Vite 会自动代理 /api 请求到后端
```

### 4.2 生产模式运行

#### 方式1：直接运行 jar

```bash
cd backend-java
java -Xms512m -Xmx2048m -jar target/tool-platform-1.0.0.jar
```

#### 方式2：一键启动（推荐）

双击项目根目录的 `start.bat`

### 4.3 配置开机自启（Linux systemd）

创建 `/etc/systemd/system/tool-platform.service`：

```ini
[Unit]
Description=Tool Platform Service
After=network.target mysql.service

[Service]
Type=simple
User=your-user
WorkingDirectory=/path/to/tool-platform/backend-java
ExecStart=/usr/bin/java -Xms512m -Xmx2048m -jar /path/to/tool-platform/backend-java/target/tool-platform-1.0.0.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

启动服务：

```bash
sudo systemctl daemon-reload
sudo systemctl start tool-platform
sudo systemctl enable tool-platform  # 开机自启
sudo systemctl status tool-platform  # 查看状态
```

---

## 五、验证部署

### 5.1 访问应用

启动成功后，访问：

```
http://localhost:5000
```

### 5.2 初始账号

系统初始化后会有一个默认管理员账号：

| 账号 | 密码 | 角色 | 权限说明 |
|------|------|------|---------|
| admin | 123456 | 管理员 | 管理所有工具、查看/回复消息、查看所有反馈 |

用户注册时可选择三种角色：

| 角色 | 权限说明 |
|------|---------|
| admin（管理员） | 全部权限 + 查看所有反馈 + 回复消息 |
| author（作者） | 上传/管理自己的工具 + 提交反馈 |
| user（普通用户） | 使用工具 + 提交反馈 + 发表评价 |

### 5.3 API 健康检查

```bash
# 检查服务状态
curl http://localhost:5000/api/stats/dashboard

# 检查登录
curl -X POST http://localhost:5000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'
```

---

## 六、常见问题排查

### 6.1 端口被占用

```bash
# Windows
netstat -ano | findstr :5000
taskkill /PID <PID> /F

# Linux
lsof -i :5000
kill -9 <PID>
```

### 6.2 数据库连接失败

```
错误: Communications link failure
```

**排查步骤：**
1. 确认 MySQL 服务已启动
2. 检查 `application.properties` 中的数据库配置
3. 确认数据库用户权限：
   ```sql
   SHOW GRANTS FOR 'tooluser'@'localhost';
   ```

### 6.3 Python 代码执行失败

```
错误: ModuleNotFoundError: No module named 'pandas'
```

**解决方案：**
```bash
pip install pandas numpy matplotlib openpyxl requests
```

### 6.4 文件上传失败

```
错误: Max upload size exceeded
```

**解决方案：** 修改 `application.properties`：
```properties
spring.servlet.multipart.max-file-size=100MB
spring.servlet.multipart.max-request-size=100MB
```

### 6.5 Python 中文乱码

**解决方案：**
1. 确认数据库字符集为 `utf8mb4`
2. 确认 MySQL 连接 URL 包含 `characterEncoding=utf8`
3. Python 代码执行乱码：后端在 `ScriptRunnerService.run` 中通过 `ProcessBuilder` 设置了两个环境变量：
   - `PYTHONIOENCODING=utf-8` — 强制 Python 的 stdin/stdout/stderr 使用 UTF-8 编码
   - `PYTHONUTF8=1` — 启用 Python UTF-8 模式（Python 3.7+），让所有文件 I/O 默认使用 UTF-8
   - Java 端使用 `StandardCharsets.UTF_8` 读取 Python 进程输出，确保编码一致
4. 如仍有乱码，请确认 Python 版本 ≥ 3.7，并检查上传的 Python 文件是否包含 BOM 头

### 6.6 前端页面刷新 404

**解决方案：** 后端已配置 `FrontendForwardController`，将所有非 API 路由转发到 `index.html`。确认该配置类存在于 `com.toolplatform.config` 包中。

### 6.7 npm 安装失败

```bash
# 清除缓存重试
npm cache clean --force
npm install

# 或使用国内镜像
npm install --registry=https://registry.npmmirror.com
```

### 6.8 Windows 启动器乱码

如果双击 `start.bat` 后出现中文乱码或报错"不是内部或外部命令"：

**原因**：批处理文件中的中文字符在 Windows CMD 中编码不兼容。

**解决方案**：项目根目录的 `start.bat` 已修复为全英文版本，可直接使用。如果需要自定义批处理脚本，请确保：
1. 文件编码为 `GBK`（使用记事本另存为时选择）
2. 或在脚本开头添加 `chcp 65001 >nul` 切换到 UTF-8

### 6.9 前端修改后页面不更新（缓存问题）

修改前端代码并 `npm run build` 后，Spring Boot 可能仍返回旧版页面。

**解决方案**：
1. 将新构建产物从 `frontend/dist/` 复制到 `backend-java/target/classes/static/`
2. 或者重启 Spring Boot 服务（推荐），让其重新加载静态资源

### 6.10 客户端路由跳转变空白

点击侧边栏或工具卡片后页面变空白（只有导航栏没有内容）。

**原因**：`Layout` 组件的 `<transition mode="out-in">` 与多根元素组件冲突导致。

**解决方案**：
1. 将 `Layout/index.vue` 中的 `<transition mode="out-in">` 改为 `<transition name="fade">`（移除 `mode` 属性）
2. 将各页面组件（Category、Detail、Home、Profile 等）用单个根 `<div>` 包裹，确保只有一个根元素
3. 重新 `npm run build` 并同步到 `target/classes/static/`

### 6.11 反馈提交后消息中心看不到新消息

**正常行为**：提交反馈后，系统会自动为所有管理员在消息表中创建一条通知消息（标题格式：`[反馈类型] 标题`）。

**排查**：
1. 确认数据库 `messages` 表中有新增记录
2. 确认 `FeedbackController.submitFeedback` 方法中包含遍历管理员并创建消息的逻辑
3. 清除浏览器缓存后重新登录

---

## 七、备份与恢复

### 7.1 数据库备份

```bash
mysqldump -u tooluser -p tooldb > backup_$(date +%Y%m%d).sql
```

### 7.2 数据库恢复

```bash
mysql -u tooluser -p tooldb < backup_20240101.sql
```

### 7.3 上传文件备份

```bash
# 备份用户上传的文件
tar -czf uploads_backup.tar.gz backend-java/uploads/
```

---

## 八、技术栈版本清单

### 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 21 (LTS) | 后端开发语言 |
| Spring Boot | 3.2.5 | 应用框架 |
| Spring Data JPA | 3.2.5 | 数据访问层 |
| Spring Security Crypto | 6.2.5 | 密码加密 |
| MySQL | 8.0+ | 关系数据库（支持 utf8mb4 编码） |
| JJWT | 0.12.5 | JWT 令牌生成与验证 |
| Apache POI | 5.2.5 | Excel 文件（.xlsx/.xls）读取处理 |
| ZipInputStream | JDK 内置 | ZIP 压缩包读取（支持批量文件处理） |
| ProcessBuilder | JDK 内置 | Python 代码执行（设置 PYTHONIOENCODING=UTF-8） |
| Maven | 3.8+ | 后端构建工具 |

### 前端

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.4+ | 前端框架 |
| Vue Router | 4.3+ | 路由管理 |
| Pinia | 2.1+ | 状态管理 |
| Vite | 5.2+ | 前端构建工具 |
| Tailwind CSS | 3.x | CSS 框架 |
| Chart.js | 4.4+ | 数据可视化 |
| Font Awesome | 6.4+ | 图标库 |

### 运行环境

| 技术 | 版本 | 用途 |
|------|------|------|
| Node.js | 18+ | 前端构建环境 |
| npm | 9+ | 前端包管理 |
| Python | 3.8+ | 代码执行引擎 |

---

## 九、附录

### A. API 接口文档

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/auth/login | 用户登录（返回 JWT token） |
| POST | /api/auth/register | 用户注册（支持 admin/author/user 三种角色） |
| GET | /api/auth/me | 获取当前登录用户信息 |
| PUT | /api/auth/update_profile | 更新个人资料（昵称、头像） |
| GET | /api/tools | 获取在线工具列表（支持按分类/名称筛选） |
| GET | /api/tools/{id} | 获取工具详情 |
| POST | /api/tools/{id}/upload | 上传 Python 文件并执行，返回执行结果 |
| POST | /api/tools | 创建工具（仅作者角色） |
| PUT | /api/tools/{id}/toggle_status | 切换工具上下线（作者本人） |
| DELETE | /api/tools/{id} | 删除工具（作者本人） |
| GET | /api/stats/dashboard | 获取仪表盘统计数据 |
| GET | /api/stats/tool/{id} | 获取单个工具的使用统计 |
| GET | /api/messages | 获取当前用户的消息列表（管理员看全部，普通用户看自己的） |
| POST | /api/messages/create | 创建消息（系统通知/管理员回复等） |
| PUT | /api/messages/{id}/read | 标记指定消息为已读 |
| PUT | /api/messages/read_all | 一键标记全部消息为已读 |
| POST | /api/messages/{id}/reply | 管理员回复消息（回复后状态变为"已答复"） |
| POST | /api/feedback | 提交反馈（自动为所有管理员创建消息通知） |
| GET | /api/feedback/my | 获取当前用户提交的反馈列表 |
| GET | /api/feedback | 获取所有反馈（仅管理员） |
| GET | /api/reviews | 获取指定工具或全部工具的评价列表 |
| POST | /api/reviews | 提交评价 |
| GET | /api/files/preview/{file} | 预览结果文件内容（支持文本/图片） |
| GET | /api/files/download/{file} | 下载结果文件 |

### B. 前端路由表

| 路径 | 页面 | 说明 |
|------|------|------|
| `/login` | 登录/注册 | 无需认证 |
| `/home` | 首页 | 仪表盘（最新工具/统计） |
| `/tools` | 全部工具 | 工具列表（支持搜索/分类筛选） |
| `/tools/:id` | 工具详情 | 使用工具、上传 Python 代码执行、查看执行结果 |
| `/tools/upload` | 上传工具 | 作者专属：上传 Python 工具到平台 |
| `/category/:name` | 分类页 | 按分类筛选工具 |
| `/profile` | 个人中心 | 修改个人信息/密码 |
| `/manage` | 工具管理 | 作者专属：增删改查自己的工具 |
| `/messages` | 消息中心 | 查看/回复消息、标记已读、管理员回复反馈 |
| `/feedback` | 反馈与评价 | 提交反馈、查看历史反馈、发表评价 |

### C. 默认文件目录

```
项目根目录/
├── start.bat                      # ⭐ 一键启动脚本（双击运行）
├── backend-java/
│   ├── uploads/
│   │   ├── templates/                  # 用户上传的模板/Python 文件
│   │   └── results/                    # Python 执行结果文件
│   ├── src/main/resources/static/      # 前端构建源（Vite 输出目录）
│   │   ├── index.html
│   │   └── assets/                     # JS/CSS 打包文件
│   ├── target/classes/static/         # ⭐ Spring Boot 运行时实际加载的静态资源
│   │   ├── index.html                  # 修改前端后需同步到此目录
│   │   └── assets/
│   └── pom.xml
│
├── frontend/
│   ├── src/
│   │   ├── api/request.js              # API 请求封装（baseURL / 拦截器）
│   │   ├── components/                # 公共组件
│   │   ├── composables/               # 组合式函数
│   │   ├── router/index.js            # 路由配置
│   │   ├── store/modules/user.js      # 用户状态（Pinia）
│   │   ├── views/                     # 页面组件
│   │   ├── App.vue
│   │   └── main.js
│   ├── index.html
│   ├── vite.config.js                  # outDir → backend static/
│   └── package.json
│
└── docs/
    └── DEPLOY.md                       # 本文档
```

> **重要提示**：修改前端代码后执行 `npm run build`，构建产物会输出到 `backend-java/src/main/resources/static/`。如需热更新调试，请将 `target/classes/static/` 也同步更新（复制 `src/main/resources/static/` 下的全部文件到 `target/classes/static/`），或直接重启 Spring Boot 服务。
