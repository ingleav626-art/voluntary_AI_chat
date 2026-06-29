-- 为 ai_profile 表添加 base_url 字段（API 基准地址）
ALTER TABLE ai_profile ADD COLUMN IF NOT EXISTS base_url VARCHAR(500) DEFAULT NULL COMMENT 'API 基准地址（可选，用于自定义 API endpoint）' AFTER api_key_enc;