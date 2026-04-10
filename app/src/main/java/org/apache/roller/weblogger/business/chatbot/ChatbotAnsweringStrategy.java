package org.apache.roller.weblogger.business.chatbot;

public interface ChatbotAnsweringStrategy {

    /**
     * Returns the unique name of this strategy (e.g. "longcontext", "rag").
     */
    String getStrategyName();

    /**
     * Returns a human-friendly display name for UI dropdowns.
     */
    String getDisplayName();

    /**
     * Answers a natural-language question grounded in the given weblog's
     * published entries.
     *
     * @param weblogId   the ID of the weblog whose entries form the knowledge base
     * @param question   the user's natural-language question
     * @return a {@link ChatbotResponse} containing the answer and metadata
     * @throws ChatbotException if the strategy fails to produce an answer
     */
    ChatbotResponse answer(String weblogId, String question) throws ChatbotException;

    /**
     * Answers a question with optional entry-level scope.
     *
     * <p>When {@code entryIdScope} is provided, implementations may restrict
     * retrieval/context to that entry (or an implementation-defined subset around
     * it) instead of using the full weblog corpus.</p>
     *
     * <p>Default behavior preserves backward compatibility and delegates to
     * {@link #answer(String, String)}.</p>
     */
    default ChatbotResponse answer(String weblogId, String question, String entryIdScope)
            throws ChatbotException {
        return answer(weblogId, question);
    }
}
