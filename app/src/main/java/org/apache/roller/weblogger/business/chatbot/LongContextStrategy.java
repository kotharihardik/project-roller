package org.apache.roller.weblogger.business.chatbot;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.pojos.WeblogEntry;

import java.util.List;


public class LongContextStrategy extends AbstractLlmStrategy {

    private static final Log LOG = LogFactory.getLog(LongContextStrategy.class);

    private static final String STRATEGY_NAME  = "longcontext";
    private static final String DISPLAY_NAME   = "Long Context (Full Content)";

    private static final String PROP_MAX_CHARS = "chatbot.longcontext.maxChars";
    private static final int    DEFAULT_MAX_CHARS = 800_000;

    private static final String SYSTEM_INSTRUCTION =
            "You are a helpful Q&A assistant for a weblog. " +
            "Answer the user's question ONLY based on the blog content provided below. " +
            "If the answer cannot be found in the provided content, say so clearly. " +
            "Be concise, accurate, and cite specific blog post titles when relevant. " +
            "Do NOT make up information that isn't in the provided content.";

    private final int maxChars;

    public LongContextStrategy() {
        super();
        this.maxChars = intConfig(PROP_MAX_CHARS, DEFAULT_MAX_CHARS);
    }

    @Override
    public String getStrategyName() {
        return STRATEGY_NAME;
    }

    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    public ChatbotResponse answer(String weblogId, String question) throws ChatbotException {
        return answer(weblogId, question, null);
    }

    @Override
    public ChatbotResponse answer(String weblogId, String question, String entryIdScope)
            throws ChatbotException {
        long start = System.currentTimeMillis();
        try {
            List<WeblogEntry> entries = fetchPublishedEntries(weblogId, entryIdScope);
            if (entries.isEmpty()) {
                return new ChatbotResponse(
                        "This weblog has no published entries yet, so I can't answer questions about it.",
                        STRATEGY_NAME, 0, System.currentTimeMillis() - start);
            }

            String context = buildContext(entries);
            String answer = callLlmApi(context, question, SYSTEM_INSTRUCTION,
                    "Here is the complete weblog content");

            return new ChatbotResponse(answer, STRATEGY_NAME, entries.size(),
                    System.currentTimeMillis() - start);

        } catch (WebloggerException e) {
            throw new ChatbotException("Failed to fetch weblog entries: " + e.getMessage(), e);
        }
    }

    private String buildContext(List<WeblogEntry> entries) {
        StringBuilder sb = new StringBuilder();
        for (WeblogEntry entry : entries) {
            if (sb.length() >= maxChars) break;

            sb.append("=== Blog Post: ").append(entry.getTitle()).append(" ===\n");
            if (entry.getPubTime() != null) {
                sb.append("Published: ").append(entry.getPubTime()).append("\n");
            }
            String body = stripHtml(entry.getText());
            if (body != null) {
                sb.append(body).append("\n\n");
            }
        }

        if (sb.length() > maxChars) {
            sb.setLength(maxChars);
        }
        return sb.toString();
    }
}
