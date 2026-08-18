package online.yudream.base.application.platform.chat.support;

/**
 * Stops chat generation when the streaming transport is no longer available.
 */
public final class ChatStreamCancelledException extends RuntimeException {

    public ChatStreamCancelledException() {
        super("Chat stream cancelled", null, false, false);
    }

    public ChatStreamCancelledException(Throwable cause) {
        super("Chat stream cancelled", cause, false, false);
    }
}
