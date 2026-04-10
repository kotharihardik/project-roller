package org.apache.roller.weblogger.business.dashboard;

import java.util.LinkedHashMap;
import java.util.Map;

public class DashboardViewFactory {

    private final Map<String, DashboardViewDefinition> registry = new LinkedHashMap<>();

    public DashboardViewFactory() {
        // Register built-in views
        register(new MinimalistViewDefinition());
        register(new FullViewDefinition());
    }

    /**
     * Registers a view definition so it can be created by name.
     * @param viewDef the view definition to register
     */
    public void register(DashboardViewDefinition viewDef) {
        registry.put(viewDef.getViewName(), viewDef);
    }

    /**
     *
     * @param viewType the type string, e.g. "minimalist" or "full"
     * @return the matching DashboardViewDefinition
     */
    public DashboardViewDefinition createView(String viewType) {
        DashboardViewDefinition view = registry.get(viewType);
        if (view == null) {
            // Default fallback
            view = registry.get("full");
        }
        return view;
    }

    /**
     * Returns all registered view type names.
     */
    public String[] getAvailableViewTypes() {
        return registry.keySet().toArray(new String[0]);
    }
}
