package org.apache.roller.weblogger.business.breakdown;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.roller.weblogger.business.discussion.DiscussionOverview;
import org.apache.roller.weblogger.business.discussion.DiscussionOverviewService;
import org.apache.roller.weblogger.pojos.WeblogEntryComment;

public class KeywordClusterStrategy implements BreakdownStrategy {

    // Constants
    private static final String STRATEGY_NAME  = "keyword";
    private static final int    MAX_THEMES      = 4;
    private static final int    MAX_REPS        = 2;
    private static final int    THEME_LABEL_TERMS = 3;
    private static final int    MIN_WORD_LEN    = 3;
    private static final Pattern PUNCT = Pattern.compile("[^a-z0-9\\s]");

    // Shared stop-word set (mirrors the one in DiscussionOverviewService)
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
        "the","and","for","that","this","with","are","was","were","have",
        "has","had","not","but","from","they","their","there","then",
        "than","those","these","what","which","who","whom","when",
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
        "getting","putting","taking","coming","seeing","thinking","etc"
    ));

    // BreakdownStrategy
    @Override
    public String getStrategyName() {
        return STRATEGY_NAME;
    }

    @Override
    public ConversationBreakdown generate(List<WeblogEntryComment> comments)
            throws BreakdownException {

        List<WeblogEntryComment> eligible = filterEligible(comments);

        if (eligible.isEmpty()) {
            return new ConversationBreakdown(
                    Collections.emptyList(),
                    "No comments to analyse.",
                    STRATEGY_NAME);
        }

        if (eligible.size() < 3) {
            // Too few to cluster; single theme with all comments
            List<String> reps = buildExcerpts(eligible);
            Theme t = new Theme("General Discussion", reps);
            String recap = buildRecap(comments, eligible.size());
            return new ConversationBreakdown(
                    Collections.singletonList(t), recap, STRATEGY_NAME);
        }

        // 1. Tokenise
        List<List<String>> tokenised = new ArrayList<>(eligible.size());
        for (WeblogEntryComment c : eligible) {
            tokenised.add(tokenise(c.getContent()));
        }

        // 2. TF-IDF
        Map<String, Double> idf = computeIdf(tokenised);
        List<Map<String, Double>> tfidfVectors = new ArrayList<>(eligible.size());
        for (List<String> tokens : tokenised) {
            tfidfVectors.add(computeTfIdf(tokens, idf));
        }

        // 3. Seed selection
        int k = Math.min(MAX_THEMES, eligible.size());
        List<Integer> seedIndices = selectSeeds(tfidfVectors, k);

        // 4. Assign each comment to nearest seed
        Map<Integer, List<Integer>> clusters = new LinkedHashMap<>();
        for (int s : seedIndices) { clusters.put(s, new ArrayList<>()); }
        for (int i = 0; i < eligible.size(); i++) {
            int best = seedIndices.get(0);
            double bestSim = -1.0;
            for (int s : seedIndices) {
                double sim = cosineSim(tfidfVectors.get(i), tfidfVectors.get(s));
                if (sim > bestSim) { bestSim = sim; best = s; }
            }
            clusters.get(best).add(i);
        }

        // 5 & 6. Build themes
        List<Theme> themes = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> entry : clusters.entrySet()) {
            List<Integer> memberIndices = entry.getValue();
            if (memberIndices.isEmpty()) continue;

            // Aggregate TF-IDF across cluster to find top terms
            Map<String, Double> clusterVector = new HashMap<>();
            for (int idx : memberIndices) {
                for (Map.Entry<String, Double> e : tfidfVectors.get(idx).entrySet()) {
                    clusterVector.merge(e.getKey(), e.getValue(), Double::sum);
                }
            }

            String label = buildThemeLabel(clusterVector, idf);

            // Representatives: members with highest total TF-IDF weight
            memberIndices.sort((a, b) -> {
                double scoreA = sumValues(tfidfVectors.get(a));
                double scoreB = sumValues(tfidfVectors.get(b));
                return Double.compare(scoreB, scoreA);
            });

            List<String> reps = new ArrayList<>();
            for (int j = 0; j < Math.min(MAX_REPS, memberIndices.size()); j++) {
                reps.add(excerpt(eligible.get(memberIndices.get(j)).getContent()));
            }

            themes.add(new Theme(label, reps));
        }

        // 7. Recap
        String recap = buildRecap(comments, eligible.size());
        return new ConversationBreakdown(themes, recap, STRATEGY_NAME);
    }

    // Private helpers
    /** Keeps only approved/pending comments with non-empty content. */
    private static List<WeblogEntryComment> filterEligible(
            List<WeblogEntryComment> comments) {
        if (comments == null) return Collections.emptyList();
        List<WeblogEntryComment> out = new ArrayList<>();
        for (WeblogEntryComment c : comments) {
            if ((WeblogEntryComment.ApprovalStatus.APPROVED.equals(c.getStatus()) ||
                 WeblogEntryComment.ApprovalStatus.PENDING.equals(c.getStatus()))
                    && c.getContent() != null
                    && !c.getContent().trim().isEmpty()) {
                out.add(c);
            }
        }
        return out;
    }

    /** Lowercase, strip punctuation, split on whitespace, remove stop-words. */
    private static List<String> tokenise(String text) {
        if (text == null) return Collections.emptyList();
        String clean = PUNCT.matcher(text.toLowerCase(Locale.ENGLISH)).replaceAll(" ");
        List<String> tokens = new ArrayList<>();
        for (String w : clean.split("\\s+")) {
            if (w.length() >= MIN_WORD_LEN && !STOP_WORDS.contains(w)) {
                tokens.add(w);
            }
        }
        return tokens;
    }

    /** Computes log(N / df) for every term appearing in at least one document. */
    private static Map<String, Double> computeIdf(List<List<String>> docs) {
        Map<String, Integer> df = new HashMap<>();
        for (List<String> doc : docs) {
            Set<String> seen = new HashSet<>(doc);
            for (String w : seen) {
                df.merge(w, 1, Integer::sum);
            }
        }
        int N = docs.size();
        Map<String, Double> idf = new HashMap<>();
        for (Map.Entry<String, Integer> e : df.entrySet()) {
            idf.put(e.getKey(), Math.log((double) N / e.getValue()));
        }
        return idf;
    }

    /** TF (raw count / doc length) × IDF for every unique term in the doc. */
    private static Map<String, Double> computeTfIdf(List<String> tokens,
                                                     Map<String, Double> idf) {
        if (tokens.isEmpty()) return Collections.emptyMap();
        Map<String, Integer> tf = new HashMap<>();
        for (String w : tokens) tf.merge(w, 1, Integer::sum);
        Map<String, Double> vec = new HashMap<>();
        double len = tokens.size();
        for (Map.Entry<String, Integer> e : tf.entrySet()) {
            double idfVal = idf.getOrDefault(e.getKey(), 0.0);
            vec.put(e.getKey(), (e.getValue() / len) * idfVal);
        }
        return vec;
    }

    private static List<Integer> selectSeeds(List<Map<String, Double>> vecs, int k) {
        List<Integer> seeds = new ArrayList<>();
        // First seed: highest total weight (most "rich" in keywords)
        int first = 0;
        double bestScore = -1;
        for (int i = 0; i < vecs.size(); i++) {
            double s = sumValues(vecs.get(i));
            if (s > bestScore) { bestScore = s; first = i; }
        }
        seeds.add(first);

        while (seeds.size() < k) {
            double minMaxSim = Double.MAX_VALUE;
            int next = -1;
            for (int i = 0; i < vecs.size(); i++) {
                if (seeds.contains(i)) continue;
                double maxSim = 0;
                for (int s : seeds) {
                    double sim = cosineSim(vecs.get(i), vecs.get(s));
                    if (sim > maxSim) maxSim = sim;
                }
                if (maxSim < minMaxSim) { minMaxSim = maxSim; next = i; }
            }
            if (next == -1) break;
            seeds.add(next);
        }
        return seeds;
    }

    /** Cosine similarity between two sparse TF-IDF vectors. */
    private static double cosineSim(Map<String, Double> a, Map<String, Double> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        double dot = 0, normA = 0, normB = 0;
        for (Map.Entry<String, Double> e : a.entrySet()) {
            dot   += e.getValue() * b.getOrDefault(e.getKey(), 0.0);
            normA += e.getValue() * e.getValue();
        }
        for (double v : b.values()) normB += v * v;
        if (normA == 0 || normB == 0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /** Sum of all values in a map (used as a rough "richness" score). */
    private static double sumValues(Map<String, Double> vec) {
        double s = 0;
        for (double v : vec.values()) s += v;
        return s;
    }

    private static String buildThemeLabel(Map<String, Double> clusterVec,
                                          Map<String, Double> idf) {
        // Prefer terms with high IDF (discriminating) AND high cluster weight
        List<Map.Entry<String, Double>> entries = new ArrayList<>(clusterVec.entrySet());
        entries.sort((a, b) -> {
            double scoreA = a.getValue() * idf.getOrDefault(a.getKey(), 0.0);
            double scoreB = b.getValue() * idf.getOrDefault(b.getKey(), 0.0);
            return Double.compare(scoreB, scoreA);
        });
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, Double> e : entries) {
            if (count >= THEME_LABEL_TERMS) break;
            if (sb.length() > 0) sb.append(", ");
            String w = e.getKey();
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            count++;
        }
        return sb.length() > 0 ? sb.toString() : "General";
    }

    /** Truncates a comment to at most 200 chars, breaking at a word boundary. */
    private static String excerpt(String text) {
        if (text == null) return "";
        String t = text.replaceAll("\\s+", " ").trim();
        if (t.length() <= 200) return t;
        int cut = t.lastIndexOf(' ', 200);
        return (cut > 0 ? t.substring(0, cut) : t.substring(0, 200)) + "…";
    }

    /** Builds excerpt list from a short list of comments. */
    private static List<String> buildExcerpts(List<WeblogEntryComment> cs) {
        List<String> out = new ArrayList<>();
        int max = Math.min(MAX_REPS, cs.size());
        for (int i = 0; i < max; i++) out.add(excerpt(cs.get(i).getContent()));
        return out;
    }

    private static String buildRecap(List<WeblogEntryComment> all, int analysed) {
        DiscussionOverview ov = DiscussionOverviewService.compute(all);

        StringBuilder sb = new StringBuilder();
        sb.append(analysed).append(analysed == 1 ? " comment was" : " comments were")
          .append(" analysed. ");

        if (!"No Comments".equals(ov.getActivityLabel())) {
            sb.append("Discussion is ").append(ov.getActivityLabel().toLowerCase())
              .append(". ");
        }

        if (ov.getDominantType() != null && !"Neutral".equals(ov.getDominantType())) {
            sb.append("The dominant tone is ")
              .append(ov.getDominantType().toLowerCase()).append(". ");
        }

        List<Map.Entry<String, Integer>> kws = ov.getTopKeywords();
        if (kws != null && !kws.isEmpty()) {
            sb.append("Recurring keywords: ");
            int limit = Math.min(5, kws.size());
            for (int i = 0; i < limit; i++) {
                if (i > 0) sb.append(", ");
                sb.append(kws.get(i).getKey());
            }
            sb.append(".");
        }

        return sb.toString().trim();
    }
}
