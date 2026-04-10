package org.apache.roller.weblogger.business.chatbot;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.config.WebloggerConfig;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatbotStrategyFactory {

    private static final Log LOG = LogFactory.getLog(ChatbotStrategyFactory.class);

    private static final String PROP_DEFAULT = "chatbot.defaultStrategy";
    private static final String FALLBACK_DEFAULT = "rag";

    // Singleton instances (lazy, thread-safe)
    private static final ConcurrentHashMap<String, ChatbotAnsweringStrategy> instances =
            new ConcurrentHashMap<>();

    /** Registry of strategy name --> supplier. Add new strategies here. */
    private static final Map<String, StrategySupplier> STRATEGY_REGISTRY;
    static {
        Map<String, StrategySupplier> map = new LinkedHashMap<>();
        map.put("longcontext", LongContextStrategy::new);
        map.put("rag",         RAGStrategy::new);
        STRATEGY_REGISTRY = Collections.unmodifiableMap(map);
    }

    @FunctionalInterface
    private interface StrategySupplier {
        ChatbotAnsweringStrategy create();
    }

    private ChatbotStrategyFactory() { }

    /**
     * Returns the strategy matching the given name, or the default strategy
     * if the name is null/unknown.
     */
    public static ChatbotAnsweringStrategy getStrategy(String name) {
        if (name == null || name.trim().isEmpty()) {
            return getDefaultStrategy();
        }
        String key = name.trim().toLowerCase();
        StrategySupplier supplier = STRATEGY_REGISTRY.get(key);
        if (supplier == null) {
            LOG.warn("Unknown chatbot strategy '" + key + "', falling back to default");
            return getDefaultStrategy();
        }
        return instances.computeIfAbsent(key, k -> {
            LOG.info("Instantiating chatbot strategy: " + k);
            return supplier.create();
        });
    }

    /** Returns the default strategy as configured in roller.properties. */
    public static ChatbotAnsweringStrategy getDefaultStrategy() {
        return instances.computeIfAbsent("__default__", k -> createDefault());
    }

    /** Returns metadata for all registered strategies (for the UI dropdown). */
    public static List<Map<String, String>> getAvailableStrategies() {
        List<Map<String, String>> list = new ArrayList<>();
        for (Map.Entry<String, StrategySupplier> entry : STRATEGY_REGISTRY.entrySet()) {
            ChatbotAnsweringStrategy s = getStrategy(entry.getKey());
            Map<String, String> info = new LinkedHashMap<>();
            info.put("name", s.getStrategyName());
            info.put("displayName", s.getDisplayName());
            list.add(info);
        }
        return list;
    }

    private static ChatbotAnsweringStrategy createDefault() {
        String dflt = WebloggerConfig.getProperty(PROP_DEFAULT);
        if (dflt == null || dflt.trim().isEmpty()) dflt = FALLBACK_DEFAULT;
        dflt = dflt.trim().toLowerCase();
        StrategySupplier supplier = STRATEGY_REGISTRY.get(dflt);
        if (supplier == null) {
            LOG.warn("Unknown default chatbot strategy '" + dflt + "'; using '" + FALLBACK_DEFAULT + "'");
            dflt = FALLBACK_DEFAULT;
            supplier = STRATEGY_REGISTRY.get(FALLBACK_DEFAULT);
        }
        LOG.info("Creating default chatbot strategy: " + dflt);
        return supplier.create();
    }
}
