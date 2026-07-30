#!/bin/bash

# ============================================
#        一线工具平台 启动脚本 (Linux)
# ============================================

set -e

# 配置变量
APP_NAME="tool-platform-1.0.0.jar"
APP_DIR="$(cd "$(dirname "$0")" && pwd)/backend-java"
APP_PATH="$APP_DIR/target/$APP_NAME"
LOG_FILE="$APP_DIR/app.log"
PID_FILE="$APP_DIR/app.pid"
PORT=5000
JAVA_OPTS="-Xms512m -Xmx2048m"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}       一线工具平台 启动器 (Linux)${NC}"
echo -e "${GREEN}============================================${NC}"
echo

# 检查 Java 环境
if ! command -v java &> /dev/null; then
    echo -e "${RED}[错误] 未检测到 Java 环境${NC}"
    echo "请先安装 JDK 21: sudo apt install openjdk-21-jdk"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -1 | awk -F '"' '{print $2}')
echo -e "[检查] Java 版本: ${JAVA_VERSION}"

# 如果没有构建 jar，则自动构建
if [ ! -f "$APP_PATH" ]; then
    echo -e "${YELLOW}[提示] 未找到 jar 包，正在构建项目...${NC}"
    cd "$APP_DIR"
    mvn clean package -DskipTests -q
    if [ $? -ne 0 ]; then
        echo -e "${RED}[错误] 项目构建失败${NC}"
        exit 1
    fi
    echo -e "${GREEN}[完成] 项目构建成功${NC}"
fi

# 检查端口占用
if lsof -i :"$PORT" > /dev/null 2>&1; then
    EXISTING_PID=$(lsof -t -i :"$PORT")
    echo -e "${YELLOW}[警告] 端口 $PORT 已被进程 $EXISTING_PID 占用${NC}"
    read -p "是否停止该进程？(y/n): " confirm
    if [ "$confirm" = "y" ] || [ "$confirm" = "Y" ]; then
        kill $EXISTING_PID 2>/dev/null
        sleep 2
        echo "[停止] 已停止占用进程"
    else
        echo "[取消] 启动已取消"
        exit 0
    fi
fi

# 如果 PID 文件存在，则停止旧进程
if [ -f "$PID_FILE" ]; then
    OLD_PID=$(cat "$PID_FILE")
    if kill -0 "$OLD_PID" 2>/dev/null; then
        echo "[停止] 正在停止旧进程 (PID: $OLD_PID)..."
        kill $OLD_PID 2>/dev/null
        sleep 2
    fi
    rm -f "$PID_FILE"
fi

# 创建必要目录
mkdir -p "$APP_DIR/uploads/templates" "$APP_DIR/uploads/results" "$APP_DIR/data"

# 启动应用
echo -e "[启动] 正在启动服务..."
cd "$APP_DIR"

nohup java $JAVA_OPTS -jar "target/$APP_NAME" > "$LOG_FILE" 2>&1 &
APP_PID=$!
echo $APP_PID > "$PID_FILE"

# 等待启动
echo -e "[等待] 正在等待服务启动 (PID: $APP_PID)..."
sleep 5

# 检查进程是否存在
if ! kill -0 "$APP_PID" 2>/dev/null; then
    echo -e "${RED}[错误] 服务启动失败，正在查看日志...${NC}"
    echo "---------- 错误日志 ----------"
    grep -i "error\|exception" "$LOG_FILE" | tail -20
    echo "----------------------------"
    rm -f "$PID_FILE"
    exit 1
fi

# 检查端口是否监听
sleep 2
if lsof -i :"$PORT" > /dev/null 2>&1; then
    echo
    echo -e "${GREEN}============================================${NC}"
    echo -e "${GREEN}  服务启动成功！${NC}"
    echo -e "  访问地址: http://localhost:$PORT"
    echo -e "  默认账号: admin"
    echo -e "  默认密码: 123456"
    echo -e "  日志文件: $LOG_FILE"
    echo -e "  进程 PID: $APP_PID"
    echo -e "${GREEN}============================================${NC}"
else
    echo -e "${RED}[警告] 服务已启动但端口尚未监听，请稍后检查日志${NC}"
    echo "  日志: $LOG_FILE"
fi
