package com.voluntary.chat.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voluntary.chat.server.entity.GroupMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 群成员 Mapper
 *
 * @author voluntary-ai-chat
 * @since 1.0.0
 */
@Mapper
public interface GroupMemberMapper extends BaseMapper<GroupMember> {

    /**
     * 查询用户加入的所有群组ID列表
     *
     * @param userId 用户ID
     * @return 群组ID列表
     */
    @Select("SELECT group_id FROM group_member WHERE user_id = #{userId} AND is_deleted = 0")
    List<Long> selectGroupIdsByUserId(@Param("userId") Long userId);

    /**
     * 查询群组的成员数量
     *
     * @param groupId 群组ID
     * @return 成员数量
     */
    @Select("SELECT COUNT(*) FROM group_member WHERE group_id = #{groupId} AND is_deleted = 0")
    int countMembers(@Param("groupId") Long groupId);

    /**
     * 查询用户在群组中的角色
     *
     * @param groupId 群组ID
     * @param userId  用户ID
     * @return 角色（0-普通成员，1-管理员，2-群主），不在群中返回null
     */
    @Select("SELECT role FROM group_member WHERE group_id = #{groupId} AND user_id = #{userId} AND is_deleted = 0")
    Integer selectRoleByGroupIdAndUserId(@Param("groupId") Long groupId, @Param("userId") Long userId);

    /**
     * 查询群组所有成员的userId列表
     *
     * @param groupId 群组ID
     * @return 成员userId列表
     */
    @Select("SELECT user_id FROM group_member WHERE group_id = #{groupId} AND is_deleted = 0")
    List<Long> selectGroupMemberUserIds(@Param("groupId") Long groupId);

    /**
     * 查询用户在群组中的完整记录（含 role、nickname 等字段）
     *
     * @param groupId 群组ID
     * @param userId  用户ID
     * @return 群成员记录，不在群中则返回 null
     */
    @Select("SELECT * FROM group_member WHERE group_id = #{groupId} AND user_id = #{userId} AND is_deleted = 0")
    GroupMember selectByGroupIdAndUserId(@Param("groupId") Long groupId, @Param("userId") Long userId);

    /**
     * 逻辑删除群组的所有成员
     *
     * @param groupId 群组ID
     */
    @org.apache.ibatis.annotations.Update("UPDATE group_member SET is_deleted = 1 WHERE group_id = #{groupId}")
    void logicalDeleteByGroupId(@Param("groupId") Long groupId);

    /**
     * 查询用户在群组中的记录（包括已退出的）
     *
     * <p>用于判断用户是否曾经加入过群，以便再次邀请时恢复记录。</p>
     *
     * @param groupId 群组ID
     * @param userId  用户ID
     * @return 群成员记录（包括已退出的），不存在则返回 null
     */
    @Select("SELECT * FROM group_member WHERE group_id = #{groupId} AND user_id = #{userId}")
    GroupMember selectByGroupIdAndUserIdIncludeDeleted(@Param("groupId") Long groupId, @Param("userId") Long userId);

    /**
     * 恢复已退出的群成员
     *
     * <p>将 is_deleted 设置为 0，并更新角色为普通成员。</p>
     *
     * @param groupId 群组ID
     * @param userId  用户ID
     */
    @org.apache.ibatis.annotations.Update("UPDATE group_member SET is_deleted = 0, role = 0, update_time = NOW() WHERE group_id = #{groupId} AND user_id = #{userId}")
    void restoreMember(@Param("groupId") Long groupId, @Param("userId") Long userId);
}