package online.yudream.base.application.platform.ai.dto;

import java.util.List;

public record AiFrontendContextDTO(Route route, List<Route> routes, List<Action> actions,
                                   Boolean navigationToolsEnabled, Boolean highlightToolsEnabled,
                                   List<String> tours, Boolean tourToolsEnabled) {
    public AiFrontendContextDTO(Route route, List<Route> routes, List<Action> actions) {
        this(route, routes, actions, null, null, null, null);
    }
    public AiFrontendContextDTO(Route route, List<Route> routes, List<Action> actions,
                                Boolean navigationToolsEnabled, Boolean highlightToolsEnabled) {
        this(route, routes, actions, navigationToolsEnabled, highlightToolsEnabled, null, null);
    }
    public record Route(String name, String title, String path) { }
    public record Action(String code, String label, String routeName) { }
}
