package online.yudream.base.interfaces.platform.chat.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ChatFrontendContextRequest(@Valid Route route,
                                         @Valid @Size(max = 100) List<Route> routes,
                                         @Valid @Size(max = 200) List<Action> actions,
                                         Boolean navigationToolsEnabled, Boolean highlightToolsEnabled,
                                         @Size(max = 10) List<@Size(max = 180) @Pattern(regexp = "^[a-z][a-z0-9:._-]{1,179}$") String> tours,
                                         Boolean tourToolsEnabled) {
    public ChatFrontendContextRequest(Route route, List<Route> routes, List<Action> actions) {
        this(route, routes, actions, null, null, null, null);
    }
    public ChatFrontendContextRequest(Route route, List<Route> routes, List<Action> actions,
                                      Boolean navigationToolsEnabled, Boolean highlightToolsEnabled) {
        this(route, routes, actions, navigationToolsEnabled, highlightToolsEnabled, null, null);
    }
    public record Route(@NotBlank @Size(max = 180) String name,
                        @Size(max = 120) String title,
                        @Size(max = 300) @Pattern(regexp = "^/(?!/)[^?#:]*$") String path) { }
    public record Action(@NotBlank @Size(max = 180) @Pattern(regexp = "^[a-z][a-z0-9:._-]{1,179}$") String code,
                         @Size(max = 120) String label,
                         @NotBlank @Size(max = 180) String routeName) { }
}
