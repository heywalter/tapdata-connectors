package io.tapdata.connector.starrocks.streamload.exception;

/**
 * @Author dayun
 * @Date 7/14/22
 */
public class StreamLoadException extends Exception {
    public StreamLoadException() {
        super();
    }

    public StreamLoadException(String message) {
        super(message);
    }

    public StreamLoadException(String message, Throwable cause) {
        super(message, cause);
    }

    public StreamLoadException(Throwable cause) {
        super(cause);
    }

    protected StreamLoadException(String message, Throwable cause,
                                  boolean enableSuppression,
                                  boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
