package org.apache.roller.weblogger.business.feed.steps;

import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.business.feed.FeedProcessingStep;
import org.apache.roller.weblogger.config.WebloggerConfig;
import org.apache.roller.weblogger.pojos.WeblogEntry;

public class WordCountLimiterStep implements FeedProcessingStep {

    private static final Log log = LogFactory.getLog(WordCountLimiterStep.class);

    private static final int DEFAULT_MAX_WORDS = 500;
    @Override
    public String getName() {
        return "WordCountLimiter";
    }

    @Override
    public boolean isEnabled() {
        return WebloggerConfig.getBooleanProperty("feed.pipeline.wordCountLimiter.enabled");
    }

    @Override
    public WeblogEntry process(WeblogEntry entry) {
        int maxWords = getMaxWords();
        String body = entry.getText();
        if (body == null || body.isEmpty()) {
            return entry;
        }

        String plain = body.replaceAll("<[^>]+>", " ").trim();
        String[] tokens = plain.split("\\s+");
        int wordCount = tokens.length;

        String existingSummary = entry.getSummary();
        boolean summaryEmpty = existingSummary == null || existingSummary.trim().isEmpty();

        if (wordCount > maxWords) {
            String[] rawTokens = body.trim().split("\\s+");
            int cut = Math.min(maxWords, rawTokens.length);
            String truncated = String.join(" ", Arrays.copyOfRange(rawTokens, 0, cut));
            String overflow  = String.join(" ", Arrays.copyOfRange(rawTokens, cut, rawTokens.length)).trim();
            entry.setText(truncated);
            if (summaryEmpty && !overflow.isEmpty()) {
                entry.setSummary("[Auto-truncated - original continuation:]\n" + overflow);
            }
            log.debug("WordCountLimiterStep truncated entry " + entry.getId()
                    + " from " + wordCount + " to " + maxWords + " words.");
            return entry;
        }

        return entry;
    }

    private int getMaxWords() {
        String raw = WebloggerConfig.getProperty("feed.pipeline.wordCountLimiter.maxWords");
        if (raw == null || raw.trim().isEmpty()) {
            return DEFAULT_MAX_WORDS;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            return v > 0 ? v : DEFAULT_MAX_WORDS;
        } catch (NumberFormatException e) {
            log.warn("Invalid feed.pipeline.wordCountLimiter.maxWords value '" + raw
                    + "', using default " + DEFAULT_MAX_WORDS);
            return DEFAULT_MAX_WORDS;
        }
    }
}