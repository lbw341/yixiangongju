#!/bin/bash

# ============================================
#        一线工具平台 停止脚本 (Linux)
# ============================================

APP_DIR="$(cd "$(dirname "$0")" && pwd)/backend-java"
PID_FILE="$APP_DIR/app.pid"
PORT=5000

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}       一线工具平台 停止器 (Linux)${NC}"
echo -e "${GREEN}============================================${NC}"
echo

# 方式1: 通过 PID 文件停止
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
        echo -e "[发现] 通过 PID 文件找到进程: $PID"
        read -p "是否停止该进程？(y/n): " confirm
        if [ "$confirm" = "y" ] || [ "$confirm" = "Y" ]; then
            echo "[停止] 正在停止服务..."
            # 优雅停止
            kill $PID 2>/dev/null
            sleep 3
            
            # 如果还在运行，强制停止
            if kill -0 "$PID" 2>/dev/null; then
                echo "[强制] 服务未响应，强制停止..."
                kill -9 $PID 2>/dev/null
                sleep 1
            fi
            
            if ! kill -0 "$PID" 2>/dev/null; then
                echo -e "${GREEN}[完成] 服务已停止${NC}"
                rm -f "$PID_FILE"
            else
                echo -e "${RED}[错误] 停止服务失败${NC}"
            fi
        else
            echo "[取消] 操作已取消"
        fi
        exit 0
    else
        echo "[提示] PID 文件中的进程已不存在"
        rm -f "$PID_FILE"
    fi
fi

# 方式2: 通过端口查找
echo -e "[查找] 正在查找占用端口 $PORT 的进程..."
EXISTING_PID=$(lsof -t -i :"$PORT" 2>/dev/null | head -1)

if [ -n "$EXISTING_PID" ]; then
    echo -e "${YELLOW}[发现] 找到占用端口的进程: $EXISTING_PID${NC}"
    read -p "是否停止该进程？(y/n): " confirm
    if [ "$confirm" = "y" ] || [ "$confirm" = "Y" ]; then
        echo "[停止] 正在停止服务..."
        kill $EXISTING_PID 2>/dev/null
        sleep 3
        
        if kill -0 "$EXISTING_PID" 2>/dev/null; then
            echo "[强制] 服务未响应，强制停止..."
            kill -9 $EXISTING_PID 2>/dev/null
            sleep 1
        fi
        
        if ! kill -0 "$EXISTING_PID" 2>/dev/null; then
            echo -e "${GREEN}[完成] 服务已停止${NC}"
        else
            echo -e "${RED}[错误] 停止服务失败${NC}"
        fi
    else
        echo "[取消] 操作已取消"
    fi
else
    echo -e "${GREEN}[提示] 服务未运行${NC}"
fi

echo
