package com.voluntary.chat.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.voluntary.chat.common.dto.PageResult;
import com.voluntary.chat.common.enums.MessageType;
import com.voluntary.chat.common.enums.TargetType;
import com.voluntary.chat.server.dto.response.ConversationResponse;
import com.voluntary.chat.server.entity.Message;
import com.voluntary.chat.server.entity.User;
import com.voluntary.chat.server.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final MessageMapper messageMapper;
    private final MessageService messageService;
    private final UserService userService;

    /**
     * 获取当前用户的会话列表
     * 查询用户参与的所有会话，按最后消息时间倒序排列
     */
    public PageResult<ConversationResponse> getConversations(Long userId, int page, int size) {
        // 查询用户参与的所有会话ID（去重）
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getSenderId, userId)
                .or()
                .eq(Message::getTargetId, userId);
        wrapper.eq(Message::getIsDeleted, 0)
                .select(Message::getSessionId)
                .groupBy(Message::getSessionId);

        List<Message> sessionMessages = messageMapper.selectList(wrapper);
        List<String> sessionIds = sessionMessages.stream()
                .map(Message::getSessionId)
                .distinct()
                .toList();

        if (sessionIds.isEmpty()) {
            return PageResult.of(List.of(), 0, page, size);
        }

        // 为每个会话获取最后一条消息和未读数
        List<ConversationResponse> conversations = new ArrayList<>();
        for (String sessionId : sessionIds) {
            Message lastMsg = messageService.getLastMessage(sessionId);
            if (lastMsg == null) {
                continue;
            }

            long unreadCount = messageService.getUnreadCount(userId, sessionId);
            ConversationResponse conv = buildConversationResponse(userId, sessionId, lastMsg, unreadCount);
            conversations.add(conv);
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
            // 群聊
            String[] parts = sessionId.split("_");
            Long groupId = Long.parseLong(parts[1]);
            builder.targetId(groupId)
                    .targetType(TargetType.GROUP.name())
                    .targetName("群聊")
                    .targetAvatar(null);
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
