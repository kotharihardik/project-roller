package org.apache.roller.weblogger.business.chatbot;

/**
 * Immutable value object carrying a chatbot answer together with metadata
 * about how it was produced.
 */
public class ChatbotResponse {

    private final String answer;
    private final String strategyUsed;
    private final int entriesConsulted;
    private final long latencyMs;

    public ChatbotResponse(String answer, String strategyUsed,
                           int entriesConsulted, long latencyMs) {
        this.answer = answer;
        this.strategyUsed = strategyUsed;
        this.entriesConsulted = entriesConsulted;
        this.latencyMs = latencyMs;
    }

    public String getAnswer() {
        return answer;
    }

    public String getStrategyUsed() {
        return strategyUsed;
    }

    public int getEntriesConsulted() {
        return entriesConsulted;
    }

    public long getLatencyMs() {
        return latencyMs;
    }
}
