package org.apache.roller.weblogger.business.notification;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.business.notification.channel.NotificationChannel;
import org.apache.roller.weblogger.business.notification.model.NotificationEvent;
import org.apache.roller.weblogger.config.WebloggerConfig;

public class NotificationDispatcher {

    private static final Log LOG = LogFactory.getLog(NotificationDispatcher.class);

    // Thread-safe channel list since dispatch may execute on background threads
    private final List<NotificationChannel> channels = new CopyOnWriteArrayList<>();

    // Executor used for async dispatch; may be null if async disabled
    private final ExecutorService executor;

    // Whether dispatcher submits tasks asynchronously
    private final boolean async;

    public NotificationDispatcher() {
        String mode = WebloggerConfig.getProperty("notification.dispatch.mode");
        this.async = mode == null || "async".equalsIgnoreCase(mode.trim());

        ExecutorService ex = null;
        if (this.async) {
            int pool = 4;
            try {
                String ps = WebloggerConfig.getProperty("notification.dispatch.poolsize");
                if (ps != null && !ps.trim().isEmpty()) {
                    pool = Integer.parseInt(ps.trim());
                }
            } catch (Exception e) {
                LOG.warn("Invalid notification.dispatch.poolsize, defaulting to 4", e);
            }
            ex = Executors.newFixedThreadPool(Math.max(1, pool));
            LOG.info("NotificationDispatcher configured for async dispatch (poolSize=" + Math.max(1, pool) + ")");
        } else {
            LOG.info("NotificationDispatcher configured for synchronous dispatch");
        }
        this.executor = ex;

        // Shutdown hook to attempt graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                shutdown();
            } catch (Exception e) {
                LOG.warn("Error during NotificationDispatcher shutdown", e);
            }
        }));
    }

    public void registerChannel(NotificationChannel channel) {
        channels.add(channel);
        LOG.info("Registered notification channel: " + channel.getChannelName());
    }

    public void dispatch(NotificationEvent event) {
        for (NotificationChannel channel : channels) {
            if (!channel.isEnabled()) {
                LOG.debug("Channel [" + channel.getChannelName() + "] is disabled, skipping");
                continue;
            }

            if (async && executor != null) {
                try {
                    executor.submit(() -> {
                        try {
                            channel.send(event);
                            LOG.debug("Event dispatched to channel: " + channel.getChannelName());
                        } catch (Exception e) {
                            LOG.error("Channel [" + channel.getChannelName() + "] failed: " + e.getMessage(), e);
                        }
                    });
                } catch (Exception e) {
                    LOG.error("Executor failed to submit notification task, falling back to sync send", e);
                    try {
                        channel.send(event);
                    } catch (Exception ex) {
                        LOG.error("Channel [" + channel.getChannelName() + "] failed on fallback: " + ex.getMessage(), ex);
                    }
                }
            } else {
                try {
                    channel.send(event);
                    LOG.debug("Event dispatched to channel: " + channel.getChannelName());
                } catch (Exception e) {
                    LOG.error("Channel [" + channel.getChannelName() + "] failed: " + e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Gracefully shutdown executor service.
     */
    public void shutdown() {
        if (executor == null) return;
        try {
            LOG.info("Shutting down NotificationDispatcher executor...");
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                LOG.info("Executor did not terminate in time; forcing shutdown now");
                executor.shutdownNow();
            }
        } catch (InterruptedException ie) {
            LOG.warn("Interrupted during NotificationDispatcher shutdown, forcing shutdown", ie);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}