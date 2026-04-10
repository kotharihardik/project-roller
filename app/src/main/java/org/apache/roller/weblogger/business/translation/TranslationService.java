package org.apache.roller.weblogger.business.translation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.config.WebloggerConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-level translation service that sits between the REST endpoint and the
 * raw {@link TranslationProvider} adapters.
 *
 * <h3>Responsibilities</h3>
 * <ol>
 *   <li><strong>Provider factory</strong> – reads {@code translation.provider}
 *       from {@code roller.properties} and instantiates the matching adapter.
 *       Adding a new provider requires only one line in
 *       {@link #buildProvider(String)}.</li>
 *   <li><strong>Caching</strong> – a lightweight, thread-safe TTL cache avoids
 *       repeated API calls for identical (provider, srcLang, tgtLang, text)
 *       quadruples.  The default TTL is 24 hours; override with
 *       {@code translation.cache.ttlSeconds} in {@code roller.properties}.</li>
 *   <li><strong>Input validation</strong> – rejects blank/null inputs, enforces
 *       per-request text limits, and verifies the target language is in the
 *       provider's supported set before making any remote call.</li>
 *   <li><strong>Batching</strong> – texts are passed to the provider in one
 *       call; cache-hit texts are stripped out before the call and re-inserted
 *       into the result list at the correct positions.</li>
 * </ol>
 *
 * <h3>Provider configuration</h3>
 * In {@code roller.properties} or {@code roller-custom.properties}:
 * <pre>
 * # Which provider to use: "sarvam", "gemini" or "remote"
 * translation.provider=sarvam
 *
 * # --- Sarvam ---
 * translation.sarvam.apiKey=&lt;key&gt;
 * # translation.sarvam.apiUrl=https://api.sarvam.ai/translate
 * # translation.sarvam.model=mayura:v1
 * # translation.sarvam.timeoutSeconds=10
 *
 * # --- Generic remote / Gemini proxy ---
 * # Configure a generic remote translation endpoint (used for "gemini" or
 * # "remote" provider names). The remote service should expose a JSON
 * # /translate and optional /detect endpoint similar to the adapter's
 * # expectations.
 * # translation.gemini.apiUrl=https://your-gemini-endpoint.example
 * # translation.gemini.apiKey=YOUR_GEMINI_API_KEY
 * # translation.gemini.timeoutSeconds=10
 *
 * # --- Cache ---
 * # translation.cache.ttlSeconds=86400
 * # translation.cache.maxEntries=5000
 *
 * # --- Limits ---
 * # translation.maxTextsPerRequest=200
 * # translation.maxCharsPerText=2000
 * </pre>
 *
 * <h3>Thread safety</h3>
 * This class is thread-safe.  Use a single shared instance (e.g. as a Spring
 * bean or a Guice singleton).
 */
public class TranslationService {

    private static final Log LOG = LogFactory.getLog(TranslationService.class);

    // -----------------------------------------------------------------------
    // Configuration property keys (all in roller.properties)
    // -----------------------------------------------------------------------
    private static final String PROP_PROVIDER              = "translation.provider";
    private static final String PROP_SARVAM_API_KEY        = "translation.sarvam.apiKey";
    private static final String PROP_SARVAM_API_URL        = "translation.sarvam.apiUrl";
    private static final String PROP_SARVAM_MODEL          = "translation.sarvam.model";
    private static final String PROP_SARVAM_TIMEOUT        = "translation.sarvam.timeoutSeconds";
    private static final String PROP_SARVAM_GENDER         = "translation.sarvam.speakerGender";
    private static final String PROP_SARVAM_MODE           = "translation.sarvam.mode";
    private static final String PROP_REMOTE_API_URL        = "translation.remote.apiUrl";
    private static final String PROP_REMOTE_API_KEY        = "translation.remote.apiKey";
    private static final String PROP_REMOTE_TIMEOUT        = "translation.remote.timeoutSeconds";
    private static final String PROP_CACHE_TTL             = "translation.cache.ttlSeconds";
    private static final String PROP_CACHE_MAX             = "translation.cache.maxEntries";
    private static final String PROP_MAX_TEXTS             = "translation.maxTextsPerRequest";
    private static final String PROP_MAX_CHARS             = "translation.maxCharsPerText";

    // Defaults
    private static final String  DEFAULT_PROVIDER    = "sarvam";
    private static final int     DEFAULT_TIMEOUT     = 10;
    private static final long    DEFAULT_TTL_SECONDS = 86_400L; // 24 h
    private static final int     DEFAULT_CACHE_MAX   = 5_000;
    private static final int     DEFAULT_MAX_TEXTS   = 200;
    private static final int     DEFAULT_MAX_CHARS   = 2_000;

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    /** Active translation provider (immutable after construction). */
    private final TranslationProvider provider;

    /** Name of the active provider, e.g. "sarvam", "gemini" or "remote". */
    private final String providerName;

    /** Lazily created providers for runtime provider switching. */
    private final ConcurrentHashMap<String, TranslationProvider> providerPool = new ConcurrentHashMap<>();

    /** TTL for cache entries in milliseconds. */
    private final long cacheTtlMs;

    /** Maximum number of cache entries before oldest are evicted. */
    private final int cacheMaxEntries;

    /** Maximum number of text items per translate request. */
    private final int maxTextsPerRequest;

    /** Maximum characters per individual text item. */
    private final int maxCharsPerText;

    /**
     * In-memory translation cache.
     * Key: {@link CacheKey}  →  Value: {@link CacheEntry}
     *
     * ConcurrentHashMap gives us thread-safe reads/writes without a global
     * lock.  We do opportunistic size-based eviction inside {@link #cacheGet}.
     */
    private final ConcurrentHashMap<CacheKey, CacheEntry> cache = new ConcurrentHashMap<>();

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    /**
     * Default constructor: reads all configuration from
     * {@link WebloggerConfig}.  Use this in production.
     *
     * @throws IllegalStateException if the configured provider is unknown or
     *                               required credentials are missing.
     */
    public TranslationService() {
        String pName = cfgStr(PROP_PROVIDER, DEFAULT_PROVIDER);
        this.providerName      = pName;
        this.provider          = buildProvider(pName);
        this.providerPool.put(normalizeProviderName(pName), this.provider);
        this.cacheTtlMs        = cfgLong(PROP_CACHE_TTL,  DEFAULT_TTL_SECONDS) * 1000L;
        this.cacheMaxEntries   = cfgInt (PROP_CACHE_MAX,  DEFAULT_CACHE_MAX);
        this.maxTextsPerRequest= cfgInt (PROP_MAX_TEXTS,  DEFAULT_MAX_TEXTS);
        this.maxCharsPerText   = cfgInt (PROP_MAX_CHARS,  DEFAULT_MAX_CHARS);
        LOG.info("TranslationService initialised with provider='" + pName + "'");
    }

    /**
     * Package-private constructor for unit tests: accepts a pre-built provider
     * so tests can inject mocks without touching {@link WebloggerConfig}.
     *
     * @param provider          provider mock or stub.
     * @param cacheTtlMs        TTL for cache entries in milliseconds.
     * @param cacheMaxEntries   maximum number of entries in the cache.
     * @param maxTextsPerRequest per-request text-item limit.
     * @param maxCharsPerText    per-item character limit.
     */
    TranslationService(TranslationProvider provider,
                       long cacheTtlMs,
                       int  cacheMaxEntries,
                       int  maxTextsPerRequest,
                       int  maxCharsPerText) {
        this.provider           = Objects.requireNonNull(provider, "provider");
        this.providerName       = provider.getProviderName();
        this.providerPool.put(normalizeProviderName(this.providerName), this.provider);
        this.cacheTtlMs         = cacheTtlMs;
        this.cacheMaxEntries    = cacheMaxEntries;
        this.maxTextsPerRequest = maxTextsPerRequest;
        this.maxCharsPerText    = maxCharsPerText;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Returns the name of the currently active provider (e.g. {@code "sarvam"},
     * {@code "gemini"} or {@code "remote"}).
     */
    public String getProviderName() {
        return providerName;
    }

    /**
     * Returns the set of ISO 639-1 language codes supported by the active
     * provider.  Both source and target roles are valid for every code.
     */
    public Set<String> getSupportedLanguages() {
        return provider.getSupportedLanguages();
    }

    /**
     * Returns supported languages for a requested provider (or default
     * provider when {@code requestedProvider} is blank).
     */
    public Set<String> getSupportedLanguages(String requestedProvider) throws TranslationException {
        TranslationProvider resolved = resolveProvider(requestedProvider);
        return resolved.getSupportedLanguages();
    }

    /**
     * Translates a list of plain-text strings from {@code sourceLang} to
     * {@code targetLang}.
     *
     * <p>Cache-hit items are returned immediately without contacting the
     * remote provider.  Only cache-miss items are batched into a single
     * provider call, then re-merged at the correct positions.</p>
     *
     * @param texts      plain-text strings extracted from the DOM (no HTML).
     *                   Blank/null entries are returned as-is.
     * @param sourceLang ISO 639-1 source language code or {@code "auto"}.
     * @param targetLang ISO 639-1 target language code; must be in
     *                   {@link #getSupportedLanguages()}.
     * @return           translated strings in the same order as {@code texts}.
     * @throws TranslationException on validation failure or provider error.
     */
    public List<String> translate(List<String> texts, String sourceLang, String targetLang)
            throws TranslationException {

        return translate(texts, sourceLang, targetLang, providerName);
    }

    /**
     * Translates using a runtime-selected provider.
     *
     * @param requestedProvider provider key such as {@code sarvam} or
     *                          {@code gemini}; blank uses configured default.
     */
    public List<String> translate(List<String> texts, String sourceLang, String targetLang,
                                  String requestedProvider) throws TranslationException {

        TranslationProvider activeProvider = resolveProvider(requestedProvider);
        String activeProviderName = normalizeProviderName(requestedProvider);

        // ---- 1. Validate inputs ----
        validateRequest(texts, sourceLang, targetLang, activeProvider, activeProviderName);

        String src = sourceLang.trim().toLowerCase();
        String tgt = targetLang.trim().toLowerCase();

        // ---- 2. Build result list; find cache misses ----
        //
        // resultSlots[i] = translated string (filled from cache immediately,
        //                  or filled after provider call)
        String[] resultSlots = new String[texts.size()];
        // missIndices contains the original indexes of cache-miss items
        List<Integer> missIndices = new ArrayList<>();
        // missTexts contains the actual text strings for those misses
        List<String>  missTexts   = new ArrayList<>();

        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);

            // Pass-through blank / null entries unchanged
            if (text == null || text.trim().isEmpty()) {
                resultSlots[i] = text;
                continue;
            }

            // Truncate oversized texts to avoid provider rejection
            String safeText = text.length() > maxCharsPerText
                              ? text.substring(0, maxCharsPerText)
                              : text;

            CacheKey key = new CacheKey(activeProviderName, src, tgt, safeText);
            String cached = cacheGet(key);
            if (cached != null) {
                resultSlots[i] = cached;
            } else {
                missIndices.add(i);
                missTexts.add(safeText);
            }
        }

        // ---- 3. Call provider only for cache misses ----
        if (!missTexts.isEmpty()) {
            LOG.debug("Translation cache miss: " + missTexts.size()
                      + " texts to translate via ");

            List<String> translated = activeProvider.translate(missTexts, src, tgt);

            if ("sarvam".equals(activeProviderName)) {
                translated = retrySarvamMissesWithGemini(missTexts, translated, src, tgt);
            }

            if (translated.size() != missTexts.size()) {
                throw new TranslationException(
                        "Provider '" + activeProviderName + "' returned "
                        + translated.size() + " results for "
                        + missTexts.size() + " inputs (size mismatch).");
            }

            // ---- 4. Store new results in cache and back-fill result slots ----
            for (int j = 0; j < missIndices.size(); j++) {
                int    origIdx = missIndices.get(j);
                String input   = missTexts.get(j);
                String output  = translated.get(j);

                // Only cache non-null, non-empty results
                if (output != null && !output.isEmpty()) {
                    CacheKey key = new CacheKey(activeProviderName, src, tgt, input);
                    cachePut(key, output);
                }
                resultSlots[origIdx] = output;
            }
        }

        // Convert array → list (preserving nulls for pass-through items)
        List<String> result = new ArrayList<>(texts.size());
        for (String slot : resultSlots) {
            result.add(slot);
        }
        return result;
    }

    private List<String> retrySarvamMissesWithGemini(List<String> originals,
                                                     List<String> translated,
                                                     String sourceLang,
                                                     String targetLang) {
        if (originals == null || translated == null || originals.size() != translated.size()) {
            return translated;
        }

        List<Integer> fallbackIndexes = new ArrayList<>();
        List<String> fallbackTexts = new ArrayList<>();

        for (int i = 0; i < originals.size(); i++) {
            if (shouldRetryUnchangedTranslation(originals.get(i), translated.get(i), sourceLang, targetLang)) {
                fallbackIndexes.add(i);
                fallbackTexts.add(originals.get(i));
            }
        }

        if (fallbackTexts.isEmpty()) {
            return translated;
        }

        try {
            TranslationProvider geminiProvider = resolveProvider("gemini");
            List<String> geminiResults = geminiProvider.translate(fallbackTexts, sourceLang, targetLang);
            if (geminiResults.size() != fallbackTexts.size()) {
                LOG.warn("Gemini fallback returned unexpected result count; keeping Sarvam outputs.");
                return translated;
            }

            List<String> merged = new ArrayList<>(translated);
            for (int i = 0; i < fallbackIndexes.size(); i++) {
                String geminiText = geminiResults.get(i);
                if (geminiText != null && !geminiText.trim().isEmpty()) {
                    merged.set(fallbackIndexes.get(i), geminiText);
                }
            }

            LOG.info("Applied Gemini fallback for " + fallbackIndexes.size()
                    + " Sarvam translation item(s).");
            return merged;
        } catch (TranslationException e) {
            LOG.warn("Gemini fallback unavailable after Sarvam untranslated output: " + e.getMessage());
            return translated;
        }
    }

    private boolean shouldRetryUnchangedTranslation(String original,
                                                    String translated,
                                                    String sourceLang,
                                                    String targetLang) {
        if (original == null || translated == null) return false;

        String src = original.trim();
        String tx = translated.trim();
        String source = sourceLang == null ? "" : sourceLang.trim().toLowerCase();
        String target = targetLang == null ? "" : targetLang.trim().toLowerCase();

        if (src.isEmpty() || tx.isEmpty() || target.isEmpty()) return false;
        if (target.equals(source)) return false;
        if (!containsLetters(src)) return false;

        if (src.equalsIgnoreCase(tx)) return true;

        return !"en".equals(target)
                && containsLatinLetters(tx)
                && !containsTargetScript(tx, target);
    }

    private boolean containsLetters(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetter(text.charAt(i))) return true;
        }
        return false;
    }

    private boolean containsLatinLetters(String text) {
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isLetter(ch)) {
                Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
                if (block == Character.UnicodeBlock.BASIC_LATIN
                        || block == Character.UnicodeBlock.LATIN_1_SUPPLEMENT) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsTargetScript(String text, String targetLang) {
        Character.UnicodeBlock targetBlock;
        if ("hi".equals(targetLang) || "mr".equals(targetLang)) {
            targetBlock = Character.UnicodeBlock.DEVANAGARI;
        } else if ("gu".equals(targetLang)) {
            targetBlock = Character.UnicodeBlock.GUJARATI;
        } else if ("te".equals(targetLang)) {
            targetBlock = Character.UnicodeBlock.TELUGU;
        } else if ("pa".equals(targetLang)) {
            targetBlock = Character.UnicodeBlock.GURMUKHI;
        } else {
            return true;
        }

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isLetter(ch) && Character.UnicodeBlock.of(ch) == targetBlock) {
                return true;
            }
        }
        return false;
    }

    /**
     * Detects the most likely language of {@code text} using the active
     * provider's detection capability.
     *
     * @param text sample text (typically first ~200 characters of the page).
     * @return ISO 639-1 language code, or {@code "unknown"}.
     * @throws TranslationException if the provider returns an error.
     */
    public String detectLanguage(String text) throws TranslationException {
        if (text == null || text.trim().isEmpty()) {
            return "unknown";
        }
        return provider.detectLanguage(text);
    }

    /**
     * Clears the entire translation cache.  Useful in tests and after
     * switching providers at runtime.
     */
    public void clearCache() {
        cache.clear();
        LOG.debug("Translation cache cleared.");
    }

    /**
     * Returns the current number of entries in the translation cache.
     * Exposed as a monitoring/test hook.
     */
    public int cacheSize() {
        return cache.size();
    }

    /** Returns the effective provider name for a runtime request. */
    public String resolveProviderName(String requestedProvider) throws TranslationException {
        return resolveProvider(requestedProvider).getProviderName();
    }

    // -----------------------------------------------------------------------
    // Provider factory
    // -----------------------------------------------------------------------

    /**
     * Instantiates a {@link TranslationProvider} for the given {@code name}.
     *
     * <p>To add a new provider:
     * <ol>
     *   <li>Create a class in this package implementing {@link TranslationProvider}.</li>
     *   <li>Add a {@code case "yourkey":} branch here.</li>
     *   <li>Set {@code translation.provider=yourkey} in roller.properties.</li>
     * </ol>
     * </p>
     *
     * @param name provider key (case-insensitive), e.g. {@code "sarvam"}.
     * @throws IllegalStateException for unknown provider names.
     */
    private TranslationProvider buildProvider(String name) {
        switch (name.toLowerCase()) {

            case "sarvam": {
                String apiKey = firstNonBlank(
                    System.getenv("SARVAM_API_KEY"),
                    System.getenv("TRANSLATION_SARVAM_API_KEY"),
                    resolveEnvPlaceholder(cfgStr(PROP_SARVAM_API_KEY, null)));
                String apiUrl = firstNonBlank(
                    System.getenv("TRANSLATION_SARVAM_API_URL"),
                    System.getenv("SARVAM_API_URL"),
                    resolveEnvPlaceholder(cfgStr(PROP_SARVAM_API_URL, null)));
                String model = firstNonBlank(
                    System.getenv("TRANSLATION_SARVAM_MODEL"),
                    resolveEnvPlaceholder(cfgStr(PROP_SARVAM_MODEL, null)));
                int    timeout  = cfgInt(PROP_SARVAM_TIMEOUT,  DEFAULT_TIMEOUT);
                String gender   = cfgStr(PROP_SARVAM_GENDER,  "Female");
                String sMode    = cfgStr(PROP_SARVAM_MODE,    "formal");
                return new SarvamTranslationProvider(apiKey, apiUrl, model, timeout, gender, sMode);
            }

            case "gemini": {
                // Prefer environment variables, fall back to properties.
                String apiUrl = System.getenv("TRANSLATION_GEMINI_API_URL");
                if (apiUrl == null || apiUrl.trim().isEmpty()) {
                    apiUrl = cfgStr("translation.gemini.apiUrl", null);
                }
                String apiKey = System.getenv("GEMINI_API_KEY");
                if (apiKey == null) {
                    apiKey = cfgStr("translation.gemini.apiKey", "");
                }
                String model = System.getenv("TRANSLATION_GEMINI_MODEL");
                if (model == null) {
                    model = cfgStr("translation.gemini.model", null);
                }
                int timeout = cfgInt("translation.gemini.timeoutSeconds", DEFAULT_TIMEOUT);
                return new GeminiTranslationProvider(apiUrl, apiKey, timeout, model);
            }

            case "remote": {
                // Backward-compatible alias for "gemini" — delegates via RemoteTranslationProvider.
                String apiUrl = System.getenv("TRANSLATION_GEMINI_API_URL");
                if (apiUrl == null || apiUrl.trim().isEmpty()) {
                    apiUrl = cfgStr("translation.gemini.apiUrl", null);
                }
                String apiKey = System.getenv("GEMINI_API_KEY");
                if (apiKey == null) {
                    apiKey = cfgStr("translation.gemini.apiKey", "");
                }
                String model = System.getenv("TRANSLATION_GEMINI_MODEL");
                if (model == null) {
                    model = cfgStr("translation.gemini.model", null);
                }
                int timeout = cfgInt("translation.gemini.timeoutSeconds", DEFAULT_TIMEOUT);
                return new RemoteTranslationProvider(apiUrl, apiKey, timeout, model);
            }

            default:
                throw new IllegalStateException(
                    "Unknown translation provider '" + name + "'. " +
                    "Supported values: sarvam, gemini (remote is a deprecated alias for gemini). " +
                    "Set 'translation.provider' in roller-custom.properties.");
        }
    }

    // -----------------------------------------------------------------------
    // Validation
    // -----------------------------------------------------------------------

    private void validateRequest(List<String> texts, String sourceLang, String targetLang,
                                 TranslationProvider activeProvider,
                                 String activeProviderName)
            throws TranslationException {

        if (texts == null) {
            throw new TranslationException("texts list must not be null.");
        }
        if (texts.isEmpty()) {
            // empty list is fine — caller gets empty list back
            return;
        }
        if (texts.size() > maxTextsPerRequest) {
            throw new TranslationException(
                    "Request contains " + texts.size() + " texts; maximum allowed is "
                    + maxTextsPerRequest + ". Split the request into smaller batches.");
        }
        if (targetLang == null || targetLang.trim().isEmpty()) {
            throw new TranslationException("targetLang must not be null or blank.");
        }
        String tgt = targetLang.trim().toLowerCase();
        if (!activeProvider.getSupportedLanguages().contains(tgt)) {
            throw new TranslationException(
                    "Target language '" + tgt + "' is not supported by provider '"
                    + activeProviderName + "'. Supported: " + activeProvider.getSupportedLanguages());
        }
        if (sourceLang == null || sourceLang.trim().isEmpty()) {
            throw new TranslationException("sourceLang must not be null or blank.");
        }
        String src = sourceLang.trim().toLowerCase();
        if (!"auto".equals(src) && !activeProvider.getSupportedLanguages().contains(src)) {
            throw new TranslationException(
                    "Source language '" + src + "' is not supported by provider '"
                    + activeProviderName + "'. Use 'auto' for automatic detection.");
        }
    }

    private String normalizeProviderName(String requestedProvider) {
        if (requestedProvider == null || requestedProvider.trim().isEmpty()) {
            return providerName.trim().toLowerCase();
        }
        return requestedProvider.trim().toLowerCase();
    }

    private TranslationProvider resolveProvider(String requestedProvider)
            throws TranslationException {
        String normalized = normalizeProviderName(requestedProvider);
        if (normalized.equals(normalizeProviderName(providerName))) {
            return provider;
        }
        try {
            return providerPool.computeIfAbsent(normalized, this::buildProvider);
        } catch (IllegalStateException e) {
            throw new TranslationException(
                    "Unknown translation provider '" + normalized +
                    "'. Supported values: sarvam, gemini.", e);
        }
    }

    // -----------------------------------------------------------------------
    // Cache helpers
    // -----------------------------------------------------------------------

    /**
     * Returns the cached translation for {@code key}, or {@code null} on miss
     * (including expired entries).
     */
    private String cacheGet(CacheKey key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() > entry.expiresAt) {
            cache.remove(key); // expired — treat as miss
            return null;
        }
        return entry.value;
    }

    /**
     * Stores {@code value} in the cache under {@code key} with a TTL of
     * {@link #cacheTtlMs} milliseconds.
     *
     * <p>When the cache exceeds {@link #cacheMaxEntries}, about 10 % of
     * entries are evicted (the ones with the earliest expiry).</p>
     */
    private void cachePut(CacheKey key, String value) {
        if (cache.size() >= cacheMaxEntries) {
            evictOldest((int) (cacheMaxEntries * 0.10) + 1);
        }
        long expiresAt = System.currentTimeMillis() + cacheTtlMs;
        cache.put(key, new CacheEntry(value, expiresAt));
    }

    /**
     * Removes up to {@code count} entries, preferring those with the earliest
     * expiry timestamp.  Uses a lightweight scan since eviction is rare.
     */
    private void evictOldest(int count) {
        cache.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(
                        (a, b) -> Long.compare(a.expiresAt, b.expiresAt)))
                .limit(count)
                .forEach(e -> cache.remove(e.getKey()));
    }

    // -----------------------------------------------------------------------
    // Config helpers (delegate to WebloggerConfig with fallback defaults)
    // -----------------------------------------------------------------------

    private static String cfgStr(String key, String defaultValue) {
        String val = WebloggerConfig.getProperty(key);
        return (val != null && !val.trim().isEmpty()) ? val.trim() : defaultValue;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String resolveEnvPlaceholder(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("${env.") || !trimmed.endsWith("}")) {
            return trimmed;
        }

        String inner = trimmed.substring("${env.".length(), trimmed.length() - 1);
        String envName = inner;
        String fallback = null;
        int colonIndex = inner.indexOf(':');
        if (colonIndex >= 0) {
            envName = inner.substring(0, colonIndex);
            fallback = inner.substring(colonIndex + 1);
        }

        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.trim().isEmpty()) {
            return envValue.trim();
        }
        if (fallback != null && !fallback.trim().isEmpty()) {
            return fallback.trim();
        }
        return null;
    }

    private static int cfgInt(String key, int defaultValue) {
        String val = WebloggerConfig.getProperty(key);
        if (val == null || val.trim().isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            LOG.warn("Invalid integer for property '" + key + "': " + val
                     + "; using default " + defaultValue);
            return defaultValue;
        }
    }

    private static long cfgLong(String key, long defaultValue) {
        String val = WebloggerConfig.getProperty(key);
        if (val == null || val.trim().isEmpty()) return defaultValue;
        try {
            return Long.parseLong(val.trim());
        } catch (NumberFormatException e) {
            LOG.warn("Invalid long for property '" + key + "': " + val
                     + "; using default " + defaultValue);
            return defaultValue;
        }
    }

    // -----------------------------------------------------------------------
    // Inner types
    // -----------------------------------------------------------------------

    /**
     * Immutable cache key composed of provider name, source language, target
     * language and the exact input text.
     */
    static final class CacheKey {
        final String provider;
        final String sourceLang;
        final String targetLang;
        final String text;

        CacheKey(String provider, String sourceLang, String targetLang, String text) {
            this.provider   = provider;
            this.sourceLang = sourceLang;
            this.targetLang = targetLang;
            this.text       = text;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CacheKey)) return false;
            CacheKey k = (CacheKey) o;
            return provider.equals(k.provider)
                && sourceLang.equals(k.sourceLang)
                && targetLang.equals(k.targetLang)
                && text.equals(k.text);
        }

        @Override
        public int hashCode() {
            return Objects.hash(provider, sourceLang, targetLang, text);
        }
    }

    /**
     * Mutable cache entry (value + expiry timestamp).
     */
    static final class CacheEntry {
        final String value;
        final long   expiresAt; // epoch millis

        CacheEntry(String value, long expiresAt) {
            this.value     = value;
            this.expiresAt = expiresAt;
        }
    }
}