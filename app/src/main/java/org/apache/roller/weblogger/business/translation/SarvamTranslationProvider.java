package org.apache.roller.weblogger.business.translation;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;

public class SarvamTranslationProvider implements TranslationProvider {

    private static final Log LOG = LogFactory.getLog(SarvamTranslationProvider.class);

    /** Default Sarvam translate endpoint. */
    public static final String DEFAULT_API_URL = "https://api.sarvam.ai/translate";

    /** Default model identifier accepted by the Sarvam API. */
    public static final String DEFAULT_MODEL = "mayura:v1";

    /** Per-item request timeout when collecting parallel futures (seconds). */
    private static final int ITEM_TIMEOUT_SECONDS = 45;

    /** Maximum retry attempts on transient HTTP errors (429 / 5xx). */
    private static final int MAX_RETRIES = 3;

    /** Number of text items to process in one medium batch. */
    private static final int MEDIUM_BATCH_SIZE = 40;

    /** Maximum concurrent HTTP calls within a batch. */
    private static final int MAX_PARALLEL_REQUESTS = 6;

    // -----------------------------------------------------------------------
    // ISO 639-1  →  Sarvam BCP-47 locale code mapping
    // -----------------------------------------------------------------------
    private static final Map<String, String> ISO_TO_SARVAM;
    private static final Set<String> SUPPORTED_LANGUAGES;

