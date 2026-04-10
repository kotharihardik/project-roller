package org.apache.roller.weblogger.business.chatbot;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.WeblogManager;
import org.apache.roller.weblogger.business.WebloggerFactory;
import org.apache.roller.weblogger.config.WebloggerConfig;
import org.apache.roller.weblogger.pojos.Weblog;
import org.apache.roller.weblogger.pojos.WeblogEntry;
import org.apache.roller.weblogger.pojos.WeblogEntrySearchCriteria;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

public abstract class AbstractLlmStrategy implements ChatbotAnsweringStrategy {

    private static final Log LOG = LogFactory.getLog(AbstractLlmStrategy.class);

    // Preferred generic config keys
    private static final String PROP_API_URL = "chatbot.llm.apiUrl";
    private static final String PROP_API_KEY = "chatbot.llm.apiKey";
    private static final String PROP_TIMEOUT = "chatbot.llm.timeoutSeconds";

    private static final int DEFAULT_TIMEOUT = 30;

    protected final String apiUrl;
    protected final String apiKey;
    protected final int timeoutSeconds;
    protected final HttpClient httpClient;

    protected AbstractLlmStrategy() {
        this.apiUrl = resolveLlmApiUrl();
        this.apiKey = resolveLlmApiKey();
        this.timeoutSeconds = resolveTimeoutSeconds();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    // Fetching published entries (shared by both strategies)

    protected List<WeblogEntry> fetchPublishedEntries(String weblogId) throws WebloggerException {
        WeblogManager wmgr = WebloggerFactory.getWeblogger().getWeblogManager();
        Weblog weblog = wmgr.getWeblog(weblogId);
        if (weblog == null) {
            throw new WebloggerException("Weblog not found: " + weblogId);
        }
        WeblogEntrySearchCriteria criteria = new WeblogEntrySearchCriteria();
        criteria.setWeblog(weblog);
        criteria.setStatus(WeblogEntry.PubStatus.PUBLISHED);
        criteria.setSortBy(WeblogEntrySearchCriteria.SortBy.PUBLICATION_TIME);
        criteria.setSortOrder(WeblogEntrySearchCriteria.SortOrder.DESCENDING);
        criteria.setMaxResults(500);
        return WebloggerFactory.getWeblogger().getWeblogEntryManager().getWeblogEntries(criteria);
    }

    /**
     * Fetches published entries, optionally scoped to a specific entry id.
     * Falls back to full weblog retrieval when scope id is absent or invalid.
     */
    protected List<WeblogEntry> fetchPublishedEntries(String weblogId, String entryIdScope)
            throws WebloggerException {
        if (entryIdScope == null || entryIdScope.trim().isEmpty()) {
            return fetchPublishedEntries(weblogId);
        }

        WeblogManager wmgr = WebloggerFactory.getWeblogger().getWeblogManager();
        Weblog weblog = wmgr.getWeblog(weblogId);
        if (weblog == null) {
            throw new WebloggerException("Weblog not found: " + weblogId);
        }

        WeblogEntry entry = WebloggerFactory.getWeblogger()
                .getWeblogEntryManager()
                .getWeblogEntry(entryIdScope);

        if (entry == null || entry.getWebsite() == null || !weblogId.equals(entry.getWebsite().getId())
                || !entry.isPublished()) {
            return fetchPublishedEntries(weblogId);
        }

        return Collections.singletonList(entry);
    }

    // LLM API call (Template Method: subclasses provide the system prompt)
    protected String callLlmApi(String context, String question, String systemInstruction, String contextLabel)
            throws ChatbotException {
        if (apiUrl == null || apiUrl.isEmpty()) {
            throw new ChatbotException(
                "Chatbot LLM API URL not configured. " +
                "Set CHATBOT_LLM_API_URL or chatbot.llm.apiUrl in roller.properties.");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            throw new ChatbotException(
                    "Chatbot LLM API key not configured. " +
                "Set CHATBOT_LLM_API_KEY or chatbot.llm.apiKey in roller.properties.");
        }

        JSONObject systemPart = new JSONObject()
                .put("parts", new JSONArray().put(new JSONObject().put("text", systemInstruction)));

        JSONObject userPart = new JSONObject()
                .put("role", "user")
                .put("parts", new JSONArray().put(new JSONObject().put("text",
                        contextLabel + ":\n\n" + context +
                        "\n\n---\nUser question: " + question)));

        JSONObject body = new JSONObject()
                .put("system_instruction", systemPart)
                .put("contents", new JSONArray().put(userPart))
                .put("generationConfig", new JSONObject()
                        .put("temperature", 0.3)
                        .put("maxOutputTokens", 1024));

        String url = apiUrl + (apiUrl.contains("?") ? "&" : "?") + "key=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                String errorBody = response.body();
                LOG.error("LLM API returned HTTP " + response.statusCode() + ": " + errorBody);
                String detail = errorBody;
                try {
                    JSONObject errJson = new JSONObject(errorBody);
                    detail = errJson.optJSONObject("error") != null
                            ? errJson.getJSONObject("error").optString("message", errorBody)
                            : errorBody;
                } catch (Exception ignored) {
                }
                throw new ChatbotException("LLM API error (HTTP " + response.statusCode() + "): " + detail);
            }
            return extractAnswer(response.body());
        } catch (ChatbotException e) {
            throw e;
        } catch (Exception e) {
            throw new ChatbotException("Failed to call LLM API: " + e.getMessage(), e);
        }
    }

    private String extractAnswer(String responseBody) throws ChatbotException {
        try {
            JSONObject json = new JSONObject(responseBody);
            JSONArray candidates = json.optJSONArray("candidates");
            if (candidates == null || candidates.isEmpty()) {
                throw new ChatbotException("Empty response from LLM API");
            }
            JSONObject first = candidates.getJSONObject(0);
            JSONObject content = first.optJSONObject("content");
            if (content == null) {
                throw new ChatbotException("No content in LLM response");
            }
            JSONArray parts = content.optJSONArray("parts");
            if (parts == null || parts.isEmpty()) {
                throw new ChatbotException("No parts in LLM response");
            }
            return parts.getJSONObject(0).getString("text").trim();
        } catch (ChatbotException e) {
            throw e;
        } catch (Exception e) {
            throw new ChatbotException("Failed to parse LLM response: " + e.getMessage(), e);
        }
    }

    // Utility: text processing & config resolutio
    protected static String stripHtml(String html) {
        if (html == null) return null;
        return html.replaceAll("<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("\\s+", " ")
                .trim();
    }

    protected static String resolveConfig(String propKey, String envKey, String defaultVal) {
        String env = System.getenv(envKey);
        if (env != null && !env.trim().isEmpty()) return env.trim();
        String prop = WebloggerConfig.getProperty(propKey);
        if (prop != null && !prop.trim().isEmpty() && !prop.contains("${")) return prop.trim();
        return defaultVal;
    }

    protected static int intConfig(String propKey, int defaultVal) {
        String val = WebloggerConfig.getProperty(propKey);
        if (val != null && !val.trim().isEmpty()) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultVal;
    }

    private static String resolveLlmApiUrl() {
        return resolveConfig(PROP_API_URL, "CHATBOT_LLM_API_URL", "");
    }

    private static String resolveLlmApiKey() {
        return resolveConfig(PROP_API_KEY, "CHATBOT_LLM_API_KEY", "");
    }

    private static int resolveTimeoutSeconds() {
        return intConfig(PROP_TIMEOUT, DEFAULT_TIMEOUT);
    }
}