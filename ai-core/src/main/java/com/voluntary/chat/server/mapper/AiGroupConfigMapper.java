package com.voluntary.chat.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voluntary.chat.server.entity.AiGroupConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiGroupConfigMapper extends BaseMapper<AiGroupConfig> {

    /**
     * 查询群AI配置（绕过逻辑删除，包含已删除的记录）
     */
    @Select("SELECT * FROM ai_group_config WHERE group_id = #{groupId} AND ai_id = #{aiId} LIMIT 1")
    AiGroupConfig selectByGroupAndAiIgnoreDeleted(@Param("groupId") Long groupId, @Param("aiId") Long aiId);
}