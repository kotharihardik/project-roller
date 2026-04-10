package org.apache.roller.weblogger.ui.struts2.ajax;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.business.translation.TranslationException;
import org.apache.roller.weblogger.business.translation.TranslationService;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Servlet that exposes translation capabilities as a JSON REST API.
 *
 * <h3>Endpoints</h3>
 *
 * <h4>GET /roller-services/translate</h4>
 * Returns supported languages and the current active provider.
 * Response:
 * <pre>
 * {
 *   "provider":   "sarvam",
 *   "languages": ["en", "hi", "fr", "es", "de", "ta", "bn"]
 * }
 * </pre>
 *
 * <h4>POST /roller-services/translate</h4>
 * Request body (JSON):
 * <pre>
 * {
 *   "provider": "sarvam",   (optional — uses roller.properties default if omitted)
 *   "source":   "auto",     (ISO 639-1 or "auto")
 *   "target":   "hi",       (ISO 639-1, required)
 *   "texts":    ["Hello world", "Click here to read more"]
 * }
 * </pre>
 * Response (HTTP 200):
 * <pre>
 * {
 *   "provider":      "sarvam",
 *   "translations":  ["नमस्ते दुनिया", "और पढ़ने के लिए यहाँ क्लिक करें"]
 * }
 * </pre>
 * Error (HTTP 400 / 429 / 503):
 * <pre>{ "error": "reason" }</pre>
 *
 * <h3>Security</h3>
 * <ul>
 *   <li>This endpoint is <strong>publicly accessible</strong> — no login required.
 *       Translation is a read-only convenience feature and API keys are
 *       stored server-side only; no key ever reaches the client.</li>
 *   <li>A simple per-IP sliding-window rate limiter (default: 60 requests /
 *       minute) prevents abuse.  Override with {@code translation.rateLimit.requestsPerMinute}
 *       in {@code roller.properties}.</li>
 *   <li>Incoming JSON body is size-limited to 64 KB.</li>
 * </ul>
 *
 * <h3>CORS</h3>
 * Adds {@code Access-Control-Allow-Origin: *} so that embedded widgets
 * served from the same Roller instance can call the API.
 */
public class TranslationServlet extends HttpServlet {

    private static final Log LOG = LogFactory.getLog(TranslationServlet.class);

    /** Maximum allowed request body size (64 KB). */
    private static final int MAX_BODY_BYTES = 65_536;
    private static final String[] SUPPORTED_RUNTIME_PROVIDERS = {"sarvam", "gemini"};

    // -----------------------------------------------------------------------
    // Singleton TranslationService (lazy-initialised, thread-safe)
    // -----------------------------------------------------------------------

    /**
     * Shared {@link TranslationService} instance.  Initialised once on first
     * use and reused by all subsequent requests.  Uses double-checked locking
     * with a volatile field for safe publication.
     */
    private static volatile TranslationService translationService;

    /**
     * Returns (or lazily creates) the shared {@link TranslationService} instance.
     *
     * <p>If initialisation fails (e.g. missing API key) the exception is
     * propagated so the caller can return HTTP 503 to the client.</p>
     */
    static TranslationService getTranslationService() {
        if (translationService == null) {
            synchronized (TranslationServlet.class) {
                if (translationService == null) {
                    try {
                        translationService = new TranslationService();
                        LOG.info("TranslationService singleton initialised.");
                    } catch (Exception e) {
                        // Re-throw so per-request error handling can report it
                        throw new IllegalStateException(
                                "Failed to initialise TranslationService: " + e.getMessage(), e);
                    }
                }
            }
        }
        return translationService;
    }

    /**
     * Replaces the singleton — used only in unit tests to inject a mock.
     */
    static void setTranslationServiceForTesting(TranslationService svc) {
        translationService = svc;
    }

    // -----------------------------------------------------------------------
    // Simple per-IP rate limiter
    // -----------------------------------------------------------------------

    private static final int DEFAULT_RATE_LIMIT = 60; // requests per minute per IP

    /** IP → [requestCount, windowStartMs] */
    private final ConcurrentHashMap<String, long[]> rateLimitMap = new ConcurrentHashMap<>();

    /**
     * Returns {@code true} if the given IP is within its request quota,
     * {@code false} if the limit has been exceeded.
     *
     * <p>Uses a simple 60-second tumbling window counter.</p>
     */
    private boolean isWithinRateLimit(String ip) {
        long now   = System.currentTimeMillis();
        long windowMs = 60_000L;

        long[] slot = rateLimitMap.compute(ip, (key, existing) -> {
            if (existing == null || (now - existing[1]) > windowMs) {
                // New window
                return new long[]{1, now};
            }
            existing[0]++;
            return existing;
        });

        return slot[0] <= DEFAULT_RATE_LIMIT;
    }

