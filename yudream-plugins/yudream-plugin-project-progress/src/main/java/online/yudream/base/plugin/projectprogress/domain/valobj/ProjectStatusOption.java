package online.yudream.base.plugin.projectprogress.domain.valobj;

public record ProjectStatusOption(
        String code,
        String label,
        boolean terminal,
        int sort
) {

    public ProjectStatusOption {
        code = requireText(code, "状态编码不能为空").toUpperCase();
        label = requireText(label, "状态名称不能为空");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
