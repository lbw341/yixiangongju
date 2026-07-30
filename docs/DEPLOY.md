# 一线工具平台 - 系统部署说明

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
| Maven | 3.8+ | 用于构建项目 |
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

### 2.2 安装 Maven

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

### 2.3 安装 MySQL 8.0

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

### 2.4 安装 Python 3.x

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
├── backend-java/              # 后端 Java 项目
│   ├── src/
│   │   └── main/
│   │       ├── java/         # Java 源码
│   │       │   └── com/toolplatform/
│   │       └── resources/    # 配置文件和前端文件
│   │           ├── application.properties
│   │           └── static/  # 前端页面
│   ├── data/                  # 数据库文件目录
│   ├── uploads/               # 用户上传文件存储
│   ├── app.log                # 运行日志（启动后生成）
│   └── pom.xml                # Maven 配置
├── frontend/                  # 前端源码（参考用）
├── database/                  # 数据库脚本
├── docs/                      # 文档
│   └── DEPLOY.md              # 本部署文档
├── scripts/                   # 辅助脚本
├── start.bat                  # ⭐ Windows 启动器
├── stop.bat                   # ⭐ Windows 停止器
├── console.bat                # ⭐ Windows 控制台菜单
├── start.sh                   # ⭐ Linux 启动器
└── stop.sh                    # ⭐ Linux 停止器
```

### 3.3 配置文件说明

编辑 `backend-java/src/main/resources/application.properties`：

```properties
# 数据库连接（根据实际环境修改）
spring.datasource.url=jdbc:mysql://localhost:3306/tooldb?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.username=tooluser
spring.datasource.password=tooldb123

# JPA 配置
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false

# 服务端口
server.port=5000

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

### 3.4 构建项目

```bash
cd backend-java

# 清理并编译
mvn clean compile

# 打包为 jar 文件
mvn clean package -DskipTests

# 打包完成后，jar 文件位于：
# backend-java/target/tool-platform-1.0.0.jar
```

---

## 四、部署运行

### 4.0 启动器使用（推荐）

项目已提供完整的启动器脚本，支持一键启动/停止/查看状态。

#### Windows 环境

双击运行 `console.bat` 打开控制台菜单：

```
============================================
    一线工具平台 控制台
============================================

  [1] 启动服务
  [2] 停止服务
  [3] 重启服务
  [4] 查看服务状态
  [5] 查看日志
  [6] 打开浏览器
  [7] 构建项目
  [0] 退出
```

也可以直接使用独立脚本：

| 脚本 | 用途 |
|------|------|
| `start.bat` | 启动服务（自动构建+启动+打开浏览器） |
| `stop.bat` | 停止服务 |
| `console.bat` | 控制台菜单（包含所有操作） |

#### Linux 环境

```bash
# 添加执行权限
chmod +x start.sh stop.sh

# 启动服务（首次会自动构建）
./start.sh

# 停止服务
./stop.sh
```

#### 启动器特性

- ✅ **自动构建**：首次运行自动检测并构建项目
- ✅ **端口检测**：自动检测端口占用并提示处理
- ✅ **日志输出**：启动成功后显示访问地址和账号
- ✅ **浏览器跳转**：Windows 下自动打开浏览器
- ✅ **优雅停止**：支持优雅停止和强制停止
- ✅ **彩色输出**：Linux 下支持彩色日志提示

### 4.1 开发模式运行

```bash
cd backend-java

# 方式1：使用 Maven 运行
mvn spring-boot:run

# 方式2：使用 jar 文件运行
java -jar target/tool-platform-1.0.0.jar
```

### 4.2 生产模式运行

#### 方式1：直接运行 jar

```bash
cd backend-java
java -Xms512m -Xmx2048m -jar target/tool-platform-1.0.0.jar
```

#### 方式2：使用项目启动器（推荐）

Windows:
```batch
# 双击运行
start.bat
```

Linux:
```bash
# 添加权限并运行
chmod +x start.sh
./start.sh
```

### 4.3 配置开机自启（Linux systemd）

