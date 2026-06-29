package org.example.client.engine;

/**
 * AI 流式对话回调接口
 *
 * <p>ViewModel 实现此接口以接收 AI 对话的流式输出。</p>
 */
public interface AiStreamCallback {

    /**
     * 收到一个文本块
     *
     * @param chunk 文本块内容
     */
    void onChunk(String chunk);

    /**
     * 对话完成
     *
     * @param fullContent 完整回复内容
     * @param messageId   消息ID（AI消息），可为 null
     */
    void onComplete(String fullContent, Long messageId);

    /**
     * 对话出错
     *
     * @param error 错误信息
     */
    void onError(String error);
}
