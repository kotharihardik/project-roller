package org.apache.roller.weblogger.business.feed.steps;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.feed.FeedProcessingStep;
import org.apache.roller.weblogger.config.WebloggerConfig;
import org.apache.roller.weblogger.pojos.WeblogEntry;

public class AutoTagGenerationStep implements FeedProcessingStep {

    private static final Log log = LogFactory.getLog(AutoTagGenerationStep.class);

    private static final int DEFAULT_MAX_TAGS = 5;
    private static final int MIN_WORD_LENGTH  = 4;

    private static final Set<String> STOPWORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "the","and","for","that","this","with","from","have","are","was",
        "were","been","has","had","not","but","they","their","them","will",
        "would","could","should","about","into","over","more","also","when",
        "than","then","some","what","your","just","each","which","there",
        "these","those","such","only","both","very","after","before","while",
        "under","other","first","last","does","here","http","https","www",
        "i", "me", "my", "myself", "we", "our", "ours", "ourselves", "you", 
        "yours", "yourself", "yourselves", "he", "him", "his", "himself", 
        "she", "her", "hers", "herself", "it", "its", "itself", "who", 
        "whom", "whose", "whose", "which", "that", "these", "those", "am", 
        "is", "be", "being", "do", "did", "doing", "an", "a", "if", "or", 
        "because", "as", "until", "while", "of", "at", "by", "up", "down", 
        "in", "out", "on", "off", "again", "further", "once", "too", "can"
    )));

    @Override
    public String getName() {
        return "AutoTagGeneration";
    }

    @Override
    public boolean isEnabled() {
        return WebloggerConfig.getBooleanProperty("feed.pipeline.autoTag.enabled");
    }

    @Override
    public WeblogEntry process(WeblogEntry entry) {
        int maxTags = getMaxTags();
        String body = entry.getText();
        if (body == null || body.trim().isEmpty()) {
            return entry;
        }

        List<String> newTags = extractTopWords(body, maxTags);
        if (newTags.isEmpty()) {
            return entry;
        }

        String existing = entry.getTagsAsString();
        Set<String> existingLower = new HashSet<>();
        if (existing != null && !existing.trim().isEmpty()) {
            for (String t : existing.trim().split("\\s+")) {
                existingLower.add(t.toLowerCase());
            }
        }

        StringBuilder appended = new StringBuilder(existing == null ? "" : existing.trim());
        int added = 0;
        for (String tag : newTags) {
            if (!existingLower.contains(tag.toLowerCase())) {
                if (appended.length() > 0) {
                    appended.append(' ');
                }
                appended.append(tag);
                added++;
            }
        }

        if (added > 0) {
            try {
                entry.setTagsAsString(appended.toString().trim());
                log.debug("AutoTagGenerationStep added " + added + " tag(s) to entry " + entry.getId()
                        + ": " + newTags);
            } catch (WebloggerException e) {
                log.warn("AutoTagGenerationStep: could not set tags on entry " + entry.getId()
                        + " — " + e.getMessage());
            }
        }
        return entry;
    }

    /**
     * Strip HTML tags, tokenise, remove stopwords, count frequencies, return
     * top-{@code max} words sorted by descending frequency.
     */
    private List<String> extractTopWords(String html, int max) {
        
        String text = html.replaceAll("<[^>]+>", " ");

        String[] tokens = text.toLowerCase().split("[^a-z]+");

        Map<String, Integer> freq = new HashMap<>();
        for (String token : tokens) {
            if (token.length() >= MIN_WORD_LENGTH && !STOPWORDS.contains(token)) {
                freq.merge(token, 1, Integer::sum);
            }
        }

        return freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(max)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private int getMaxTags() {
        String raw = WebloggerConfig.getProperty("feed.pipeline.autoTag.maxTags");
        if (raw == null || raw.trim().isEmpty()) {
            return DEFAULT_MAX_TAGS;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            return v > 0 ? v : DEFAULT_MAX_TAGS;
        } catch (NumberFormatException e) {
            log.warn("Invalid feed.pipeline.autoTag.maxTags value '" + raw
                    + "', using default " + DEFAULT_MAX_TAGS);
            return DEFAULT_MAX_TAGS;
        }
    }
}
