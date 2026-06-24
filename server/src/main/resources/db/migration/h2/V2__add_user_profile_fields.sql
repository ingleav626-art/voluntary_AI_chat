-- Flyway V2: 为老数据库添加用户个人信息新字段（H2 版本）
-- H2 支持 ADD COLUMN IF NOT EXISTS 语法，可直接使用
-- 新数据库（V1 已创建完整表）执行此脚本时，字段已存在，会被跳过
-- 老数据库（基线化为 V1，跳过 V1）执行此脚本时，添加缺少的字段

ALTER TABLE user ADD COLUMN IF NOT EXISTS gender TINYINT DEFAULT 0;
ALTER TABLE user ADD COLUMN IF NOT EXISTS age INT DEFAULT NULL;
ALTER TABLE user ADD COLUMN IF NOT EXISTS birthday DATE DEFAULT NULL;
ALTER TABLE user ADD COLUMN IF NOT EXISTS detail_bio TEXT DEFAULT NULL;
