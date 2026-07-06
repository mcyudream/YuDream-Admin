package online.yudream.base.domain.system.dashboard.valobj;

public record DashboardGridPlacement(
        int x,
        int y,
        int w,
        int h
) {
    public DashboardGridPlacement {
        x = Math.max(x, 0);
        y = Math.max(y, 0);
        w = Math.max(w, 1);
        h = Math.max(h, 1);
    }
}
