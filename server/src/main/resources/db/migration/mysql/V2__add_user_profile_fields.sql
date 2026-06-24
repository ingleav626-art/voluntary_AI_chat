-- Flyway V2: 为老数据库添加用户个人信息新字段（MySQL 版本）
-- 使用动态 SQL 条件判断，确保字段不存在时才添加
-- 新数据库（V1 已创建完整表）执行此脚本时，字段已存在，会被跳过
-- 老数据库（基线化为 V1，跳过 V1）执行此脚本时，添加缺少的字段

-- 添加 gender 字段（性别：0-未知，1-男，2-女）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'user' AND column_name = 'gender');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `user` ADD COLUMN `gender` TINYINT DEFAULT 0 COMMENT ''性别：0-未知，1-男，2-女''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加 age 字段（年龄）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'user' AND column_name = 'age');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `user` ADD COLUMN `age` INT DEFAULT NULL COMMENT ''年龄''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加 birthday 字段（生日）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'user' AND column_name = 'birthday');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `user` ADD COLUMN `birthday` DATE DEFAULT NULL COMMENT ''生日''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加 detail_bio 字段（个人详细说明）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'user' AND column_name = 'detail_bio');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `user` ADD COLUMN `detail_bio` TEXT DEFAULT NULL COMMENT ''个人详细说明''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
