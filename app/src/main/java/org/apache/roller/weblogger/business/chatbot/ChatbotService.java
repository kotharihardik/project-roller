package org.apache.roller.weblogger.business.chatbot;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;
import java.util.Map;

public class ChatbotService {

    private static final Log LOG = LogFactory.getLog(ChatbotService.class);

    // Singleton
    private static volatile ChatbotService INSTANCE;

    private ChatbotService() { }

    public static ChatbotService getInstance() {
        if (INSTANCE == null) {
            synchronized (ChatbotService.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ChatbotService();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Answers a user question about a weblog using the specified strategy.
     *
     * @param weblogId   the weblog ID
     * @param question   the user's question
     * @param strategy   strategy name (null = use default)
     * @return a {@link ChatbotResponse} with the answer
     */
    public ChatbotResponse askQuestion(String weblogId, String question, String strategy)
            throws ChatbotException {
        return askQuestion(weblogId, question, strategy, null);
    }

    /**
     * Answers a user question with optional entry-level scope.
     *
     * @param weblogId      the weblog ID
     * @param question      the user's question
     * @param strategy      strategy name (null = use default)
     * @param entryIdScope  optional entry ID to scope retrieval/context
     * @return a {@link ChatbotResponse} with the answer
     */
    public ChatbotResponse askQuestion(String weblogId, String question, String strategy,
                                       String entryIdScope)
            throws ChatbotException {
        ChatbotAnsweringStrategy impl = ChatbotStrategyFactory.getStrategy(strategy);
        LOG.info("Chatbot answering with strategy: " + impl.getStrategyName() +
                 " for weblog: " + weblogId);
        return impl.answer(weblogId, question, entryIdScope);
    }

    /**
     * Returns metadata about all available strategies for the UI.
     */
    public List<Map<String, String>> getAvailableStrategies() {
        return ChatbotStrategyFactory.getAvailableStrategies();
    }
}
