package org.apache.roller.weblogger.business.chatbot;

/**
 * Thrown when the chatbot subsystem encounters an unrecoverable error.
 */
public class ChatbotException extends Exception {

    private static final long serialVersionUID = 1L;

    public ChatbotException(String message) {
        super(message);
    }

    public ChatbotException(String message, Throwable cause) {
        super(message, cause);
    }
}
