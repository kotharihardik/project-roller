package org.apache.roller.weblogger.business.feed;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.business.feed.steps.AutoTagGenerationStep;
import org.apache.roller.weblogger.business.feed.steps.ProfanityFilterStep;
import org.apache.roller.weblogger.business.feed.steps.WordCountLimiterStep;
import org.apache.roller.weblogger.config.WebloggerConfig;
import org.apache.roller.weblogger.pojos.WeblogEntry;
public final class FeedProcessingPipeline {

    private static final Log log = LogFactory.getLog(FeedProcessingPipeline.class);

    private static final FeedProcessingPipeline INSTANCE = new FeedProcessingPipeline();

    /** Ordered list of candidate steps.  Each step decides for itself
     *  whether it is enabled via {@link FeedProcessingStep#isEnabled()}. */
    private final List<FeedProcessingStep> steps;

    private FeedProcessingPipeline() {
        steps = Arrays.asList(
            new ProfanityFilterStep(),
            new WordCountLimiterStep(),
            new AutoTagGenerationStep()
        );
    }

    public static FeedProcessingPipeline getInstance() {
        return INSTANCE;
    }

    /**
     * Run the entry through all enabled steps in order.
     *
     * @param entry  the entry about to be saved
     * @return       the (possibly modified) entry
     */
    public WeblogEntry process(WeblogEntry entry) {
        if (!WebloggerConfig.getBooleanProperty("feed.pipeline.enabled")) {
            log.debug("Feed processing pipeline is disabled — skipping all steps.");
            return entry;
        }

        WeblogEntry result = entry;
        for (FeedProcessingStep step : steps) {
            if (step.isEnabled()) {
                log.debug("Applying feed pipeline step: " + step.getName());
                result = step.process(result);
            } else {
                log.debug("Skipping disabled feed pipeline step: " + step.getName());
            }
        }
        return result;
    }
}