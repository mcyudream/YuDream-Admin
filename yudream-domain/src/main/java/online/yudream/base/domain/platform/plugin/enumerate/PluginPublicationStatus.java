package online.yudream.base.domain.platform.plugin.enumerate;

/** 插件市场发布物状态：待审核 → 发布/拒绝；已发布可下架。 */
public enum PluginPublicationStatus {
    PENDING,
    PUBLISHED,
    REJECTED,
    REVOKED
}
