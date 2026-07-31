-- =============================================
-- 一线工具平台数据库初始化脚本
-- 版本: V1
-- 描述: 创建基础表结构
-- =============================================

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS tooldb
DEFAULT CHARACTER SET utf8mb4
DEFAULT COLLATE utf8mb4_unicode_ci;

USE tooldb;

-- =============================================
-- 用户表
-- =============================================
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密存储）',
    nickname VARCHAR(100) DEFAULT '' COMMENT '昵称',
    email VARCHAR(100) DEFAULT '' COMMENT '邮箱',
    phone VARCHAR(20) DEFAULT '' COMMENT '手机号',
    company VARCHAR(100) DEFAULT '' COMMENT '公司',
    department VARCHAR(100) DEFAULT '' COMMENT '部门',
    bio TEXT COMMENT '个人简介',
    role VARCHAR(20) DEFAULT 'user' COMMENT '角色：admin/user/author',
    avatar VARCHAR(255) DEFAULT '' COMMENT '头像URL',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_username (username),
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- =============================================
-- 工具表
-- =============================================
CREATE TABLE IF NOT EXISTS tools (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL COMMENT '工具名称',
    type VARCHAR(50) NOT NULL COMMENT '工具类型：excel/python/bash/bat',
    category VARCHAR(50) NOT NULL COMMENT '工具分类：规划/建设/优化/维护/客服',
    keywords VARCHAR(500) COMMENT '关键字（逗号分隔）',
    description TEXT COMMENT '功能介绍',
    department VARCHAR(100) COMMENT '所属部门',
    author_id BIGINT NOT NULL COMMENT '作者ID',
    author_name VARCHAR(100) COMMENT '作者名称',
    template_file VARCHAR(255) COMMENT '模板文件名',
    instructions TEXT COMMENT '使用说明',
    faq TEXT COMMENT '常见问题',
    contact_email VARCHAR(100) COMMENT '联系邮箱',
    contact_phone VARCHAR(20) COMMENT '联系电话',
    status VARCHAR(20) DEFAULT 'online' COMMENT '状态：online/offline',
    downloads INT DEFAULT 0 COMMENT '下载次数',
    calls INT DEFAULT 0 COMMENT '调用次数',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_category (category),
    INDEX idx_status (status),
    INDEX idx_author_id (author_id),
    INDEX idx_type (type),
    FULLTEXT INDEX ft_search (name, keywords, description)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工具表';

-- =============================================
-- 评价表
-- =============================================
CREATE TABLE IF NOT EXISTS reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tool_id BIGINT NOT NULL COMMENT '工具ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    username VARCHAR(100) COMMENT '用户名',
    content TEXT NOT NULL COMMENT '评价内容',
    rating INT DEFAULT 5 COMMENT '评分（1-5）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_tool_id (tool_id),
    INDEX idx_user_id (user_id),
    FOREIGN KEY (tool_id) REFERENCES tools(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评价表';

-- =============================================
-- 下载统计表
-- =============================================
CREATE TABLE IF NOT EXISTS download_stats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tool_id BIGINT NOT NULL COMMENT '工具ID',
    date DATE NOT NULL COMMENT '日期',
    count INT DEFAULT 1 COMMENT '下载/调用次数',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_tool_date (tool_id, date),
    INDEX idx_tool_id (tool_id),
    INDEX idx_date (date),
    FOREIGN KEY (tool_id) REFERENCES tools(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='下载统计表';

-- =============================================
-- 用户工具使用记录表
-- =============================================
CREATE TABLE IF NOT EXISTS user_tool_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    tool_id BIGINT NOT NULL COMMENT '工具ID',
    use_count INT DEFAULT 1 COMMENT '使用次数',
    last_used VARCHAR(50) COMMENT '最后使用时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_user_tool (user_id, tool_id),
    INDEX idx_user_id (user_id),
    INDEX idx_tool_id (tool_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (tool_id) REFERENCES tools(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户工具使用记录表';

-- =============================================
-- 消息表
-- =============================================
CREATE TABLE IF NOT EXISTS messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    type VARCHAR(50) NOT NULL COMMENT '消息类型：问题反馈/系统通知/问题答复',
    title VARCHAR(200) NOT NULL COMMENT '标题',
    content TEXT COMMENT '内容',
    from_user VARCHAR(100) DEFAULT '' COMMENT '发送者名称',
    from_user_id BIGINT COMMENT '发送者用户ID',
    to_user_id BIGINT COMMENT '接收者用户ID',
    tool_id BIGINT COMMENT '关联工具ID',
    status VARCHAR(20) DEFAULT 'unread' COMMENT '状态：unread/read',
    reply_content TEXT DEFAULT '' COMMENT '回复内容',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表';

-- =============================================
-- 初始化管理员账号
-- 密码: 123456 (实际应用中应使用BCrypt加密)
-- =============================================
INSERT INTO users (username, password, nickname, role) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '管理员', 'admin')
ON DUPLICATE KEY UPDATE username='admin';

-- =============================================
-- 示例工具数据
-- =============================================
INSERT INTO tools (name, type, category, keywords, description, department, author_id, author_name, instructions, status) VALUES
('项目周报自动生成器', 'python', '规划', '周报,报告,自动生成', '根据输入的工作内容自动生成格式化的项目周报', '研发部', 1, '管理员', '1. 输入本周工作内容\n2. 点击生成周报\n3. 下载生成的周报文件', 'online'),
('Excel数据审核工具', 'excel', '建设', 'excel,审核,数据校验', '自动审核Excel表格数据，检查格式和内容正确性', '研发部', 1, '管理员', '1. 上传待审核的Excel文件\n2. 系统自动检查数据\n3. 下载审核结果', 'online'),
('Python脚本执行器', 'python', '优化', 'python,脚本,执行', '在线执行Python脚本，支持数据处理和自动化任务', '研发部', 1, '管理员', '1. 上传.py文件或输入代码\n2. 点击执行\n3. 查看执行结果', 'online')
ON DUPLICATE KEY UPDATE name=VALUES(name);