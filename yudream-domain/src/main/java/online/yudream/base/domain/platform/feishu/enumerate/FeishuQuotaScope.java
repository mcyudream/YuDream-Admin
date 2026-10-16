package online.yudream.base.domain.platform.feishu.enumerate;

/**
 * 飞书调用额度作用域：按能力内的 API 模块分别记账。
 * <p>
 * 当前只有云文档模块（本工作项范围）；后续模块接入时新增枚举值并各自拥有分钟/日窗口，
 * 不复用同一计数桶，避免不同模块互相挤占额度。
 */
public enum FeishuQuotaScope {

    CLOUD_DOC("cloud-doc", "飞书云文档");

    private final String code;
    private final String label;

    FeishuQuotaScope(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }
}
