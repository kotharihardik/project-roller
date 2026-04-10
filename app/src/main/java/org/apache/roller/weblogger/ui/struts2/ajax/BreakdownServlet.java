package org.apache.roller.weblogger.ui.struts2.ajax;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.WeblogEntryManager;
import org.apache.roller.weblogger.business.WebloggerFactory;
import org.apache.roller.weblogger.business.breakdown.BreakdownException;
import org.apache.roller.weblogger.business.breakdown.BreakdownStrategy;
import org.apache.roller.weblogger.business.breakdown.BreakdownStrategyFactory;
import org.apache.roller.weblogger.business.breakdown.ConversationBreakdown;
import org.apache.roller.weblogger.business.breakdown.Theme;
import org.apache.roller.weblogger.pojos.CommentSearchCriteria;
import org.apache.roller.weblogger.pojos.WeblogEntry;
import org.apache.roller.weblogger.pojos.WeblogEntryComment;
import org.apache.roller.weblogger.ui.core.RollerSession;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class BreakdownServlet extends HttpServlet {

    private static final Log LOG = LogFactory.getLog(BreakdownServlet.class);

    /** Maximum breakdown calls per session per minute (guards Gemini quota). */
    private static final int RATE_LIMIT = 10;

    /** Session-ID --> [callCount, windowStartMs] */
    private final ConcurrentHashMap<String, long[]> rateLimitMap = new ConcurrentHashMap<>();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        setCorsHeaders(resp);
        resp.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    // POST — generate breakdown

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        setCorsHeaders(resp);
        req.setCharacterEncoding("UTF-8");

        HttpSession session = req.getSession(false);
        RollerSession rollerSession = (session != null)
                ? (RollerSession) session.getAttribute(RollerSession.ROLLER_SESSION)
                : null;
        boolean loggedIn = rollerSession != null
                && rollerSession.getAuthenticatedUser() != null;

        if (!loggedIn) {
            // Debug: surface session state so the UI can display it
            JSONObject debugInfo = new JSONObject();
            debugInfo.put("error", "Login required to generate a breakdown.");
            debugInfo.put("debug_sessionExists", session != null);
            debugInfo.put("debug_rollerSessionFound", rollerSession != null);
            debugInfo.put("debug_authenticatedUser",
                    (rollerSession != null && rollerSession.getAuthenticatedUser() != null)
                    ? rollerSession.getAuthenticatedUser().getUserName() : "null");
            debugInfo.put("debug_principal",
                    req.getUserPrincipal() != null ? req.getUserPrincipal().getName() : "null");
            debugInfo.put("debug_remoteUser",
                    req.getRemoteUser() != null ? req.getRemoteUser() : "null");
            writeJson(resp, HttpServletResponse.SC_UNAUTHORIZED, debugInfo.toString());
            return;
        }

        if (session != null && !isWithinRateLimit(session.getId())) {
            writeError(resp, 429,
                    "Rate limit exceeded. Maximum " + RATE_LIMIT +
                    " breakdown requests per minute.");
            return;
        }

        String entryId = req.getParameter("entryId");
        String method  = req.getParameter("method");   // null --> factory default

        if (entryId == null || entryId.trim().isEmpty()) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Missing required parameter: entryId");
            return;
        }

        if (method != null && !method.trim().isEmpty()) {
            String m = method.trim().toLowerCase();
            if (!m.equals("keyword") && !m.equals("gemini")) {
                writeError(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "Unknown method '" + method +
                        "'. Supported values: keyword, gemini.");
                return;
            }
        }

        List<WeblogEntryComment> allComments;
        try {
            WeblogEntryManager wmgr = WebloggerFactory.getWeblogger()
                    .getWeblogEntryManager();
            WeblogEntry entry = wmgr.getWeblogEntry(entryId.trim());
            if (entry == null) {
                writeError(resp, HttpServletResponse.SC_NOT_FOUND,
                        "Entry not found: " + entryId);
                return;
            }
            CommentSearchCriteria csc = new CommentSearchCriteria();
            csc.setEntry(entry);
            csc.setReverseChrono(false);
            allComments = wmgr.getComments(csc);
        } catch (WebloggerException e) {
            LOG.error("Error loading comments for breakdown", e);
            writeError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Error loading comments.");
            return;
        }

        BreakdownStrategy strategy;
        try {
            strategy = BreakdownStrategyFactory.get(method);
        } catch (IllegalArgumentException e) {
            writeError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        }

        ConversationBreakdown breakdown;
        try {
            breakdown = strategy.generate(allComments);
        } catch (BreakdownException e) {
            int statusHint = e.getStatusCode();
            if (statusHint == 429) {
                writeError(resp, 429, e.getMessage());
            } else {
                LOG.error("BreakdownStrategy [" + strategy.getStrategyName() +
                          "] failed: " + e.getMessage(), e);
                writeError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "Breakdown unavailable: " + e.getMessage());
            }
            return;
        }

        writeJson(resp, HttpServletResponse.SC_OK, toJson(breakdown));
    }

    private static String toJson(ConversationBreakdown bd) {
        JSONObject out = new JSONObject();
        out.put("strategy", bd.getStrategyName());
        out.put("recap",    bd.getOverallRecap());

        JSONArray themesArr = new JSONArray();
        for (Theme t : bd.getThemes()) {
            JSONObject to = new JSONObject();
            to.put("name", t.getName());
            JSONArray repsArr = new JSONArray();
            for (String r : t.getRepresentativeComments()) {
                repsArr.put(r);
            }
            to.put("representatives", repsArr);
            themesArr.put(to);
        }
        out.put("themes", themesArr);
        return out.toString();
    }

    private boolean isWithinRateLimit(String sessionId) {
        long now      = System.currentTimeMillis();
        long windowMs = 60_000L;
        long[] slot   = rateLimitMap.compute(sessionId, (k, v) -> {
            if (v == null || (now - v[1]) > windowMs) return new long[]{1, now};
            v[0]++;
            return v;
        });
        return slot[0] <= RATE_LIMIT;
    }

    private static void setCorsHeaders(HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin", "*");
    }

    private static void writeJson(HttpServletResponse resp, int status, String body)
            throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try (PrintWriter pw = resp.getWriter()) {
            pw.write(body);
        }
    }

    private static void writeError(HttpServletResponse resp, int status, String msg)
            throws IOException {
        writeJson(resp, status, new JSONObject().put("error", msg).toString());
    }
}
