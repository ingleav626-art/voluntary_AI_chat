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
import com.voluntary.chat.server.dto.response.SendMessageResponse;
import com.voluntary.chat.server.entity.Message;
import com.voluntary.chat.server.entity.MessageRead;
import com.voluntary.chat.server.entity.User;
import com.voluntary.chat.server.mapper.GroupMemberMapper;
import com.voluntary.chat.server.mapper.MessageMapper;
import com.voluntary.chat.server.mapper.MessageReadMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getSessionId, sessionId)
                .eq(Message::getIsDeleted, 0)
                .orderByDesc(Message::getCreateTime);

        long total = messageMapper.selectCount(wrapper);
        int offset = (page - 1) * size;
        List<Message> messages = messageMapper.selectList(wrapper.last("LIMIT " + offset + ", " + size));

        // 批量获取发送者信息，避免循环查询
        Set<Long> senderIds = messages.stream()
                .map(Message::getSenderId)
                .filter(id -> SenderType.USER.ordinal() == messages.stream()
                        .filter(m -> m.getSenderId().equals(id))
                        .findFirst().map(Message::getSenderType).orElse(-1))
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userService.findByIds(senderIds);

        List<MessageResponse> list = messages.stream()
                .map(msg -> toResponse(msg, userMap))
                .toList();

        return PageResult.of(list, total, page, size);
    }

    /**
     * 撤回消息
     * 人-人消息：2分钟内可撤回；AI消息：随时可撤回；
     * 群消息：仅群主和管理员可撤回他人消息，普通成员只能撤回自己的消息
     */
    @Transactional
    public void recallMessage(Long userId, Long messageId) {
        Message message = messageMapper.selectById(messageId);
        if (message == null || message.getIsDeleted() == 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "消息不存在");
        }

        // AI消息可随时撤回
        if (message.getSenderType() == SenderType.AI.ordinal()) {
            message.setRecallTime(LocalDateTime.now());
            messageMapper.updateById(message);
            return;
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
    }

    /**
     * 标记消息已读
     * 批量插入已读记录，忽略重复（利用唯一索引）
     */
    @Transactional
    public void markRead(Long userId, MarkReadRequest request) {
        for (String messageIdStr : request.getMessageIds()) {
            Long messageId = Long.parseLong(messageIdStr);
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

    private MessageResponse toResponse(Message message, Map<Long, User> userMap) {
        MessageResponse.MessageResponseBuilder builder = MessageResponse.builder()
                .messageId(message.getId())
                .sessionId(message.getSessionId())
                .senderId(message.getSenderId())
                .senderType(SenderType.values()[message.getSenderType()].name())
                .type(MessageType.values()[message.getType()].name())
                .content(message.getContent())
                .extra(message.getExtra())
                .createTime(message.getCreateTime())
                .recalled(message.getRecallTime() != null);

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
     * sessionId 解析结果
     */
    private record SessionInfo(Long targetId, TargetType targetType) {
    }
}
