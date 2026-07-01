package com.voluntary.chat.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.voluntary.chat.common.dto.PageResult;
import com.voluntary.chat.common.enums.GroupRole;
import com.voluntary.chat.common.enums.MessageType;
import com.voluntary.chat.common.enums.SenderType;
import com.voluntary.chat.common.enums.TargetType;
import com.voluntary.chat.common.exception.BusinessException;
import com.voluntary.chat.common.exception.ErrorCode;
import com.voluntary.chat.server.dto.request.MarkReadRequest;
import com.voluntary.chat.server.dto.request.SendMessageRequest;
import com.voluntary.chat.server.dto.response.MessageResponse;
import com.voluntary.chat.server.dto.response.RecallMessageResponse;
import com.voluntary.chat.server.dto.response.SendMessageResponse;
import com.voluntary.chat.server.entity.Message;
import com.voluntary.chat.server.entity.MessageRead;
import com.voluntary.chat.server.entity.User;
import com.voluntary.chat.server.mapper.GroupMemberMapper;
import com.voluntary.chat.server.mapper.MessageMapper;
import com.voluntary.chat.server.mapper.MessageReadMapper;
import com.voluntary.chat.server.service.ConversationCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageMapper messageMapper;
    private final MessageReadMapper messageReadMapper;
    private final UserService userService;
    private final GroupMemberMapper groupMemberMapper;
    private final ConversationCacheService conversationCacheService;

    /** 消息撤回时间限制：2分钟 */
    private static final long RECALL_TIMEOUT_MINUTES = 2;

    /**
     * 发送消息（REST 备用通道）
     * 解析 sessionId，构建消息实体并持久化
     */
    @Transactional
    public SendMessageResponse sendMessage(Long senderId, SendMessageRequest request) {
        Message message = buildMessage(senderId, request);
        messageMapper.insert(message);
        log.info("消息发送成功: messageId={}, sessionId={}", message.getId(), message.getSessionId());
        return SendMessageResponse.builder()
                .messageId(message.getId())
                .createTime(message.getCreateTime())
                .build();
    }

    /**
     * 构建 Message 实体
     * 根据 sessionId 格式解析 targetId 和 targetType
     */
    private Message buildMessage(Long senderId, SendMessageRequest request) {
        MessageType msgType = parseMessageType(request.getType());
        SessionInfo sessionInfo = parseSessionId(request.getSessionId());

        Message message = new Message();
        message.setSessionId(request.getSessionId());
        message.setSenderId(senderId);
        message.setSenderType(SenderType.USER.ordinal());
        message.setTargetId(sessionInfo.targetId);
        message.setTargetType(sessionInfo.targetType.ordinal());
        message.setType(msgType.ordinal());
        message.setContent(request.getContent());
        return message;
    }

    /**
     * 获取聊天记录（按会话维度分页）
     */
    public PageResult<MessageResponse> getHistory(String sessionId, Long userId, int page, int size) {
        log.info("[MSG-HISTORY] sessionId={}, userId={}, page={}, size={}",
                sessionId, userId, page, size);
        // COUNT 查询不需要 ORDER BY（H2 不支持 COUNT + ORDER BY 组合）
        LambdaQueryWrapper<Message> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(Message::getSessionId, sessionId)
                .eq(Message::getIsDeleted, 0);
        long total = messageMapper.selectCount(countWrapper);

        // 数据查询需要 ORDER BY
        LambdaQueryWrapper<Message> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Message::getSessionId, sessionId)
                .eq(Message::getIsDeleted, 0)
                .orderByDesc(Message::getCreateTime);

        int offset = (page - 1) * size;
        List<Message> messages = messageMapper.selectList(queryWrapper.last("LIMIT " + offset + ", " + size));

        // 批量获取发送者信息，避免循环查询
        Set<Long> senderIds = messages.stream()
                .map(Message::getSenderId)
                .filter(id -> SenderType.USER.ordinal() == messages.stream()
                        .filter(m -> m.getSenderId().equals(id))
                        .findFirst().map(Message::getSenderType).orElse(-1))
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userService.findByIds(senderIds);

        // 修复：批量查询当前用户对这些消息的已读状态
        Set<Long> readMessageIds = getReadMessageIds(userId, messages);

        List<MessageResponse> list = messages.stream()
                .map(msg -> toResponse(msg, userMap, readMessageIds.contains(msg.getId())))
                .toList();

        return PageResult.of(list, total, page, size);
    }

    /**
     * 批量查询当前用户对消息列表的已读状态
     *
     * @param userId   当前用户ID
     * @param messages 消息列表
     * @return 已读消息ID集合
     */
    private Set<Long> getReadMessageIds(final Long userId, final List<Message> messages) {
        if (userId == null || messages == null || messages.isEmpty()) {
            return Set.of();
        }
        try {
            // 只查询当前用户发送的消息的已读状态
            List<Long> myMessageIds = messages.stream()
                    .filter(m -> userId.equals(m.getSenderId()))
                    .map(Message::getId)
                    .toList();
            if (myMessageIds.isEmpty()) {
                return Set.of();
            }
            // 查询对方（接收方）已读的记录
            // 注意：这里的"已读"是指接收方读了我发的消息
            // 对于私聊，接收方读了 → 我能看到"已读"
            // 对于群聊，发送方只能看到自己消息的群成员已读人数（暂简化为部分已读）
            LambdaQueryWrapper<MessageRead> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(MessageRead::getMessageId, myMessageIds);
            List<MessageRead> reads = messageReadMapper.selectList(wrapper);
            // 这里假设每条消息至少有一个接收方读了就算已读（实际应更精细）
            Set<Long> result = new HashSet<>();
            for (MessageRead r : reads) {
                result.add(r.getMessageId());
            }
            return result;
        } catch (Exception e) {
            log.warn("查询已读状态失败: userId={}", userId, e);
            return Set.of();
        }
    }

    /**
     * 撤回消息
     * 人-人消息：2分钟内可撤回；AI消息：随时可撤回；
     * 群消息：仅群主和管理员可撤回他人消息，普通成员只能撤回自己的消息
     */
    @Transactional
    public RecallMessageResponse recallMessage(Long userId, Long messageId) {
        Message message = messageMapper.selectById(messageId);
        if (message == null || message.getIsDeleted() == 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "消息不存在");
        }

        // AI消息可随时撤回
        if (message.getSenderType() == SenderType.AI.ordinal()) {
            message.setRecallTime(LocalDateTime.now());
            messageMapper.updateById(message);
            return RecallMessageResponse.builder()
                    .messageId(message.getId())
                    .sessionId(message.getSessionId())
                    .senderId(message.getSenderId())
                    .build();
        }

        // 群消息：群主/管理员可撤回他人消息
        String sessionId = message.getSessionId();
        if (sessionId != null && sessionId.startsWith("g_")) {
            Long groupId = Long.parseLong(sessionId.split("_")[1]);
            Integer role = groupMemberMapper.selectRoleByGroupIdAndUserId(groupId, userId);
            boolean isGroupAdmin = role != null && role >= GroupRole.ADMIN.getCode();

            if (!message.getSenderId().equals(userId) && !isGroupAdmin) {
                // 不是发送者，也不是群管理，无权撤回
                throw new BusinessException(ErrorCode.NO_PERMISSION_TO_RECALL);
            }
            // 群管理员/群主可以撤回任何消息，不限制2分钟
            if (message.getSenderId().equals(userId) && !isGroupAdmin) {
                // 普通成员只能撤回自己的消息，且需在2分钟内
                if (message.getCreateTime().plusMinutes(RECALL_TIMEOUT_MINUTES).isBefore(LocalDateTime.now())) {
                    throw new BusinessException(ErrorCode.MESSAGE_RECALL_TIMEOUT);
                }
            }
        } else {
            // 人-人消息：只能撤回自己的消息，且2分钟内
            if (!message.getSenderId().equals(userId)) {
                throw new BusinessException(ErrorCode.NO_PERMISSION_TO_RECALL);
            }
            if (message.getCreateTime().plusMinutes(RECALL_TIMEOUT_MINUTES).isBefore(LocalDateTime.now())) {
                throw new BusinessException(ErrorCode.MESSAGE_RECALL_TIMEOUT);
            }
        }

        message.setRecallTime(LocalDateTime.now());
        messageMapper.updateById(message);

        log.info("消息撤回成功: messageId={}, userId={}", messageId, userId);

        return RecallMessageResponse.builder()
                .messageId(message.getId())
                .sessionId(message.getSessionId())
                .senderId(message.getSenderId())
                .build();
    }

    /**
     * 标记消息已读
     * 批量插入已读记录，忽略重复（利用唯一索引）
     */
    @Transactional
    public void markRead(Long userId, MarkReadRequest request) {
        log.info("[MSG-READ] userId={}, sessionId={}, messageIds={}",
                userId, request.getSessionId(), request.getMessageIds());
        for (Long messageId : request.getMessageIds()) {
            // 检查是否已标记
            LambdaQueryWrapper<MessageRead> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MessageRead::getMessageId, messageId)
                    .eq(MessageRead::getUserId, userId);
            if (messageReadMapper.selectCount(wrapper) == 0) {
                MessageRead read = new MessageRead();
                read.setMessageId(messageId);
                read.setUserId(userId);
                read.setSessionId(request.getSessionId());
                messageReadMapper.insert(read);
            }
        }
        // 清零未读缓存
        if (request.getSessionId() != null) {
            conversationCacheService.clearUnread(userId, request.getSessionId());
        }
    }

    /**
     * 获取会话的未读消息数
     */
    public long getUnreadCount(Long userId, String sessionId) {
        LambdaQueryWrapper<Message> msgWrapper = new LambdaQueryWrapper<>();
        msgWrapper.eq(Message::getSessionId, sessionId)
                .ne(Message::getSenderId, userId)
                .eq(Message::getIsDeleted, 0)
                .isNull(Message::getRecallTime);

        List<Message> unreadMessages = messageMapper.selectList(msgWrapper);
        if (unreadMessages.isEmpty()) {
            return 0;
        }

        Set<Long> messageIds = unreadMessages.stream()
                .map(Message::getId)
                .collect(Collectors.toSet());

        LambdaQueryWrapper<MessageRead> readWrapper = new LambdaQueryWrapper<>();
        readWrapper.eq(MessageRead::getUserId, userId)
                .in(MessageRead::getMessageId, messageIds);
        long readCount = messageReadMapper.selectCount(readWrapper);

        return messageIds.size() - readCount;
    }

    /**
     * 获取会话最后一条消息
     */
    public Message getLastMessage(String sessionId) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getSessionId, sessionId)
                .eq(Message::getIsDeleted, 0)
                .isNull(Message::getRecallTime)
                .orderByDesc(Message::getCreateTime)
                .last("LIMIT 1");
        return messageMapper.selectOne(wrapper);
    }

    /**
     * 获取用户参与的所有会话ID（去重）
     * 包括消息表中存在的会话，以及用户加入的群组会话
     */
    public List<String> getUserSessionIds(Long userId) {
        // 1. 从消息表中查询用户参与过的会话
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(Message::getSenderId, userId).or().eq(Message::getTargetId, userId))
                .eq(Message::getIsDeleted, 0)
                .select(Message::getSessionId)
                .groupBy(Message::getSessionId);

        List<Message> messages = messageMapper.selectList(wrapper);
        List<String> sessionIds = messages.stream()
                .map(Message::getSessionId)
                .distinct()
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        // 2. 补充用户加入但无消息的群组会话
        List<Long> groupIds = groupMemberMapper.selectGroupIdsByUserId(userId);
        for (Long groupId : groupIds) {
            String groupSessionId = "g_" + groupId;
            if (!sessionIds.contains(groupSessionId)) {
                sessionIds.add(groupSessionId);
            }
        }

        return sessionIds;
    }

    private MessageResponse toResponse(Message message, Map<Long, User> userMap, boolean read) {
        MessageResponse.MessageResponseBuilder builder = MessageResponse.builder()
                .messageId(message.getId())
                .sessionId(message.getSessionId())
                .senderId(message.getSenderId())
                .senderType(SenderType.values()[message.getSenderType()].name())
                .type(MessageType.values()[message.getType()].name())
                .content(message.getContent())
                .extra(message.getExtra())
                .createTime(message.getCreateTime())
                .recalled(message.getRecallTime() != null)
                .read(read);

        // 填充发送者信息
        User sender = userMap.get(message.getSenderId());
        if (sender != null) {
            builder.senderName(sender.getUsername());
            builder.senderAvatar(sender.getAvatar());
        }

        return builder.build();
    }

    private MessageType parseMessageType(String type) {
        try {
            return MessageType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的消息类型: " + type);
        }
    }

    /**
     * 解析 sessionId，提取 targetId 和 targetType
     * 格式：p_{min}_{max}（单聊）、g_{groupId}（群聊）、a_{aiId}_{userId}（AI单聊）
     */
    private SessionInfo parseSessionId(String sessionId) {
        if (sessionId.startsWith("p_")) {
            String[] parts = sessionId.split("_");
            if (parts.length != 3) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的会话ID格式");
            }
            return new SessionInfo(Long.parseLong(parts[2]), TargetType.USER);
        } else if (sessionId.startsWith("g_")) {
            String[] parts = sessionId.split("_");
            if (parts.length != 2) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的会话ID格式");
            }
            return new SessionInfo(Long.parseLong(parts[1]), TargetType.GROUP);
        } else if (sessionId.startsWith("a_")) {
            String[] parts = sessionId.split("_");
            if (parts.length != 3) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的会话ID格式");
            }
            return new SessionInfo(Long.parseLong(parts[1]), TargetType.USER);
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的会话ID格式");
    }

    /**
     * 获取用户在指定 messageId 之后的离线消息（用于断线重连补发）
     *
     * @param userId        用户ID
     * @param lastMessageId 客户端最后收到的消息ID
     * @return 离线消息列表
     */
    public List<MessageResponse> getOfflineMessages(Long userId, Long lastMessageId) {
        // 获取用户参与的所有会话ID
        List<String> sessionIds = getUserSessionIds(userId);

        if (sessionIds.isEmpty()) {
            return List.of();
        }

        // 查询这些会话中 ID 大于 lastMessageId 的消息
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Message::getSessionId, sessionIds)
                .gt(Message::getId, lastMessageId)
                .eq(Message::getIsDeleted, 0)
                .orderByAsc(Message::getCreateTime);

        List<Message> messages = messageMapper.selectList(wrapper);

        if (messages.isEmpty()) {
            return List.of();
        }

        // 批量获取发送者信息
        Set<Long> senderIds = messages.stream()
                .map(Message::getSenderId)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userService.findByIds(senderIds);

        // 批量查询已读状态
        Set<Long> readMessageIds = getReadMessageIds(userId, messages);

        return messages.stream()
                .map(msg -> toResponse(msg, userMap, readMessageIds.contains(msg.getId())))
                .toList();
    }

    /**
     * 根据消息ID列表查找消息发送者（去重）
     *
     * @param messageIds 消息ID列表
     * @return 发送者ID列表（去重）
     */
    public List<Long> findMessageSenders(final List<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return List.of();
        }
        // 使用 SQL: SELECT DISTINCT sender_id FROM message WHERE id IN (...)
        final LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Message::getId, messageIds)
                .select(Message::getSenderId);
        final List<Message> messages = messageMapper.selectList(wrapper);
        return messages.stream()
                .map(Message::getSenderId)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * sessionId 解析结果
     */
    private record SessionInfo(Long targetId, TargetType targetType) {
    }
}
