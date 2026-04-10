package org.apache.roller.weblogger.business.discussion;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.roller.weblogger.pojos.WeblogEntryComment;

public class DiscussionOverviewService {

    private static final int TOP_KEYWORD_LIMIT = 10;
    private static final int MIN_WORD_LENGTH = 3;
    private static final double THRESHOLD_VERY_ACTIVE = 3.0;
    private static final double THRESHOLD_MODERATE = 1.0;

    private static final long MS_PER_DAY = 86_400_000L;

    private static final Pattern QUESTION_START = Pattern.compile(
        "^(?:why|how|what|when|where|who|whose|which)\\b",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern AUX_QUESTION_START = Pattern.compile(
        "^(?:is|are|am|was|were|do|does|did|have|has|had|"
        + "can|could|will|would|shall|should|may|might|must)\\b",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern DEBATE_PATTERN = Pattern.compile(
        "\\b(disagree|disagrees|disagreement|wrong|incorrect|inaccurate|"
        + "misleading|false|not true|actually|but |however|although|"
        + "on the contrary|in contrast|dispute|challenge|counter|"
        + "oppose|opposition|objection|i think you|i believe you|"
        + "that is not|that's not|thats not)\\b",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern APPRECIATION_PATTERN = Pattern.compile(
        "\\b(thank|thanks|thank you|appreciated|appreciate|great|"
        + "excellent|awesome|wonderful|fantastic|amazing|brilliant|"
        + "love this|love it|well done|nice post|nice article|good post|"
        + "good article|helpful|very helpful|enjoyed|enjoy|impressive|"
        + "good job|great job|kudos|bravo|perfect|best post)\\b",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern FEEDBACK_PATTERN = Pattern.compile(
        "\\b(suggest|suggestion|recommend|recommendation|improve|"
        + "improvement|maybe|perhaps|should|could|would be better|"
        + "might want|consider|alternatively|one thing|one issue|"
        + "missing|lacks|lack|needs|need to|please add|please include|"
        + "it would be|it would help|i wish|i hope|feature request)\\b",
        Pattern.CASE_INSENSITIVE);

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
        "the","and","for","that","this","with","are","was","were","have",
        "has","had","not","but","from","they","their","there","then",
        "than","the","those","these","what","which","who","whom","when",
        "where","why","how","all","any","can","could","should","would",
        "will","may","might","must","shall","its","his","her","our",
        "your","you","him","she","just","also","into","out","more",
        "very","some","such","only","been","even","back","over","about",
        "get","got","one","two","let","did","does","too","each","able",
        "own","both","after","before","during","while","though","through",
        "off","here","like","well","now","still","yet","much","many",
        "most","need","said","same","seem","seems","far","new","big",
        "way","use","make","made","come","came","see","saw","say","says",
        "take","put","give","keep","want","work","find","look","think",
        "know","next","year","day","time","post","blog","write","read",
        "article","author","wrote","page","site","content","http","www",
        "com","net","org","really","actually","already","always","often",
        "never","other","another","every","thing","things","people",
        "person","place","right","left","since","because","however",
        "although","though","while","instead","around","toward",
        "whether","either","neither","else","anything","something",
        "nothing","everything","everyone","someone","anyone","bit",
        "going","went","been","being","done","doing","having","making",
        "getting","putting","taking","coming","seeing","thinking", "etc"
    ));

    /**
     * Compute a {@link DiscussionOverview} for the supplied list of comments.
     * @param comments  the full comment list for an entry (may be empty or null)
     * @return          a populated {@code DiscussionOverview}; never null
     */
    public static DiscussionOverview compute(List<WeblogEntryComment> comments) {

        DiscussionOverview overview = new DiscussionOverview();

        if (comments == null || comments.isEmpty()) {
            return overview;
        }

        List<WeblogEntryComment> eligible = new ArrayList<>();
        for (WeblogEntryComment c : comments) {
            if ((WeblogEntryComment.ApprovalStatus.APPROVED.equals(c.getStatus()) ||
                 WeblogEntryComment.ApprovalStatus.PENDING.equals(c.getStatus()))
                    && c.getContent() != null && !c.getContent().trim().isEmpty()) {
                eligible.add(c);
            }
        }

        if (eligible.isEmpty()) {
            return overview;
        }

        computeActivityLevel(overview, eligible);
        computeTypeDistribution(overview, eligible);
        computeTopKeywords(overview, eligible);

        return overview;
    }

    private static void computeActivityLevel(DiscussionOverview overview,
                                             List<WeblogEntryComment> eligible) {

        int total = eligible.size();
        overview.setTotalComments(total);

        // Find earliest and latest post times
        Timestamp earliest = null;
        Timestamp latest   = null;
        for (WeblogEntryComment c : eligible) {
            Timestamp t = c.getPostTime();
            if (t == null) { continue; }
            if (earliest == null || t.before(earliest)) { earliest = t; }
            if (latest   == null || t.after(latest))    { latest   = t; }
        }

        double avgPerDay = 0.0;
        if (earliest != null && latest != null) {
            long spanMs  = latest.getTime() - earliest.getTime();
            // guard against division by zero by treating span as at least 1 day.
            double spanDays = Math.max(1.0, (double) spanMs / MS_PER_DAY);
            avgPerDay = total / spanDays;
        }

        overview.setAvgCommentsPerDay(
                Math.round(avgPerDay * 100.0) / 100.0);  // 2 d.p.

        String label;
        if      (avgPerDay >= THRESHOLD_VERY_ACTIVE) { label = "Very Active"; }
        else if (avgPerDay >= THRESHOLD_MODERATE)    { label = "Moderately Active"; }
        else                                          { label = "Quiet"; }

        overview.setActivityLabel(label);
    }

    private static void computeTypeDistribution(DiscussionOverview overview, List<WeblogEntryComment> eligible) {

        Map<String, Integer> dist = new LinkedHashMap<>();
        dist.put("Question",     0);
        dist.put("Debate",       0);
        dist.put("Appreciation", 0);
        dist.put("Feedback",     0);
        dist.put("Neutral",      0);

        for (WeblogEntryComment c : eligible) {
            String text = c.getContent().toLowerCase(Locale.ENGLISH);
            String type = classifyComment(text);
            dist.put(type, dist.get(type) + 1);
        }

        overview.setTypeDistribution(dist);

        // Determine dominant type (type with highest count)
        String dominant = "Neutral";
        int    max      = -1;
        for (Map.Entry<String, Integer> e : dist.entrySet()) {
            if (e.getValue() > max) {
                max      = e.getValue();
                dominant = e.getKey();
            }
        }
        overview.setDominantType(dominant);
    }

    public static boolean isQuestion(String text) {
        text = text.trim();

        if (text.contains("?")) {
            return true;
        }

        if (QUESTION_START.matcher(text).find()) {
            return true;
        }

        if (AUX_QUESTION_START.matcher(text).find()) {
            return true;
        }

        return false;
    }

    private static String classifyComment(String lowerText) {
        if (isQuestion(lowerText)) { return "Question"; }
        if (DEBATE_PATTERN.matcher(lowerText).find())        { return "Debate"; }
        if (APPRECIATION_PATTERN.matcher(lowerText).find())  { return "Appreciation"; }
        if (FEEDBACK_PATTERN.matcher(lowerText).find())      { return "Feedback"; }
        return "Neutral";
    }

    private static void computeTopKeywords(DiscussionOverview overview,
                                           List<WeblogEntryComment> eligible) {

        Map<String, Integer> freq = new HashMap<>();

        for (WeblogEntryComment c : eligible) {
            // strip HTML tags and non-alphabetic characters, lower-case
            String text = c.getContent()
                    .replaceAll("<[^>]*>", " ")          // strip HTML
                    .replaceAll("[^a-zA-Z\\s]", " ")     // keep only letters
                    .toLowerCase(Locale.ENGLISH);

            for (String token : text.split("\\s+")) {
                if (token.length() < MIN_WORD_LENGTH) { continue; }
                if (STOP_WORDS.contains(token))       { continue; }
                freq.merge(token, 1, Integer::sum);
            }
        }

        // Sort by frequency descending, then alphabetically for stable output
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(freq.entrySet());
        sorted.sort(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry.comparingByKey()));

        int limit = Math.min(TOP_KEYWORD_LIMIT, sorted.size());
        overview.setTopKeywords(new ArrayList<>(sorted.subList(0, limit)));
    }
}
