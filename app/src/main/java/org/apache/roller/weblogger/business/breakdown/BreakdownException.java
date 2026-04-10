package org.apache.roller.weblogger.business.breakdown;

public class BreakdownException extends Exception {

    private static final long serialVersionUID = 1L;

    /** HTTP status code from a remote API, or {@code -1} if not applicable. */
    private final int statusCode;

    public BreakdownException(String message) {
        super(message);
        this.statusCode = -1;
    }

    public BreakdownException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    public BreakdownException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
