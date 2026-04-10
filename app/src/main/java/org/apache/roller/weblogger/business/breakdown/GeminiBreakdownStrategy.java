package org.apache.roller.weblogger.business.breakdown;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.config.WebloggerConfig;
import org.apache.roller.weblogger.pojos.WeblogEntryComment;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class GeminiBreakdownStrategy implements BreakdownStrategy {

    private static final Log LOG = LogFactory.getLog(GeminiBreakdownStrategy.class);

    private static final String STRATEGY_NAME    = "gemini";
    private static final int    MAX_COMMENTS     = 50;
    private static final int    MAX_COMMENT_CHARS = 300;
    private static final int    DEFAULT_TIMEOUT  = 20;

    private static volatile Map<String, String> dotEnvCache = null;

    private static Map<String, String> loadDotEnv() {
        if (dotEnvCache != null) return dotEnvCache;
        synchronized (GeminiBreakdownStrategy.class) {
            if (dotEnvCache != null) return dotEnvCache;
            Map<String, String> env = new HashMap<>();
            String userDir  = System.getProperty("user.dir", "");
            String catBase  = System.getProperty("catalina.base", "");
            String[] candidates = {
                userDir + "/.env",
                userDir + "/../.env",
                catBase + "/.env",
                catBase + "/../../.env",
            };
            for (String candidate : candidates) {
                Path p = Paths.get(candidate).normalize();
                if (!Files.exists(p)) continue;
                try {
                    for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        int eq = line.indexOf('=');
                        if (eq <= 0) continue;
                        String k = line.substring(0, eq).trim();
                        String v = line.substring(eq + 1).trim();
                        // Strip surrounding quotes
                        if (v.length() >= 2
                                && ((v.charAt(0) == '"' && v.charAt(v.length()-1) == '"')
                                 || (v.charAt(0) == '\'' && v.charAt(v.length()-1) == '\''))) {
                            v = v.substring(1, v.length() - 1);
                        }
                        env.putIfAbsent(k, v); // first occurrence wins
                    }
                    LOG.info("GeminiBreakdownStrategy: loaded .env from "
                             + p.toAbsolutePath());
                    break; // use the first file found
                } catch (IOException e) {
                    LOG.warn("Could not read .env from " + p + ": " + e.getMessage());
                }
            }
            dotEnvCache = env;
        }
        return dotEnvCache;
    }

    private static String getEnv(String key) {
        String val = System.getenv(key);
        if (val != null && !val.trim().isEmpty()) return val.trim();
        String dotVal = loadDotEnv().get(key);
        return (dotVal != null && !dotVal.isEmpty()) ? dotVal : null;
    }

    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;

    public GeminiBreakdownStrategy() {
        String url = getEnv("TRANSLATION_GEMINI_API_URL");
        if (url == null || url.isEmpty()) {
            url = WebloggerConfig.getProperty("breakdown.gemini.apiUrl",
                  WebloggerConfig.getProperty("translation.gemini.apiUrl", null));
        }
        this.apiUrl = (url != null) ? url.trim().replaceAll("/$", "") : null;

        String key = getEnv("GEMINI_API_KEY");
        if (key == null || key.isEmpty()) {
            key = WebloggerConfig.getProperty("breakdown.gemini.apiKey",
                  WebloggerConfig.getProperty("translation.gemini.apiKey", ""));
        }
        this.apiKey = (key != null) ? key.trim() : "";

        String m = getEnv("TRANSLATION_GEMINI_MODEL");
        if (m == null || m.isEmpty()) {
            m = WebloggerConfig.getProperty("breakdown.gemini.model", "gemini-2.5-flash");
        }
        this.model = m.trim();

        int timeout = 0;
        try {
            timeout = Integer.parseInt(
                    WebloggerConfig.getProperty("breakdown.gemini.timeoutSeconds",
                                                String.valueOf(DEFAULT_TIMEOUT)));
        } catch (NumberFormatException e) {
            timeout = DEFAULT_TIMEOUT;
        }
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(timeout, 5)))
                .build();
    }

    // BreakdownStrategy
    @Override
    public String getStrategyName() {
        return STRATEGY_NAME;
    }

    @Override
    public ConversationBreakdown generate(List<WeblogEntryComment> comments)
            throws BreakdownException {

        if (apiUrl == null || apiUrl.isEmpty()) {
            throw new BreakdownException(
                    "Gemini API URL not configured. " +
                    "Set TRANSLATION_GEMINI_API_URL environment variable.");
        }
        if (apiKey.isEmpty()) {
            throw new BreakdownException(
                    "Gemini API key not configured. " +
                    "Set GEMINI_API_KEY environment variable.");
        }

        List<WeblogEntryComment> eligible = filterEligible(comments);
        if (eligible.isEmpty()) {
            return new ConversationBreakdown(
                    Collections.emptyList(),
                    "No comments to analyse.",
                    STRATEGY_NAME);
        }

        // Cap input to avoid excessive token usage
        List<WeblogEntryComment> sample = eligible.size() > MAX_COMMENTS
                ? eligible.subList(0, MAX_COMMENTS)
                : eligible;

        String prompt  = buildPrompt(sample);
        String rawJson = callGemini(prompt);
        return parseResponse(rawJson, sample);
    }

    // Prompt construction
    private String buildPrompt(List<WeblogEntryComment> comments) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a helpful assistant analysing reader comments on a blog post.\n\n");
        sb.append("Below are ").append(comments.size())
          .append(" reader comments, numbered from 1.\n");
        sb.append("Each comment is enclosed in triple back-ticks.\n\n");

        for (int i = 0; i < comments.size(); i++) {
            String text = comments.get(i).getContent();
            if (text.length() > MAX_COMMENT_CHARS) {
                text = text.substring(0, MAX_COMMENT_CHARS) + "…";
            }
            // Escape back-ticks inside the comment to avoid prompt confusion
            text = text.replace("`", "'");
            sb.append("Comment ").append(i + 1).append(":\n```\n").append(text).append("\n```\n\n");
        }

        sb.append("TASK:\n");
        sb.append("1. Identify 2 to 4 major themes being discussed.\n");
        sb.append("2. For each theme, provide 1 to 2 short representative excerpts.\n");
        sb.append("3. Write a 2-sentence overall recap of the conversation.\n\n");
        sb.append("RULES:\n");
        sb.append("- Reply ONLY with a valid JSON object — no markdown, no explanation.\n");
        sb.append("- Use exactly this structure:\n");
        sb.append("{\n");
        sb.append("  \"themes\": [\n");
        sb.append("    {\n");
        sb.append("      \"name\": \"short theme label\",\n");
        sb.append("      \"representatives\": [\"excerpt 1\", \"excerpt 2\"]\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"recap\": \"Overall recap sentence(s).\"\n");
        sb.append("}\n");

        return sb.toString();
    }

    // HTTP call to Gemini
    private String callGemini(String userPrompt) throws BreakdownException {

        // Build request body following the Gemini generateContent schema
        JSONArray parts = new JSONArray();
        parts.put(new JSONObject().put("text", userPrompt));
        JSONObject contentObj = new JSONObject();
        contentObj.put("parts", parts);
        JSONArray contents = new JSONArray();
        contents.put(contentObj);
        JSONObject body = new JSONObject();
        body.put("contents", contents);

        // Deterministic output: temperature 0 for consistent JSON
        JSONObject genConfig = new JSONObject();
        genConfig.put("temperature", 0);
        body.put("generationConfig", genConfig);

        // Append API key as query parameter
        String targetUrl = apiUrl;
        if (!apiKey.isEmpty() && !apiKey.startsWith("Bearer ")) {
            String encoded = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
            targetUrl += (targetUrl.contains("?") ? "&" : "?") + "key=" + encoded;
        }

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(
                        body.toString(), StandardCharsets.UTF_8));

        if (!apiKey.isEmpty() && apiKey.startsWith("Bearer ")) {
            reqBuilder.header("Authorization", apiKey);
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(reqBuilder.build(),
                                       HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new BreakdownException(
                    "Network error calling Gemini API: " + e.getMessage(), e);
        }

        int status = response.statusCode();
        LOG.debug("Gemini breakdown API status=" + status);

        if (status == 429) {
            throw new BreakdownException(
                    "Gemini API quota exceeded (HTTP 429). Try again later.", 429);
        }
        if (status < 200 || status >= 300) {
            throw new BreakdownException(
                    "Gemini API returned HTTP " + status +
                    ". Body: " + truncate(response.body(), 400), status);
        }

        // Extract text from candidates[0].content.parts[0].text
        try {
            JSONObject json = new JSONObject(response.body());
            JSONArray cands = json.optJSONArray("candidates");
            if (cands != null && cands.length() > 0) {
                JSONObject content = cands.getJSONObject(0).optJSONObject("content");
                if (content != null) {
                    JSONArray prts = content.optJSONArray("parts");
                    if (prts != null && prts.length() > 0) {
                        return prts.getJSONObject(0).optString("text", "");
                    }
                }
            }
        } catch (JSONException e) {
            throw new BreakdownException(
                    "Failed to parse Gemini response: " + e.getMessage(), e);
        }
        throw new BreakdownException(
                "Gemini response missing candidate text. Body: " +
                truncate(response.body(), 400));
    }

    // Response parsing
    private ConversationBreakdown parseResponse(String raw,
                                                 List<WeblogEntryComment> sample) {
        String cleaned = stripFences(raw == null ? "" : raw.trim());
        try {
            JSONObject obj = new JSONObject(cleaned);

            List<Theme> themes = new ArrayList<>();
            JSONArray themeArr = obj.optJSONArray("themes");
            if (themeArr != null) {
                for (int i = 0; i < themeArr.length(); i++) {
                    JSONObject t   = themeArr.optJSONObject(i);
                    if (t == null) continue;
                    String name    = t.optString("name", "Theme " + (i + 1));
                    List<String> reps = new ArrayList<>();
                    JSONArray repArr  = t.optJSONArray("representatives");
                    if (repArr != null) {
                        for (int j = 0; j < repArr.length(); j++) {
                            reps.add(repArr.optString(j, ""));
                        }
                    }
                    themes.add(new Theme(name, reps));
                }
            }

            String recap = obj.optString("recap", "");
            if (recap.isEmpty()) recap = "AI analysis complete.";

            return new ConversationBreakdown(themes, recap, STRATEGY_NAME);

        } catch (JSONException e) {
            LOG.warn("Gemini breakdown response was not valid JSON; using fallback. " +
                     "Raw (truncated): " + truncate(cleaned, 200));
            // Fallback: single theme with all comments, raw text as recap
            List<String> reps = new ArrayList<>();
            for (int i = 0; i < Math.min(2, sample.size()); i++) {
                reps.add(excerpt(sample.get(i).getContent()));
            }
            Theme fallback = new Theme("General Discussion", reps);
            return new ConversationBreakdown(
                    Collections.singletonList(fallback),
                    "AI analysis returned an unexpected format. Showing a general summary.",
                    STRATEGY_NAME);
        }
    }

    // Utility
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

    private static String excerpt(String text) {
        if (text == null) return "";
        String t = text.replaceAll("\\s+", " ").trim();
        if (t.length() <= 200) return t;
        int cut = t.lastIndexOf(' ', 200);
        return (cut > 0 ? t.substring(0, cut) : t.substring(0, 200)) + "…";
    }

    /** Strips Markdown code fences from the model's response. */
    private static String stripFences(String text) {
        if (text.startsWith("```")) {
            int first = text.indexOf('\n');
            if (first >= 0) text = text.substring(first + 1);
            if (text.endsWith("```")) text = text.substring(0, text.length() - 3).trim();
        }
        return text;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