创建 `/etc/systemd/system/tool-platform.service`：

```ini
[Unit]
Description=一线工具平台
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

| 账号 | 密码 | 角色 |
|------|------|------|
| admin | 123456 | 管理员 |

### 5.3 API 健康检查

```bash
# 检查服务状态
curl http://localhost:5000/api/stats/dashboard

# 检查登录
curl -X POST http://localhost:5000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'
```

### 5.4 日志查看

```bash
# 查看实时日志
tail -f backend-java/app.log

# 查看最近错误
grep -i error backend-java/app.log | tail -20
```

---

## 六、常见问题排查

### 6.1 端口被占用

```bash
# Windows
netstat -ano | findstr 5000
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

### 6.5 中文乱码

**解决方案：**
- 确认数据库字符集为 `utf8mb4`
- 确认 MySQL 连接 URL 包含 `characterEncoding=utf8`

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

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 21 (LTS) | 后端开发语言 |
| Spring Boot | 3.2.5 | 应用框架 |
| Spring Data JPA | 3.2.5 | 数据访问层 |
| Spring Security Crypto | 6.2.5 | 密码加密 |
| MySQL | 8.0+ | 关系数据库 |
| H2 | 2.2+ (可选) | 内存数据库（开发用） |
| JJWT | 0.12.5 | JWT 令牌 |
| Apache POI | 5.2.5 | Excel 文件处理 |
| Maven | 3.8+ | 构建工具 |
| HTML5 + Tailwind CSS | - | 前端页面 |
| JavaScript (ES6+) | - | 前端逻辑 |
| Chart.js | 4.4+ | 数据可视化 |
| Font Awesome | 6.4+ | 图标库 |
| Python | 3.8+ | 代码执行引擎 |

---

## 九、附录

### A. 完整的 MySQL 建表语句

参考：`database/V1__init_schema.sql`

### B. 前端 API 接口文档

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/auth/login | 用户登录 |
| POST | /api/auth/register | 用户注册 |
| GET | /api/auth/me | 获取当前用户信息 |
| PUT | /api/auth/update_profile | 更新个人资料 |
| GET | /api/tools | 获取工具列表 |
| GET | /api/tools/{id} | 获取工具详情 |
| POST | /api/tools/{id}/upload | 上传文件 |
| POST | /api/tools/create | 创建工具 |
| GET | /api/stats/dashboard | 获取仪表盘数据 |
| GET | /api/messages | 获取消息列表 |
| POST | /api/messages/create | 创建消息 |
| GET | /api/reviews | 获取评价列表 |
| POST | /api/reviews | 提交评价 |

### C. 默认文件目录结构

```
tool-platform/
├── backend-java/
│   ├── data/                           # 数据库文件目录
│   ├── uploads/
│   │   ├── templates/                  # 用户上传的模板文件
│   │   └── results/                    # 处理结果文件
│   ├── app.log                         # 运行日志（启动后生成）
│   ├── app.pid                         # 进程 PID 文件（启动后生成）
│   ├── src/main/resources/static/      # 前端静态文件
│   │   ├── index.html
│   │   ├── css/
│   │   └── js/
│   └── target/                         # 编译输出（构建后生成）
│       └── tool-platform-1.0.0.jar
├── start.bat                           # Windows 启动器
├── stop.bat                            # Windows 停止器
├── console.bat                         # Windows 控制台菜单
├── start.sh                            # Linux 启动器
└── stop.sh                             # Linux 停止器
```

### D. 启动器脚本说明

#### Windows 脚本

| 脚本 | 功能 | 使用方式 |
|------|------|---------|
| `start.bat` | 启动服务 | 双击运行 |
| `stop.bat` | 停止服务 | 双击运行 |
| `console.bat` | 控制台菜单 | 双击运行，选择操作 |

#### Linux 脚本

| 脚本 | 功能 | 使用方式 |
|------|------|---------|
| `start.sh` | 启动服务 | `./start.sh` |
| `stop.sh` | 停止服务 | `./stop.sh` |
