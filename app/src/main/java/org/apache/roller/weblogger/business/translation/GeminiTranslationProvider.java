package org.apache.roller.weblogger.business.translation;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class GeminiTranslationProvider implements TranslationProvider {

    private static final Log LOG = LogFactory.getLog(GeminiTranslationProvider.class);

    private static final Set<String> SUPPORTED_LANGUAGES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "en",   // English
                    "hi",   // Hindi
                    "te",   // Telugu
                    "mr",   // Marathi
                    "gu",   // Gujarati
                "pa"    // Punjabi
            )));

    private static final String SYSTEM_INSTRUCTION =
            "You are a strict translator. " +
            "RULES (follow exactly, no exceptions):\n" +
            "1. Translate EVERY word. Keep the exact meaning. Do not add or remove words.\n" +
            "2. Do NOT summarize, shorten, paraphrase, or change the meaning.\n" +
            "3. Preserve punctuation and special characters. Localize digits to the target language script when applicable (hi/mr=Devanagari, te=Telugu, gu=Gujarati, pa=Gurmukhi).\n" +
            "4. Output ONLY the translated text — no explanations, no notes, no markdown.\n" +
            "5. If a word has no direct translation, transliterate it.";

    private static final int MAX_RETRIES = 3;

    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;


    /**
     * @param apiUrl          Full Gemini endpoint URL including model path.
     *                        Must not be {@code null}.
     * @param apiKey          Gemini API key (appended as {@code ?key=...}) or
     *                        {@code "Bearer <token>"} (sent as Authorization header).
     * @param timeoutSeconds  HTTP connect timeout in seconds.
     * @param model           Gemini model name (e.g. {@code "gemini-2.0-flash"}).
     *                        Used only for logging; the model is already encoded in
     *                        the URL by {@link TranslationService}.
     */
    public GeminiTranslationProvider(String apiUrl, String apiKey,
                                     int timeoutSeconds, String model) {
        if (apiUrl == null || apiUrl.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Gemini apiUrl must not be null/empty. " +
                    "Set TRANSLATION_GEMINI_API_URL or translation.gemini.apiUrl.");
        }
        this.apiUrl = apiUrl.trim().replaceAll("/$", "");
        this.apiKey = (apiKey != null) ? apiKey.trim() : "";
        this.model  = (model  != null && !model.trim().isEmpty()) ? model.trim() : "gemini-2.0-flash";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(timeoutSeconds, 5)))
                .build();
    }

    @Override
    public String getProviderName() {
        return "gemini";
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return SUPPORTED_LANGUAGES;
    }

    @Override
    public List<String> translate(List<String> texts, String sourceLang, String targetLang)
            throws TranslationException {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        String src = "auto".equalsIgnoreCase(sourceLang) ? "auto" : sourceLang.toLowerCase();
        String tgt = targetLang.toLowerCase();

        return translateBatch(texts, src, tgt);
    }

    @Override
    public String detectLanguage(String text) throws TranslationException {
        if (text == null || text.trim().isEmpty()) {
            return "unknown";
        }
        String sample = text.length() > 300 ? text.substring(0, 300) : text;
        String prompt = "Identify the language of the text below.\n" +
                "Return ONLY the ISO 639-1 two-letter code (e.g. 'en', 'hi', 'ta').\n" +
                "Output ONLY the code — no explanation, no punctuation.\n" +
                "Text:\n" + sample;
        try {
            String raw = callGeminiEndpoint(prompt, 0);
            if (raw == null) return "unknown";
            String code = raw.trim().toLowerCase().replaceAll("[^a-z]", "");
            return code.length() >= 2 ? code.substring(0, 2) : "unknown";
        } catch (TranslationException e) {
            LOG.warn("Gemini language detection failed: " + e.getMessage());
            return "unknown";
        }
    }

    private List<String> translateBatch(List<String> texts, String src, String tgt)
            throws TranslationException {

        // Build prompt that includes source language so the model knows what
        // language to translate FROM (fixes the "src ignored" bug).
        StringBuilder sb = new StringBuilder();
        if ("auto".equals(src)) {
            sb.append("Detect the source language and translate each text to ").append(tgt).append(".\n");
        } else {
            sb.append("Translate each text from ").append(src).append(" to ").append(tgt).append(".\n");
        }
        sb.append("Translate all natural-language words and sentences fully into the target language.\n");
        sb.append("Do not keep English text unchanged, except for URLs, email addresses, code snippets, and clear proper names.\n");
        sb.append("Convert number digits to the target language numeral script when the target language supports native numerals (hi/mr/te/gu/pa).\n");
        sb.append("Return ONLY a valid JSON array of translated strings in the SAME ORDER as the input.\n");
        sb.append("Output ONLY the JSON array — no explanation, no markdown, no extra text.\n");
        sb.append("Input:\n[");
        for (int i = 0; i < texts.size(); i++) {
            if (i > 0) sb.append(",\n");
            String raw = (texts.get(i) == null) ? "" : texts.get(i);
            String t = raw.replace("\r\n", " ").replace("\n", " ")
                          .replace("\r", " ").replace("\t", " ").trim();
            // JSON-escape backslashes and double-quotes inside the value
            t = t.replace("\\", "\\\\").replace("\"", "\\\"");
            sb.append('"').append(t).append('"');
        }
        sb.append("\n]");

        String out = callGeminiEndpoint(sb.toString(), 0);
        if (out == null) {
            throw new TranslationException("Gemini returned null output for batch translate.");
        }
        out = stripMarkdownFences(out);

        // Parse the model's JSON array response
        try {
            JSONArray arr = new JSONArray(out.trim());
            List<String> result = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
                result.add(cleanText(arr.optString(i, "")));
            }
            // Ensure result size matches input size
            while (result.size() < texts.size()) {
                result.add("");
            }
            return result.size() > texts.size()
                   ? result.subList(0, texts.size())
                   : result;
        } catch (JSONException je) {
            // Fallback: split by newlines and map positionally to inputs
            LOG.warn("Gemini batch response was not a valid JSON array; attempting line split. " +
                     "Raw output: " + truncate(out, 200));
            String[] lines = out.split("\r?\n");
            List<String> result = new ArrayList<>(texts.size());
            for (int i = 0; i < texts.size(); i++) {
                String v = (i < lines.length) ? cleanText(lines[i].trim()) : "";
                result.add(v);
            }
            return result;
        }
    }

    // -----------------------------------------------------------------------
    // Core HTTP call — shared by both translate and detectLanguage
    // -----------------------------------------------------------------------

    /**
     * Sends one request to the Gemini generative API with the given user
     * prompt and returns the raw text from {@code candidates[0].content.parts[0]}.
     *
     * <p>This is the single canonical method that builds the request body,
     * handles retries and parses the response.  Both
     * {@link #translateBatch} and {@link #detectLanguage} call this method
     * directly, avoiding any code duplication.</p>
     *
     * @param userPrompt the user-turn text for the generative model.
     * @param attempt    current retry attempt (0-based).
     * @return raw text from the model's first candidate, never {@code null}
     *         unless the response genuinely contains no text part.
     * @throws TranslationException on HTTP or parse failure.
     */
    private String callGeminiEndpoint(String userPrompt, int attempt)
            throws TranslationException {

        // ── Build JSON body ──────────────────────────────────────────────
        JSONArray sysParts = new JSONArray();
        sysParts.put(new JSONObject().put("text", SYSTEM_INSTRUCTION));
        JSONObject sysInstr = new JSONObject();
        sysInstr.put("parts", sysParts);

        JSONArray parts = new JSONArray();
        parts.put(new JSONObject().put("text", userPrompt));
        JSONObject contentObj = new JSONObject();
        contentObj.put("parts", parts);
        JSONArray contents = new JSONArray();
        contents.put(contentObj);

        JSONObject body = new JSONObject();
        body.put("model", this.model);
        body.put("systemInstruction", sysInstr);
        body.put("contents", contents);

        // ── Determine target URL (append ?key= if plain API key) ─────────
        String targetUrl = this.apiUrl;
        if (!this.apiKey.isEmpty()
                && !this.apiKey.startsWith("Bearer ")
                && !this.apiKey.startsWith("bearer ")) {
            String encoded = URLEncoder.encode(this.apiKey, StandardCharsets.UTF_8);
            targetUrl += (targetUrl.contains("?") ? "&" : "?") + "key=" + encoded;
        }

        // ── Build HTTP request ────────────────────────────────────────────
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(
                        body.toString(), StandardCharsets.UTF_8));
        if (!this.apiKey.isEmpty()
                && (this.apiKey.startsWith("Bearer ") || this.apiKey.startsWith("bearer "))) {
            reqBuilder.header("Authorization", this.apiKey);
        }

        // ── Send ──────────────────────────────────────────────────────────
        HttpResponse<String> response;
        try {
            response = httpClient.send(reqBuilder.build(),
                                       HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new TranslationException(
                    "Network error calling Gemini API: " + e.getMessage(), e);
        }

        int statusCode = response.statusCode();
        LOG.debug("Gemini API response status=" + statusCode
                + " attempt=" + attempt
                + " bodyLen=" + (response.body() == null ? 0 : response.body().length()));

        // ── Handle non-2xx ────────────────────────────────────────────────
        if (statusCode < 200 || statusCode >= 300) {
            if (statusCode == 429) {
                // Quota exceeded — do not retry immediately
                throw new TranslationException(
                        "Gemini API quota exceeded (HTTP 429). " +
                        "Body: " + truncate(response.body(), 300), statusCode);
            }
            if (attempt < MAX_RETRIES) {
                long waitMs = (1L << attempt) * 1000L;   // 1 s, 2 s, 4 s
                LOG.warn("Gemini API HTTP " + statusCode + " on attempt " + attempt
                        + "; retrying in " + waitMs + " ms.");
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                return callGeminiEndpoint(userPrompt, attempt + 1);
            }
            throw new TranslationException(
                    "Gemini API returned HTTP " + statusCode +
                    ". Body: " + truncate(response.body(), 800), statusCode);
        }

        // ── Parse candidates[0].content.parts[0].text ─────────────────────
        try {
            JSONObject json = new JSONObject(response.body());
            if (json.has("candidates")) {
                JSONArray cands = json.getJSONArray("candidates");
                if (!cands.isEmpty()) {
                    JSONObject content = cands.getJSONObject(0).optJSONObject("content");
                    if (content != null) {
                        JSONArray prts = content.optJSONArray("parts");
                        if (prts != null && prts.length() > 0) {
                            return prts.getJSONObject(0).optString("text", null);
                        }
                    }
                }
            }
            // Fallback field used by some Gemini proxy wrappers
            if (json.has("result")) {
                return json.getString("result");
            }
            throw new TranslationException(
                    "Gemini response missing candidate text. " +
                    "Body: " + truncate(response.body(), 800));
        } catch (JSONException e) {
            throw new TranslationException(
                    "Failed to parse Gemini API response: " + e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------------
    // Text-cleanup helpers
    // -----------------------------------------------------------------------

    /**
     * Strips Markdown code fences (e.g. <code>```json\n[...]\n```</code>) and
     * outer wrapping double-quotes around a JSON array that the model sometimes
     * emits.
     */
    private static String stripMarkdownFences(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            t = (nl >= 0) ? t.substring(nl + 1) : t.substring(3);
        }
        if (t.endsWith("```")) {
            t = t.substring(0, t.length() - 3);
        }
        t = t.trim();
        // Unwrap outer quotes around a JSON array: "[...]" -> [...]
        if (t.startsWith("\"[") && t.endsWith("]\"")) {
            t = t.substring(1, t.length() - 1).trim();
        }
        return t;
    }

    /**
     * Removes surrounding quotes and trailing commas the model sometimes emits
     * for individual strings, and normalises escape sequences.
     */
    static String cleanText(String s) {
        if (s == null) return null;
        String t = s.trim();
        // Normalise escape sequences the model sometimes emits literally
        t = t.replace("\\n", " ").replace("\\t", " ").trim();
        // Normalise embedded whitespace
        t = t.replace("\r\n", " ").replace("\n", " ")
             .replace("\r", " ").replace("\t", " ").trim();
        // Iteratively strip surrounding quotes and trailing commas
        String prev;
        do {
            prev = t;
            if (t.endsWith(",")) t = t.substring(0, t.length() - 1).trim();
            if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\""))
                t = t.substring(1, t.length() - 1).trim();
            if (t.length() >= 2 && t.startsWith("'") && t.endsWith("'"))
                t = t.substring(1, t.length() - 1).trim();
        } while (!t.equals(prev));
        // A lone stray quote is meaningless — discard it
        if (t.equals("\"") || t.equals("'")) return "";
        return t;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "(null)";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
    }
}