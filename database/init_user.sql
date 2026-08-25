-- 一线工具平台 数据库账号初始化（幂等，可重复执行）
-- 用法: mysql -u root -p < init_user.sql
CREATE USER IF NOT EXISTS 'tooluser'@'localhost' IDENTIFIED BY 'tooldb123';
GRANT ALL PRIVILEGES ON tooldb.* TO 'tooluser'@'localhost';
FLUSH PRIVILEGES;
