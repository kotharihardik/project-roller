package org.apache.roller.weblogger.business.chatbot;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.pojos.WeblogEntry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RAGStrategy extends AbstractLlmStrategy {

    private static final Log LOG = LogFactory.getLog(RAGStrategy.class);

    private static final String STRATEGY_NAME = "rag";
    private static final String DISPLAY_NAME  = "RAG (Retrieval-Augmented)";

    private static final String PROP_TOP_K    = "chatbot.rag.topK";
    private static final String PROP_CHUNK_SZ = "chatbot.rag.chunkSize";
    private static final String PROP_CHUNK_OV = "chatbot.rag.chunkOverlap";

    private static final int DEFAULT_TOP_K    = 5;
    private static final int DEFAULT_CHUNK_SZ = 1500;
    private static final int DEFAULT_CHUNK_OV = 150;

    private static final String SYSTEM_INSTRUCTION =
            "You are a helpful Q&A assistant for a weblog. " +
            "Answer the user's question ONLY based on the RETRIEVED blog excerpts provided below. " +
            "If the provided excerpts don't contain enough information to answer, say so clearly. " +
            "Be concise and accurate. Cite the blog post title(s) when relevant. " +
            "Do NOT hallucinate or add information not present in the excerpts.";

    private final int topK;
    private final int chunkSize;
    private final int chunkOverlap;

    // In-memory TF-IDF index per weblog
    private final ConcurrentHashMap<String, List<IndexedChunk>> indexCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> indexTimestamp = new ConcurrentHashMap<>();
    private static final long INDEX_TTL_MS = 10 * 60 * 1000L;

    public RAGStrategy() {
        super();
        this.topK = positiveConfig(PROP_TOP_K, DEFAULT_TOP_K);
        this.chunkSize = positiveConfig(PROP_CHUNK_SZ, DEFAULT_CHUNK_SZ);

        int overlap = nonNegativeConfig(PROP_CHUNK_OV, DEFAULT_CHUNK_OV);
        if (overlap >= chunkSize) {
            LOG.warn("Config " + PROP_CHUNK_OV + "=" + overlap +
                    " must be smaller than " + PROP_CHUNK_SZ + "=" + chunkSize +
                    "; clamping overlap to " + (chunkSize / 4));
            overlap = chunkSize / 4;
        }
        this.chunkOverlap = overlap;
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
            List<IndexedChunk> index = getOrBuildIndex(weblogId);
            if (index.isEmpty()) {
                return new ChatbotResponse(
                        "This weblog has no published entries yet, so I can't answer questions about it.",
                        STRATEGY_NAME, 0, System.currentTimeMillis() - start);
            }

            List<IndexedChunk> scope = applyEntryScope(index, entryIdScope);
            if (scope.isEmpty()) {
                scope = index;
            }

            boolean broadQuery = entryIdScope == null && isBroadQuery(question);
            List<IndexedChunk> relevant = retrieveTopK(question, scope, broadQuery);

            StringBuilder context = new StringBuilder();
            Set<String> entryTitles = new LinkedHashSet<>();
            for (IndexedChunk chunk : relevant) {
                context.append("=== From: ").append(chunk.entryTitle).append(" ===\n");
                context.append(chunk.text).append("\n\n");
                entryTitles.add(chunk.entryTitle);
            }

            String answer = callLlmApi(context.toString(), question, SYSTEM_INSTRUCTION,
                    "Retrieved blog excerpts");

            return new ChatbotResponse(answer, STRATEGY_NAME, entryTitles.size(),
                    System.currentTimeMillis() - start);

        } catch (WebloggerException e) {
            throw new ChatbotException("Failed to fetch weblog entries for RAG: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // TF-IDF Indexing
    // ------------------------------------------------------------------

    private List<IndexedChunk> getOrBuildIndex(String weblogId) throws WebloggerException {
        Long ts = indexTimestamp.get(weblogId);
        if (ts != null && (System.currentTimeMillis() - ts) < INDEX_TTL_MS && indexCache.containsKey(weblogId)) {
            return indexCache.get(weblogId);
        }
        return buildIndex(weblogId);
    }

    private synchronized List<IndexedChunk> buildIndex(String weblogId) throws WebloggerException {
        Long ts = indexTimestamp.get(weblogId);
        if (ts != null && (System.currentTimeMillis() - ts) < INDEX_TTL_MS && indexCache.containsKey(weblogId)) {
            return indexCache.get(weblogId);
        }

        LOG.info("Building RAG index for weblog " + weblogId);

        List<WeblogEntry> entries = fetchPublishedEntries(weblogId);
        List<IndexedChunk> chunks = new ArrayList<>();

        for (WeblogEntry entry : entries) {
            String body = stripHtml(entry.getText());
            if (body == null || body.trim().isEmpty()) continue;

            String title = entry.getTitle() != null ? entry.getTitle() : "Untitled";
            String dateStr = entry.getPubTime() != null ? entry.getPubTime().toString() : "";

            chunks.add(new IndexedChunk(title, title + " " + dateStr, entry.getId()));

            List<String> bodyChunks = chunkText(body, chunkSize, chunkOverlap);
            for (String chunkText : bodyChunks) {
                chunks.add(new IndexedChunk(title, chunkText, entry.getId()));
            }
        }

        int n = chunks.size();
        Map<String, Integer> docFreq = new HashMap<>();
        for (IndexedChunk chunk : chunks) {
            chunk.terms = tokenize(chunk.text);
            Set<String> uniqueTerms = new HashSet<>(chunk.terms.keySet());
            for (String term : uniqueTerms) {
                docFreq.merge(term, 1, Integer::sum);
            }
        }

        for (IndexedChunk chunk : chunks) {
            chunk.tfidf = new HashMap<>();
            for (Map.Entry<String, Integer> e : chunk.terms.entrySet()) {
                double tf = e.getValue();
                double idf = Math.log((double) n / (1.0 + docFreq.getOrDefault(e.getKey(), 0)));
                chunk.tfidf.put(e.getKey(), tf * idf);
            }
            chunk.norm = vectorNorm(chunk.tfidf);
        }

        indexCache.put(weblogId, chunks);
        indexTimestamp.put(weblogId, System.currentTimeMillis());
        LOG.info("RAG index built: " + chunks.size() + " chunks from " + entries.size() + " entries");
        return chunks;
    }

    private List<IndexedChunk> retrieveTopK(String question, List<IndexedChunk> index, boolean diversifyByEntry) {
        Map<String, Integer> queryTerms = tokenize(question);

        Map<String, Double> queryTfidf = new HashMap<>();
        int n = index.size();
        for (Map.Entry<String, Integer> e : queryTerms.entrySet()) {
            double tf = e.getValue();
            long df = index.stream().filter(c -> c.terms.containsKey(e.getKey())).count();
            double idf = Math.log((double) n / (1.0 + df));
            queryTfidf.put(e.getKey(), tf * idf);
        }
        double queryNorm = vectorNorm(queryTfidf);

        if (queryNorm == 0) {
            List<ScoredChunk> fallbackScored = scoreWithLooseOverlap(question, index);
            if (!fallbackScored.isEmpty()) {
                return selectTopK(fallbackScored, diversifyByEntry);
            }
            return index.subList(0, Math.min(topK, index.size()));
        }

        List<ScoredChunk> scored = new ArrayList<>();
        for (IndexedChunk chunk : index) {
            double dot = 0;
            for (Map.Entry<String, Double> e : queryTfidf.entrySet()) {
                Double chunkVal = chunk.tfidf.get(e.getKey());
                if (chunkVal != null) {
                    dot += e.getValue() * chunkVal;
                }
            }
            double sim = (chunk.norm > 0) ? dot / (queryNorm * chunk.norm) : 0;
            scored.add(new ScoredChunk(chunk, sim));
        }

        scored.sort((a, b) -> Double.compare(b.score, a.score));

        return selectTopK(scored, diversifyByEntry);
    }

    private List<IndexedChunk> applyEntryScope(List<IndexedChunk> index, String entryIdScope) {
        if (entryIdScope == null || entryIdScope.trim().isEmpty()) {
            return index;
        }
        return index.stream()
                .filter(c -> entryIdScope.equals(c.entryId))
                .collect(Collectors.toList());
    }

    private static boolean isBroadQuery(String question) {
        if (question == null) return false;
        String q = question.toLowerCase(Locale.ROOT);
        return q.contains("summarize")
                || q.contains("summary")
                || q.contains("overall")
                || q.contains("all posts")
                || q.contains("all entries")
                || q.contains("whole blog")
                || q.contains("across the blog")
                || q.contains("what topics")
                || q.contains("main topics")
                || q.contains("what has this blog said");
    }

    // Text processing utilities
    private static List<String> chunkText(String text, int chunkSize, int chunkOverlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty() || chunkSize <= 0) return chunks;

        int i = 0;
        while (i < text.length()) {
            int end = Math.min(i + chunkSize, text.length());
            if (end < text.length()) {
                int sentenceBoundary = bestSentenceBoundary(text, end);
                if (sentenceBoundary > i + chunkSize / 2) {
                    end = sentenceBoundary + 1;
                } else {
                    int lastSpace = text.lastIndexOf(' ', end);
                    if (lastSpace > i) {
                        end = lastSpace;
                    }
                }
            }

            if (end <= i) {
                end = Math.min(text.length(), i + chunkSize);
                if (end <= i) break;
            }

            String chunk = text.substring(i, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            if (end >= text.length()) {
                break;
            }

            int next = Math.max(i + 1, end - chunkOverlap);
            while (next < text.length() && Character.isWhitespace(text.charAt(next))) {
                next++;
            }
            i = next;
        }
        return chunks;
    }

    private static int bestSentenceBoundary(String text, int end) {
        int period = text.lastIndexOf(". ", end);
        int bang = text.lastIndexOf("! ", end);
        int question = text.lastIndexOf("? ", end);
        return Math.max(period, Math.max(bang, question));
    }

    private static Map<String, Integer> tokenize(String text) {
        Map<String, Integer> terms = new HashMap<>();
        if (text == null) return terms;
        String[] words = text.toLowerCase().split("[^a-z0-9]+");
        for (String w : words) {
            if (w.length() > 2 && !STOP_WORDS.contains(w)) {
                terms.merge(w, 1, Integer::sum);
            }
        }
        return terms;
    }

    private static Set<String> tokenizeLoose(String text) {
        Set<String> terms = new HashSet<>();
        if (text == null) return terms;
        String[] words = text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        for (String w : words) {
            if (!w.isEmpty()) {
                terms.add(w);
            }
        }
        return terms;
    }

    private static List<ScoredChunk> scoreWithLooseOverlap(String question, List<IndexedChunk> index) {
        Set<String> queryTerms = tokenizeLoose(question);
        if (queryTerms.isEmpty()) {
            return Collections.emptyList();
        }

        List<ScoredChunk> scored = new ArrayList<>();
        for (IndexedChunk chunk : index) {
            Set<String> chunkTerms = tokenizeLoose(chunk.text);
            int overlap = 0;
            for (String qt : queryTerms) {
                if (chunkTerms.contains(qt)) {
                    overlap++;
                }
            }
            if (overlap > 0) {
                scored.add(new ScoredChunk(chunk, overlap));
            }
        }
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        return scored;
    }

    private List<IndexedChunk> selectTopK(List<ScoredChunk> scored, boolean diversifyByEntry) {
        if (!diversifyByEntry) {
            return scored.stream()
                    .limit(topK)
                    .map(sc -> sc.chunk)
                    .collect(Collectors.toList());
        }

        // Broad query behavior: prefer one best chunk per entry to maximize
        // coverage rather than taking multiple chunks from the same entry.
        LinkedHashMap<String, IndexedChunk> perEntry = new LinkedHashMap<>();
        for (ScoredChunk sc : scored) {
            perEntry.putIfAbsent(sc.chunk.entryId, sc.chunk);
            if (perEntry.size() >= topK) break;
        }
        if (!perEntry.isEmpty()) {
            return new ArrayList<>(perEntry.values());
        }

        return scored.stream()
                .limit(topK)
                .map(sc -> sc.chunk)
                .collect(Collectors.toList());
    }

    private static int positiveConfig(String propKey, int defaultVal) {
        int value = intConfig(propKey, defaultVal);
        if (value <= 0) {
            LOG.warn("Config " + propKey + "=" + value + " is invalid; using " + defaultVal);
            return defaultVal;
        }
        return value;
    }

    private static int nonNegativeConfig(String propKey, int defaultVal) {
        int value = intConfig(propKey, defaultVal);
        if (value < 0) {
            LOG.warn("Config " + propKey + "=" + value + " is invalid; using " + defaultVal);
            return defaultVal;
        }
        return value;
    }

    private static double vectorNorm(Map<String, Double> vector) {
        double sum = 0;
        for (double v : vector.values()) sum += v * v;
        return Math.sqrt(sum);
    }

    // Inner classes
    static class IndexedChunk {
        final String entryTitle;
        final String text;
        final String entryId;
        Map<String, Integer> terms;
        Map<String, Double> tfidf;
        double norm;

        IndexedChunk(String entryTitle, String text, String entryId) {
            this.entryTitle = entryTitle;
            this.text = text;
            this.entryId = entryId;
        }
    }

    private static class ScoredChunk {
        final IndexedChunk chunk;
        final double score;

        ScoredChunk(IndexedChunk chunk, double score) {
            this.chunk = chunk;
            this.score = score;
        }
    }

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "the", "is", "at", "which", "on", "a", "an", "and", "or", "but",
            "in", "with", "to", "for", "of", "not", "no", "can", "had", "have",
            "was", "were", "been", "being", "are", "this", "that", "these",
            "those", "it", "its", "from", "by", "as", "into", "through",
            "during", "before", "after", "above", "below", "between", "out",
            "off", "over", "under", "again", "further", "then", "once", "here",
            "there", "when", "where", "why", "how", "all", "both", "each",
            "few", "more", "most", "other", "some", "such", "only", "own",
            "same", "so", "than", "too", "very", "just", "because", "about",
            "would", "could", "should", "will", "shall", "may", "might",
            "must", "also", "what", "who", "whom", "has", "does", "did",
            "doing", "done", "get", "got", "gets", "getting", "make", "made",
            "you", "your", "they", "them", "their", "his", "her", "she", "he",
            "him", "we", "our", "my", "me", "do"
    ));
}
