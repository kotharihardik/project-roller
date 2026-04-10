package org.apache.roller.weblogger.business.feed;

import org.apache.roller.weblogger.pojos.WeblogEntry;

public interface FeedProcessingStep {

    String getName();

    boolean isEnabled();

    /**
     * Apply this step's transformation to the entry.
     *
     * @param entry  the entry to process (may be mutated in-place)
     * @return       the (possibly modified) entry — never {@code null}
     */
    WeblogEntry process(WeblogEntry entry);
}
