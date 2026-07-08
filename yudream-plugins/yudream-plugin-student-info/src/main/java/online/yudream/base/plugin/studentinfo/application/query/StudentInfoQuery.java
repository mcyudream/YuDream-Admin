package online.yudream.base.plugin.studentinfo.application.query;

public record StudentInfoQuery(
        String keyword,
        String college,
        String className,
        int page,
        int size
) {

    public int safePage() {
        return Math.max(page, 1);
    }

    public int safeSize() {
        return Math.min(Math.max(size, 1), 100);
    }
}
