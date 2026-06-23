package com.voluntary.chat.server.service;

import com.voluntary.chat.common.dto.PageResult;
import com.voluntary.chat.common.enums.MessageType;
import com.voluntary.chat.common.enums.TargetType;
import com.voluntary.chat.server.dto.response.ConversationResponse;
import com.voluntary.chat.server.entity.GroupEntity;
import com.voluntary.chat.server.entity.Message;
import com.voluntary.chat.server.entity.User;
import com.voluntary.chat.server.mapper.GroupMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final MessageService messageService;
    private final UserService userService;
    private final GroupMapper groupMapper;

    /**
     * 获取当前用户的会话列表（兼容：无关键词搜索）
     */
    public PageResult<ConversationResponse> getConversations(Long userId, int page, int size) {
        return getConversations(userId, page, size, null);
    }

    /**
     * 获取当前用户的会话列表
     * 查询用户参与的所有会话，按最后消息时间倒序排列
     *
     * @param keyword 可选搜索关键词，按会话名称过滤
     */
    public PageResult<ConversationResponse> getConversations(Long userId, int page, int size, String keyword) {
        // 查询用户参与的所有会话ID（去重）
        List<String> sessionIds = messageService.getUserSessionIds(userId);

        if (sessionIds.isEmpty()) {
            return PageResult.of(List.of(), 0, page, size);
        }

        // 为每个会话获取最后一条消息和未读数
        List<ConversationResponse> conversations = new ArrayList<>();
        for (String sessionId : sessionIds) {
            Message lastMsg = messageService.getLastMessage(sessionId);
            // 群会话即使没有消息也要展示（新创建的群或刚被邀请入群时没有消息）
            if (lastMsg == null) {
                if (sessionId.startsWith("g_")) {
                    // 群会话无消息：创建一个仅含群名的占位会话
                    String[] parts = sessionId.split("_");
                    Long groupId = Long.parseLong(parts[1]);
                    GroupEntity group = groupMapper.selectById(groupId);
                    if (group != null) {
                        ConversationResponse conv = ConversationResponse.builder()
                                .sessionId(sessionId)
                                .targetId(groupId)
                                .targetType(TargetType.GROUP.name())
                                .targetName(group.getName())
                                .targetAvatar(group.getAvatar())
                                .lastMessage("")
                                .lastMessageType("")
                                .lastMessageTime(group.getCreateTime())
                                .unreadCount(0)
                                .build();
                        conversations.add(conv);
                    }
                }
                continue;
            }

            long unreadCount = messageService.getUnreadCount(userId, sessionId);
            ConversationResponse conv = buildConversationResponse(userId, sessionId, lastMsg, unreadCount);
            conversations.add(conv);
        }

        // 搜索过滤：按 targetName 匹配关键词（大小写不敏感）
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim().toLowerCase();
            conversations = conversations.stream()
                    .filter(c -> c.getTargetName() != null && c.getTargetName().toLowerCase().contains(kw))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }

        // 按最后消息时间倒序
        conversations.sort(Comparator.comparing(ConversationResponse::getLastMessageTime).reversed());

        // 分页
        int total = conversations.size();
        int fromIndex = Math.min((page - 1) * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<ConversationResponse> pagedList = conversations.subList(fromIndex, toIndex);

        return PageResult.of(pagedList, total, page, size);
    }

    private ConversationResponse buildConversationResponse(Long userId, String sessionId,
            Message lastMsg, long unreadCount) {
        ConversationResponse.ConversationResponseBuilder builder = ConversationResponse.builder()
                .sessionId(sessionId)
                .lastMessage(lastMsg.getContent())
                .lastMessageType(MessageType.values()[lastMsg.getType()].name())
                .lastMessageTime(lastMsg.getCreateTime())
                .unreadCount(unreadCount);

        if (sessionId.startsWith("p_")) {
            // 单聊：对方是 target
            String[] parts = sessionId.split("_");
            Long user1 = Long.parseLong(parts[1]);
            Long user2 = Long.parseLong(parts[2]);
            Long targetId = user1.equals(userId) ? user2 : user1;
            User target = userService.findById(targetId);
            builder.targetId(targetId)
                    .targetType(TargetType.USER.name())
                    .targetName(target.getUsername())
                    .targetAvatar(target.getAvatar());
        } else if (sessionId.startsWith("g_")) {
            // 群聊：查询真实群名称和头像
            String[] parts = sessionId.split("_");
            Long groupId = Long.parseLong(parts[1]);
            GroupEntity group = groupMapper.selectById(groupId);
            if (group != null) {
                builder.targetId(groupId)
                        .targetType(TargetType.GROUP.name())
                        .targetName(group.getName())
                        .targetAvatar(group.getAvatar());
            } else {
                builder.targetId(groupId)
                        .targetType(TargetType.GROUP.name())
                        .targetName("群聊")
                        .targetAvatar(null);
            }
        } else if (sessionId.startsWith("a_")) {
            // AI单聊
            String[] parts = sessionId.split("_");
            Long aiId = Long.parseLong(parts[1]);
            builder.targetId(aiId)
                    .targetType(TargetType.USER.name())
                    .targetName("AI助手")
                    .targetAvatar(null);
        }

        return builder.build();
    }
}
