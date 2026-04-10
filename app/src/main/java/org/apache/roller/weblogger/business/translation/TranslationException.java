package org.apache.roller.weblogger.business.translation;

/**
 * Thrown when a translation provider encounters an error during a translate
 * or language-detection call.
 *
 * <p>Wraps the root cause so callers can log provider-specific detail without
 * coupling to a particular HTTP or IO library.</p>
 */
public class TranslationException extends Exception {

    private static final long serialVersionUID = 1L;

    /** HTTP status code returned by the remote provider, or -1 if n/a. */
    private final int httpStatus;

    public TranslationException(String message) {
        super(message);
        this.httpStatus = -1;
    }

    public TranslationException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = -1;
    }

    public TranslationException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public TranslationException(String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    /**
     * Returns the HTTP status code from the remote provider response, or
     * {@code -1} when the error was not an HTTP-level failure.
     */
    public int getHttpStatus() {
        return httpStatus;
    }
}
