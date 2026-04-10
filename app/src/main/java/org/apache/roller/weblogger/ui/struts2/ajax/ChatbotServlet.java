package org.apache.roller.weblogger.ui.struts2.ajax;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.business.chatbot.ChatbotException;
import org.apache.roller.weblogger.business.chatbot.ChatbotResponse;
import org.apache.roller.weblogger.business.chatbot.ChatbotService;
import org.apache.roller.weblogger.config.WebloggerConfig;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ChatbotServlet extends HttpServlet {

    private static final Log LOG = LogFactory.getLog(ChatbotServlet.class);
    private static final int MAX_BODY_BYTES = 16_384; // 16 KB max request

    private static final String PROP_RATE_LIMIT_PER_MIN = "chatbot.rateLimit.requestsPerMinute";
    private static final String PROP_RATE_LIMIT_WINDOW_SECONDS = "chatbot.rateLimit.windowSeconds";
    private static final String PROP_RATE_LIMIT_MAX_TRACKED_IPS = "chatbot.rateLimit.maxTrackedIps";

    private static final int DEFAULT_RATE_LIMIT_PER_MIN = 20;
    private static final int DEFAULT_RATE_WINDOW_SECONDS = 60;
    private static final int DEFAULT_MAX_TRACKED_IPS = 2000;

    private final int rateLimitPerMinute =
        readIntConfig(PROP_RATE_LIMIT_PER_MIN, DEFAULT_RATE_LIMIT_PER_MIN, 1, 10_000);
    private final long rateWindowMs = 1000L *
        readIntConfig(PROP_RATE_LIMIT_WINDOW_SECONDS, DEFAULT_RATE_WINDOW_SECONDS, 1, 3600);
    private final int maxTrackedIps =
        readIntConfig(PROP_RATE_LIMIT_MAX_TRACKED_IPS, DEFAULT_MAX_TRACKED_IPS, 100, 100_000);

    // Simple per-IP rate limiter
    private final ConcurrentHashMap<String, RateBucket> rateLimiter = new ConcurrentHashMap<>();

    // GET — return available strategies
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=UTF-8");
        setCorsHeaders(resp);

        try {
            ChatbotService service = ChatbotService.getInstance();
            List<Map<String, String>> strategies = service.getAvailableStrategies();

            JSONArray arr = new JSONArray();
            for (Map<String, String> s : strategies) {
                arr.put(new JSONObject(s));
            }

            JSONObject json = new JSONObject().put("strategies", arr);
            resp.setStatus(200);
            PrintWriter w = resp.getWriter();
            w.print(json.toString());
            w.flush();
        } catch (Exception e) {
            LOG.error("ChatbotServlet GET error", e);
            sendError(resp, 500, "Internal server error");
        }
    }

    // POST — answer a question
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=UTF-8");
        setCorsHeaders(resp);

        // Rate limiting
        String ip = getClientIp(req);
        if (isRateLimited(ip)) {
            resp.setHeader("Retry-After", String.valueOf(Math.max(1L, rateWindowMs / 1000L)));
            sendError(resp, 429, "Rate limit exceeded. Please wait before trying again.");
            return;
        }

        // Read request body
        String body;
        try {
            body = readBody(req);
        } catch (IOException e) {
            sendError(resp, 400, "Request body too large (max 16 KB)");
            return;
        }

        // Parse JSON
        JSONObject input;
        try {
            input = new JSONObject(body);
        } catch (Exception e) {
            sendError(resp, 400, "Invalid JSON");
            return;
        }

        String weblogId = input.optString("weblogId", "").trim();
        String question = input.optString("question", "").trim();
        String strategy = input.optString("strategy", null);
        String entryId = input.optString("entryId", "").trim();

        if (weblogId.isEmpty()) {
            sendError(resp, 400, "Missing required field: weblogId");
            return;
        }
        if (question.isEmpty()) {
            sendError(resp, 400, "Missing required field: question");
            return;
        }
        if (question.length() > 1000) {
            sendError(resp, 400, "Question too long (max 1000 characters)");
            return;
        }
        if (entryId.length() > 128) {
            sendError(resp, 400, "entryId too long");
            return;
        }

        try {
            ChatbotService service = ChatbotService.getInstance();
            ChatbotResponse result = service.askQuestion(
                    weblogId,
                    question,
                    strategy,
                    entryId.isEmpty() ? null : entryId
            );

            JSONObject json = new JSONObject()
                    .put("answer", result.getAnswer())
                    .put("strategyUsed", result.getStrategyUsed())
                    .put("entriesConsulted", result.getEntriesConsulted())
                    .put("latencyMs", result.getLatencyMs());

            resp.setStatus(200);
            PrintWriter w = resp.getWriter();
            w.print(json.toString());
            w.flush();

        } catch (ChatbotException e) {
            LOG.error("Chatbot error: " + e.getMessage(), e);
            sendError(resp, 503, "Chatbot error: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Unexpected chatbot error", e);
            sendError(resp, 500, "Internal server error");
        }
    }

    // OPTIONS — CORS preflight
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        setCorsHeaders(resp);
        resp.setStatus(200);
    }

    // Helpers
    private void setCorsHeaders(HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
        resp.setHeader("Access-Control-Max-Age", "3600");
    }

    private String readBody(HttpServletRequest req) throws IOException {
        int len = req.getContentLength();
        if (len > MAX_BODY_BYTES) {
            throw new IOException("Body too large");
        }
        byte[] buf = req.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (buf.length > MAX_BODY_BYTES) {
            throw new IOException("Body too large");
        }
        return new String(buf, StandardCharsets.UTF_8);
    }

    private void sendError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json; charset=UTF-8");
        PrintWriter w = resp.getWriter();
        w.print(new JSONObject().put("error", message).toString());
        w.flush();
    }

    private String getClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    private boolean isRateLimited(String ip) {
        long now = System.currentTimeMillis();
        cleanupRateLimiter(now);

        RateBucket bucket = rateLimiter.compute(ip, (k, v) -> {
            if (v == null || (now - v.windowStart) > rateWindowMs) {
                return new RateBucket(now);
            }
            return v;
        });

        if (bucket == null) {
            // If map is saturated and a new key could not be tracked, fail-safe.
            return true;
        }
        return bucket.count.incrementAndGet() > rateLimitPerMinute;
    }

    private void cleanupRateLimiter(long now) {
        if (rateLimiter.size() <= maxTrackedIps) {
            return;
        }

        // First pass: drop expired buckets.
        for (Map.Entry<String, RateBucket> e : rateLimiter.entrySet()) {
            if ((now - e.getValue().windowStart) > rateWindowMs) {
                rateLimiter.remove(e.getKey(), e.getValue());
            }
        }

        if (rateLimiter.size() <= maxTrackedIps) {
            return;
        }

        // Second pass: evict oldest buckets to keep memory bounded.
        int toEvict = rateLimiter.size() - maxTrackedIps;
        rateLimiter.entrySet().stream()
                .sorted(Comparator.comparingLong(e -> e.getValue().windowStart))
                .limit(toEvict)
                .forEach(e -> rateLimiter.remove(e.getKey(), e.getValue()));
    }

    private static int readIntConfig(String key, int defaultVal, int min, int max) {
        String raw = WebloggerConfig.getProperty(key);
        if (raw == null || raw.trim().isEmpty() || raw.contains("${")) {
            return defaultVal;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            if (parsed < min || parsed > max) {
                return defaultVal;
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            return defaultVal;
        }
    }

    private static class RateBucket {
        final long windowStart;
        final AtomicInteger count;

        RateBucket(long windowStart) {
            this.windowStart = windowStart;
            this.count = new AtomicInteger(0);
        }
    }
}
