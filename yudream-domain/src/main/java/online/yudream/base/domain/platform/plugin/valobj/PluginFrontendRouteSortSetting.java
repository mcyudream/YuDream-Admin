package online.yudream.base.domain.platform.plugin.valobj;

public record PluginFrontendRouteSortSetting(
        String path,
        String name,
        Integer sort,
        Integer parentSort
) {
    public boolean matches(PluginFrontendRouteInfo route) {
        if (route == null) {
            return false;
        }
        return same(path, route.path()) || same(name, route.name());
    }

    private static boolean same(String left, String right) {
        return left != null && !left.isBlank() && left.equals(right);
    }
}