    static {
        Map<String, String> m = new HashMap<>();
        m.put("en",  "en-IN");
        m.put("hi",  "hi-IN");
        m.put("te",  "te-IN");
        m.put("gu",  "gu-IN");
        m.put("mr",  "mr-IN");
        m.put("pa",  "pa-IN");
        ISO_TO_SARVAM = Collections.unmodifiableMap(m);
        SUPPORTED_LANGUAGES = Collections.unmodifiableSet(new HashSet<>(m.keySet()));
    }

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------
    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final String speakerGender;   // D7 fix — configurable
    private final String mode;            // D7 fix — configurable
    private final HttpClient httpClient;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    /**
     * Full constructor.  All optional parameters fall back to sensible
     * defaults when {@code null} or empty.
     *
     * @param apiKey         Sarvam AI subscription key.  {@code null} is
     *                       accepted at construction time; an exception is
     *                       thrown on the first actual {@link #translate} call.
     * @param apiUrl         Endpoint override, or {@code null} to use
     *                       {@link #DEFAULT_API_URL}.
     * @param model          Model override, or {@code null} to use
     *                       {@link #DEFAULT_MODEL}.
     * @param timeoutSeconds HTTP connect timeout in seconds (≥ 1).
     * @param speakerGender  {@code "Female"} or {@code "Male"} — controls
     *                       gendered transliteration.  Defaults to
     *                       {@code "Female"}.
     * @param mode           Translation style: {@code "formal"} or
     *                       {@code "modern-colloquial"}.  Defaults to
     *                       {@code "formal"}.
     */
    public SarvamTranslationProvider(String apiKey, String apiUrl, String model,
                                     int timeoutSeconds,
                                     String speakerGender, String mode) {
        this.apiKey  = (apiKey != null) ? apiKey.trim() : "";
        this.apiUrl  = (apiUrl  != null && !apiUrl.trim().isEmpty())
                       ? apiUrl.trim() : DEFAULT_API_URL;
        this.model   = (model  != null && !model.trim().isEmpty())
                       ? model.trim()  : DEFAULT_MODEL;
        this.speakerGender = (speakerGender != null && !speakerGender.trim().isEmpty())
                             ? speakerGender.trim() : "Female";
        this.mode    = (mode   != null && !mode.trim().isEmpty())
                       ? mode.trim()   : "formal";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(timeoutSeconds, 1)))
                .build();
    }

    /**
     * Convenience constructor that uses default {@code speakerGender} and
     * {@code mode}.  Kept for backward compatibility with existing callers.
     */
    public SarvamTranslationProvider(String apiKey, String apiUrl,
                                     String model, int timeoutSeconds) {
        this(apiKey, apiUrl, model, timeoutSeconds, "Female", "formal");
    }

    // -----------------------------------------------------------------------
    // TranslationProvider interface
    // -----------------------------------------------------------------------

    @Override
    public String getProviderName() {
        return "sarvam";
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return SUPPORTED_LANGUAGES;
    }

    /**
     * Translates texts using <strong>medium-sized throttled batches</strong>.
     *
     * <p>Each batch is translated concurrently with a fixed upper bound on
     * parallel HTTP calls to reduce API bursts and avoid HTTP 429 responses.
     * This preserves order and per-item fallback semantics.</p>
     *
     * <p>If an individual item fails (e.g. Sarvam returns HTTP 422 for an
     * ambiguous short text such as "Password (Confirm)"), that item falls back
     * to its original text rather than aborting the entire batch.  This was
     * the root cause of form-label fields disappearing in the UI when Sarvam
     * was used in the old sequential loop.</p>
     *
     * {@inheritDoc}
     */
    @Override
    public List<String> translate(List<String> texts, String sourceLang, String targetLang)
            throws TranslationException {

        if (apiKey.isEmpty()) {
            throw new TranslationException(
                    "Sarvam API key is not configured. " +
                    "Set SARVAM_API_KEY environment variable or " +
                    "translation.sarvam.apiKey in roller-jettyrun.properties.");
        }
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        String sarvamSource = resolveSarvamCode(sourceLang, true);
        String sarvamTarget = resolveSarvamCode(targetLang, false);
        List<String> results = new ArrayList<>(texts.size());

        ExecutorService executor = Executors.newFixedThreadPool(MAX_PARALLEL_REQUESTS);
        try {
            for (int start = 0; start < texts.size(); start += MEDIUM_BATCH_SIZE) {
                int end = Math.min(start + MEDIUM_BATCH_SIZE, texts.size());

                List<CompletableFuture<String>> futures = new ArrayList<>(end - start);
                for (int i = start; i < end; i++) {
                    String text = texts.get(i);
                    if (text == null || text.trim().isEmpty()) {
                        futures.add(CompletableFuture.completedFuture(text));
                    } else {
                        final String original = text;
                        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                            try {
                                return translateSingle(original, sarvamSource, sarvamTarget, 0);
                            } catch (TranslationException e) {
                                LOG.warn("Sarvam: item translation failed, keeping original text. " +
                                        "Reason: " + e.getMessage() +
                                        " | item: " + truncate(original, 60));
                                return original;
                            }
                        }, executor);
                        futures.add(future);
                    }
                }

                for (int i = 0; i < futures.size(); i++) {
                    int originalIndex = start + i;
                    try {
                        results.add(futures.get(i).get(ITEM_TIMEOUT_SECONDS, TimeUnit.SECONDS));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        results.add(texts.get(originalIndex));
                    } catch (ExecutionException | TimeoutException e) {
                        LOG.warn("Sarvam: future failed/timed-out for item " + originalIndex +
                                "; keeping original. Cause: " + e.getMessage());
                        results.add(texts.get(originalIndex) != null ? texts.get(originalIndex) : "");
                    }
                }
            }
        } finally {
            executor.shutdown();
        }

        return results;
    }

    /**
     * Detects the script/language of the given text using Unicode block
     * membership — works offline with zero API calls for all major Indic
     * scripts.  Falls back to {@code "en"} when only Latin characters are
     * found and to {@code "unknown"} when the script cannot be determined.
     *
     * <p>This replaces the previous stub that always returned {@code "unknown"}
     * (bug D10).</p>
     *
     * {@inheritDoc}
     */
    @Override
    public String detectLanguage(String text) throws TranslationException {
        if (text == null || text.trim().isEmpty()) {
            return "unknown";
        }
        // Count characters per Unicode block; return the dominant Indic script.
        Map<String, Integer> votes = new HashMap<>();
        for (char c : text.toCharArray()) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
            if (block == null) continue;
            if (block == Character.UnicodeBlock.DEVANAGARI)   vote(votes, "hi");
            else if (block == Character.UnicodeBlock.TAMIL)        vote(votes, "ta");
            else if (block == Character.UnicodeBlock.BENGALI)      vote(votes, "bn");
            else if (block == Character.UnicodeBlock.TELUGU)       vote(votes, "te");
            else if (block == Character.UnicodeBlock.KANNADA)      vote(votes, "kn");
            else if (block == Character.UnicodeBlock.MALAYALAM)    vote(votes, "ml");
            else if (block == Character.UnicodeBlock.GUJARATI)     vote(votes, "gu");
            else if (block == Character.UnicodeBlock.GURMUKHI)     vote(votes, "pa");
            else if (block == Character.UnicodeBlock.ORIYA)        vote(votes, "or");
            else if (block == Character.UnicodeBlock.ARABIC)       vote(votes, "ur");
            else if (block == Character.UnicodeBlock.BASIC_LATIN ||
                     block == Character.UnicodeBlock.LATIN_1_SUPPLEMENT)
                                                                   vote(votes, "en");
        }
        if (votes.isEmpty()) return "unknown";
        // Return language with most votes
        return votes.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("unknown");
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /**
     * Sends a single translate request to Sarvam with exponential-backoff
     * retry on HTTP 429 (rate-limit) and a one-shot retry with
     * {@code source=en-IN} on HTTP 422 "unable to detect language".
     */
    private String translateSingle(String text, String sarvamSrc, String sarvamTgt, int attempt)
            throws TranslationException {

        JSONObject body = new JSONObject();
        body.put("input",                text);
        body.put("source_language_code", sarvamSrc);
        body.put("target_language_code", sarvamTgt);
        body.put("speaker_gender",       speakerGender);   // D7 fix — from field
        body.put("mode",                 mode);             // D7 fix — from field
        body.put("model",                model);
        body.put("enable_preprocessing", false);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type",         "application/json")
                .header("API-Subscription-Key", apiKey)
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(
                        body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new TranslationException(
                    "Network error calling Sarvam API: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TranslationException(
                    "Interrupted while calling Sarvam API: " + e.getMessage(), e);
        }

        int statusCode = response.statusCode();

        if (statusCode != 200) {
            // HTTP 429 — exponential backoff retry
            if (statusCode == 429 && attempt < MAX_RETRIES) {
                long waitMs = (1L << attempt) * 1000L;
                LOG.warn("Sarvam 429 rate-limit on attempt " + attempt +
                        "; waiting " + waitMs + " ms before retry.");
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                return translateSingle(text, sarvamSrc, sarvamTgt, attempt + 1);
            }
            // HTTP 422 with "unable to detect" — retry once forcing source=en-IN
            if (statusCode == 422 && !"en-IN".equals(sarvamSrc)) {
                String bodyLower = response.body() == null ? "" : response.body().toLowerCase();
                if (bodyLower.contains("unable to detect") ||
                        bodyLower.contains("source_language_code")) {
                    LOG.debug("Sarvam 422 auto-detect failure; retrying with source=en-IN " +
                            "for text: " + truncate(text, 60));
                    // Use attempt + 1 so this path also counts against MAX_RETRIES
                    return translateSingle(text, "en-IN", sarvamTgt, attempt + 1);
                }
            }
            throw new TranslationException(
                    "Sarvam API returned HTTP " + statusCode +
                    ". Body: " + truncate(response.body(), 300),
                    statusCode);
        }

        try {
            JSONObject json = new JSONObject(response.body());
            if (!json.has("translated_text")) {
                throw new TranslationException(
                        "Sarvam response missing 'translated_text'. " +
                        "Body: " + truncate(response.body(), 300));
            }
            return json.getString("translated_text");
        } catch (org.json.JSONException e) {
            throw new TranslationException(
                    "Failed to parse Sarvam API response: " + e.getMessage(), e);
        }
    }

    /**
     * Converts an ISO 639-1 or ISO 639-2/3 code to the Sarvam BCP-47 locale
     * code.  Passes {@code "auto"} through unchanged.
     */
    private String resolveSarvamCode(String isoCode, boolean isSource)
            throws TranslationException {
        if ("auto".equalsIgnoreCase(isoCode)) {
            return "auto";
        }
        String sarvamCode = ISO_TO_SARVAM.get(isoCode.toLowerCase());
        if (sarvamCode == null) {
            throw new TranslationException(
                    "Language '" + isoCode + "' is not supported by the Sarvam provider. " +
                    "Supported: " + ISO_TO_SARVAM.keySet());
        }
        return sarvamCode;
    }

    /** Increments a vote counter in the given map. */
    private static void vote(Map<String, Integer> votes, String lang) {
        votes.merge(lang, 1, Integer::sum);
    }

    /** Truncates a string for use in log/error messages. */
    private static String truncate(String s, int maxLen) {
        if (s == null) return "(null)";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
    }
}
