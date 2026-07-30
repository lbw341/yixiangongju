import mysql.connector

try:
    conn = mysql.connector.connect(
        host='localhost',
        user='root',
        password='root',
        auth_plugin='caching_sha2_password'
    )
    
    cursor = conn.cursor()
    
    cursor.execute("CREATE DATABASE IF NOT EXISTS tooldb DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    print("数据库 tooldb 创建成功")
    
    cursor.execute("CREATE USER IF NOT EXISTS 'tooluser'@'localhost' IDENTIFIED BY 'tooldb123'")
    print("用户 tooluser 创建成功")
    
    cursor.execute("GRANT ALL PRIVILEGES ON tooldb.* TO 'tooluser'@'localhost'")
    print("权限授予成功")
    
    cursor.execute("FLUSH PRIVILEGES")
    print("权限刷新成功")
    
    conn.close()
    print("\n所有操作完成！")
    
except Exception as e:
    print(f"错误: {e}")
    print("\n尝试无密码连接...")
    try:
        conn = mysql.connector.connect(
            host='localhost',
            user='root',
            password=''
        )
        cursor = conn.cursor()
        cursor.execute("CREATE DATABASE IF NOT EXISTS tooldb DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
        print("数据库 tooldb 创建成功")
        cursor.execute("CREATE USER IF NOT EXISTS 'tooluser'@'localhost' IDENTIFIED BY 'tooldb123'")
        print("用户 tooluser 创建成功")
        cursor.execute("GRANT ALL PRIVILEGES ON tooldb.* TO 'tooluser'@'localhost'")
        print("权限授予成功")
        cursor.execute("FLUSH PRIVILEGES")
        print("权限刷新成功")
        conn.close()
        print("\n所有操作完成！")
    except Exception as e2:
        print(f"无密码连接也失败: {e2}")