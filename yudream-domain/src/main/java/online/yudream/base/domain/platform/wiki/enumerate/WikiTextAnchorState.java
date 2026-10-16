package online.yudream.base.domain.platform.wiki.enumerate;

/**
 * 评论文本锚点在「当前已发布版本」上的可定位状态（读取侧投影，不落库）。
 * <p>
 * 判定只使用服务端盖章的 contentHash 与正文文本：只有引用文本仍能按上下文
 * <b>唯一</b>命中的锚点才可能被高亮公开端展示；锚点失配不会隐藏评论本身。
 * 线协议使用小写名称（active/reanchorable/orphaned）。
 */
public enum WikiTextAnchorState {
    /** 命中当前已发布版本且内容哈希与盖章一致，可高亮。 */
    ACTIVE("active"),
    /** 页面已改版（哈希不一致），但引用文本仍能唯一命中，可由作者重新定锚。 */
    REANCHORABLE("reanchorable"),
    /** 引用文本无法在当前已发布版本中唯一命中（含歧义与已删除），仅保留评论不做高亮。 */
    ORPHANED("orphaned");

    private final String wireValue;

    WikiTextAnchorState(String wireValue) {
        this.wireValue = wireValue;
    }

    /** 线协议名称（小写）。 */
    public String wireValue() {
        return wireValue;
    }

    /** 解析线协议名或枚举名；未知或空返回 null。 */
    public static WikiTextAnchorState of(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        for (WikiTextAnchorState state : values()) {
            if (state.wireValue.equalsIgnoreCase(normalized) || state.name().equalsIgnoreCase(normalized)) {
                return state;
            }
        }
        return null;
    }
}
