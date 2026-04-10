package org.apache.roller.weblogger.business.breakdown;

import org.apache.roller.weblogger.config.WebloggerConfig;

/**
 * Factory that resolves a {@link BreakdownStrategy} by name.
 *
 * <h3>Pattern: Factory Method</h3>
 * <p>Centralises the mapping from a caller-supplied strategy name (e.g.
 * {@code "keyword"} or {@code "gemini"}) to a concrete implementation.
 * Adding a new strategy requires only one additional {@code case} in
 * {@link #get(String)} — the servlet and the rest of the system need no
 * changes.</p>
 *
 * <h3>Default strategy</h3>
 * <p>When the caller passes {@code null} or an empty string the factory falls
 * back to the value of {@code breakdown.defaultMethod} in
 * {@code roller.properties} (itself defaulting to {@code "keyword"}).
 * This keeps the common case cheap (no LLM) while still allowing the UI to
 * explicitly request the Gemini strategy.</p>
 */
public final class BreakdownStrategyFactory {

    /** Default property key for the preferred strategy when none is supplied. */
    private static final String PROP_DEFAULT_METHOD = "breakdown.defaultMethod";
    private static final String FALLBACK_METHOD     = "keyword";

    private BreakdownStrategyFactory() { /* static utility */ }

    /**
     * Returns a {@link BreakdownStrategy} for the given {@code method} name.
     *
     * <p>Recognised names (case-insensitive):</p>
     * <ul>
     *   <li>{@code "keyword"} — {@link KeywordClusterStrategy} (no LLM, default)</li>
     *   <li>{@code "gemini"}  — {@link GeminiBreakdownStrategy} (Gemini API, opt-in)</li>
     * </ul>
     *
     * @param method  strategy identifier; {@code null}/blank uses the configured default.
     * @return a ready-to-use {@link BreakdownStrategy}; never {@code null}.
     * @throws IllegalArgumentException if the name is non-null, non-blank, and unknown.
     */
    public static BreakdownStrategy get(String method) {
        String resolved;
        if (method == null || method.trim().isEmpty()) {
            resolved = WebloggerConfig.getProperty(PROP_DEFAULT_METHOD, FALLBACK_METHOD);
        } else {
            resolved = method.trim().toLowerCase();
        }

        switch (resolved) {
            case "keyword":
                return new KeywordClusterStrategy();

            case "gemini":
                return new GeminiBreakdownStrategy();

            default:
                throw new IllegalArgumentException(
                        "Unknown breakdown method '" + resolved + "'. " +
                        "Supported values: keyword, gemini.");
        }
    }
}
