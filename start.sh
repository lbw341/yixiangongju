#!/bin/bash
cd "$(dirname "$0")/backend-java"

if [ ! -f "target/tool-platform-1.0.0.jar" ]; then
    echo "正在构建项目..."
    mvn clean package -DskipTests -q
fi

java -jar target/tool-platform-1.0.0.jar &
sleep 5
xdg-open http://localhost:5000 2>/dev/null || open http://localhost:5000 2>/dev/null || echo "请手动访问: http://localhost:5000"
echo "默认账号: admin / 密码: 123456"
