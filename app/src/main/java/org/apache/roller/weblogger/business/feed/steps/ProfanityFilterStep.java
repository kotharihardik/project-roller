package org.apache.roller.weblogger.business.feed.steps;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.business.feed.FeedProcessingStep;
import org.apache.roller.weblogger.config.WebloggerConfig;
import org.apache.roller.weblogger.pojos.WeblogEntry;

public class ProfanityFilterStep implements FeedProcessingStep {

    private static final Log log = LogFactory.getLog(ProfanityFilterStep.class);

    private static final String REPLACEMENT = "***";

    @Override
    public String getName() {
        return "ProfanityFilter";
    }

    @Override
    public boolean isEnabled() {
        return WebloggerConfig.getBooleanProperty("feed.pipeline.profanityFilter.enabled");
    }

    @Override
    public WeblogEntry process(WeblogEntry entry) {
        Set<String> words = buildWordSet();
        if (words.isEmpty()) {
            return entry;
        }

        if (entry.getText() != null) {
            entry.setText(censor(entry.getText(), words));
        }
        if (entry.getSummary() != null) {
            entry.setSummary(censor(entry.getSummary(), words));
        }
        if (entry.getTitle() != null) {
            entry.setTitle(censor(entry.getTitle(), words));
        }

        log.debug("ProfanityFilterStep processed entry: " + entry.getId());
        return entry;
    }

    private Set<String> buildWordSet() {
        String raw = WebloggerConfig.getProperty("feed.pipeline.profanityFilter.wordlist", "");
        Set<String> words = new HashSet<>();
        if (raw == null || raw.trim().isEmpty()) {
            return words;
        }
        for (String word : raw.split(",")) {
            String trimmed = word.trim();
            if (!trimmed.isEmpty()) {
                words.add(trimmed.toLowerCase());
            }
        }
        return words;
    }

    private String censor(String text, Set<String> words) {
        String result = text;
        for (String word : words) {
            Pattern p = Pattern.compile("(?i)\\b" + Pattern.quote(word) + "\\b");
            result = p.matcher(result).replaceAll(REPLACEMENT);
        }
        return result;
    }
}
