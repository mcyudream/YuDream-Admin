package online.yudream.base.domain.platform.plugin.enumerate;

/** 插件市场源类型：LOCAL 仅内置本机源；STATIC_INDEX 为 legacy 静态 index.json；V2_API 为交互式协议。 */
public enum MarketSourceType {
    LOCAL,
    STATIC_INDEX,
    V2_API
}
