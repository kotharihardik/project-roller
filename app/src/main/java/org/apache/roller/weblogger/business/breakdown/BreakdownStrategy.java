package org.apache.roller.weblogger.business.breakdown;

import java.util.List;

import org.apache.roller.weblogger.pojos.WeblogEntryComment;

/**
 * Strategy interface for generating a {@link ConversationBreakdown} from a
 * list of weblog-entry comments.
 *
 * <h3>Pattern: Strategy</h3>
 * <p>Each implementing class encapsulates one self-contained algorithm for
 * identifying themes, picking representative comments, and writing a recap.
 * The {@link BreakdownStrategyFactory} selects the right implementation at
 * runtime based on a caller-supplied {@code method} name.  The caller
 * ({@link org.apache.roller.weblogger.ui.struts2.ajax.BreakdownServlet}) is
 * completely decoupled from the concrete strategy — it only holds a reference
 * to this interface.</p>
 *
 * <h3>Adding a new strategy</h3>
 * <ol>
 *   <li>Create a class in this package that implements {@code BreakdownStrategy}.</li>
 *   <li>Register its key in {@link BreakdownStrategyFactory#get(String)}.</li>
 * </ol>
 *
 * <h3>Thread safety</h3>
 * <p>Implementations <strong>must</strong> be stateless (or thread-safe) — a
 * single instance may be reused across concurrent HTTP requests.</p>
 */
public interface BreakdownStrategy {

    /**
     * Short identifier for this strategy, shown in the JSON response and UI.
     * Examples: {@code "keyword"}, {@code "gemini"}.
     *
     * @return non-null, non-empty identifier string.
     */
    String getStrategyName();

    /**
     * Analyses the supplied comments and returns a structured breakdown.
     *
     * <p>Implementations should:</p>
     * <ul>
     *   <li>Filter to only {@code APPROVED} or {@code PENDING} comments with
     *       non-empty content.</li>
     *   <li>Return a valid {@link ConversationBreakdown} even when the list is
     *       empty or too small to form meaningful themes.</li>
     * </ul>
     *
     * @param comments full comment list for a blog entry; must not be {@code null}.
     * @return a populated {@link ConversationBreakdown}; never {@code null}.
     * @throws BreakdownException if the strategy encounters an unrecoverable error
     *                            (e.g. a remote API call failure).
     */
    ConversationBreakdown generate(List<WeblogEntryComment> comments)
            throws BreakdownException;
}