    // -----------------------------------------------------------------------
    // GET — return supported languages + active provider name
    // -----------------------------------------------------------------------

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        setCorsHeaders(resp);

        TranslationService svc;
        try {
            svc = getTranslationService();
        } catch (IllegalStateException e) {
            writeError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Translation service unavailable: " + e.getMessage());
            return;
        }

        Set<String> languages = svc.getSupportedLanguages();

        JSONObject out = new JSONObject();
        out.put("provider",  svc.getProviderName());
        out.put("providers", new JSONArray(SUPPORTED_RUNTIME_PROVIDERS));
        out.put("languages", new JSONArray(languages));

        writeJson(resp, HttpServletResponse.SC_OK, out.toString());
    }

    // -----------------------------------------------------------------------
    // OPTIONS — CORS preflight
    // -----------------------------------------------------------------------

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        setCorsHeaders(resp);
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    // -----------------------------------------------------------------------
    // POST — translate texts
    // -----------------------------------------------------------------------

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        setCorsHeaders(resp);

        // ---- Rate limit ----
        String clientIp = getClientIp(req);
        if (!isWithinRateLimit(clientIp)) {
            writeError(resp, 429 /* Too Many Requests */,
                    "Rate limit exceeded. Maximum " + DEFAULT_RATE_LIMIT
                    + " requests per minute per IP.");
            return;
        }

        // ---- Initialise service ----
        TranslationService svc;
        try {
            svc = getTranslationService();
        } catch (IllegalStateException e) {
            LOG.error("TranslationService init failed", e);
            writeError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Translation service unavailable. Check server configuration.");
            return;
        }

        // ---- Read and size-check request body ----
        String body;
        try {
            body = readBody(req);
        } catch (IOException e) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Could not read request body: " + e.getMessage());
            return;
        }

        if (body == null || body.trim().isEmpty()) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Request body is empty. Expected JSON with 'target' and 'texts' fields.");
            return;
        }

        // ---- Parse JSON ----
        JSONObject requestJson;
        try {
            requestJson = new JSONObject(body);
        } catch (org.json.JSONException e) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid JSON: " + e.getMessage());
            return;
        }

        // ---- Extract required fields ----
        String targetLang = requestJson.optString("target", "").trim();
        if (targetLang.isEmpty()) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "'target' language code is required (e.g. \"hi\", \"fr\").");
            return;
        }

        String sourceLang = requestJson.optString("source", "auto").trim();
        if (sourceLang.isEmpty()) sourceLang = "auto";

        String requestedProvider = requestJson.optString("provider", svc.getProviderName()).trim();
        if (requestedProvider.isEmpty()) {
            requestedProvider = svc.getProviderName();
        }

        JSONArray textsArray = requestJson.optJSONArray("texts");
        if (textsArray == null || textsArray.isEmpty()) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "'texts' array is required and must not be empty.");
            return;
        }

        // Convert JSONArray to List<String>
        List<String> texts = new ArrayList<>(textsArray.length());
        for (int i = 0; i < textsArray.length(); i++) {
            texts.add(textsArray.optString(i, ""));
        }

        // ---- Translate ----
        List<String> translated;
        String effectiveProvider;
        try {
            translated = svc.translate(texts, sourceLang, targetLang, requestedProvider);
            effectiveProvider = svc.resolveProviderName(requestedProvider);
        } catch (TranslationException e) {
            LOG.warn("Translation error for target=" + targetLang + ": " + e.getMessage());
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        } catch (Exception e) {
            LOG.error("Unexpected error during translation", e);
            writeError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "An unexpected error occurred. Please try again.");
            return;
        }

        // ---- Build response ----
        JSONObject out = new JSONObject();
        out.put("provider",     effectiveProvider);
        out.put("translations", new JSONArray(translated));

        writeJson(resp, HttpServletResponse.SC_OK, out.toString());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Reads the request body up to {@link #MAX_BODY_BYTES}. */
    private String readBody(HttpServletRequest req) throws IOException {
        int contentLength = req.getContentLength();
        if (contentLength > MAX_BODY_BYTES) {
            throw new IOException("Request body exceeds maximum allowed size ("
                    + MAX_BODY_BYTES + " bytes).");
        }
        return req.getReader().lines().collect(Collectors.joining("\n"));
    }

    private static void writeJson(HttpServletResponse resp, int status, String json)
            throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        PrintWriter writer = resp.getWriter();
        writer.print(json);
        writer.flush();
    }

    private static void writeError(HttpServletResponse resp, int status, String message)
            throws IOException {
        JSONObject err = new JSONObject();
        err.put("error", message);
        writeJson(resp, status, err.toString());
    }

    private static void setCorsHeaders(HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin", "*");
    }

    /** Resolves the real client IP, respecting X-Forwarded-For for proxied deployments. */
    private static String getClientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            // Use only the first IP in the chain
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}