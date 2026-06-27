package com.voluntary.chat.server.websocket;

/**
 * AI 流式推送接口
 *
 * <p>
 * AiChatService 通过此接口发送 AI 流式回复，不依赖具体的 WebSocketHandler 实现。
 * 客户包和云端包各自实现此接口。
 * </p>
 */
public interface AiStreamSender {

    /**
     * 推送 AI 流式回复（中间块或结束块）
     *
     * @param userId    目标用户 ID
     * @param messageId 消息 ID（用于前端匹配）
     * @param content   回复内容（可能是部分内容或完整内容）
     * @param done      是否完成
     */
    void sendAiStream(Long userId, String messageId, String content, boolean done);

    /**
     * 推送 AI 流式回复（完成块，带 AI 消息 ID）
     *
     * @param userId      目标用户 ID
     * @param messageId   消息 ID
     * @param content     完整回复内容
     * @param done        是否完成
     * @param aiMessageId AI 消息 ID（用于后续查询）
     */
    void sendAiStream(Long userId, String messageId, String content, boolean done, Long aiMessageId);
}
