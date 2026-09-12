package online.yudream.base.domain.platform.plugin.valobj;

import online.yudream.base.domain.platform.plugin.enumerate.MarketSourceType;

/**
 * 市场源寻址引用：rootUrl 为源基址（STATIC_INDEX 指向 index.json，V2_API 指向源根，
 * 客户端拼接 /api/v2/...），token 可空（私有源 Bearer 凭据）。网关按 type 分发协议。
 */
public record PluginStoreSourceRef(String rootUrl, String token, MarketSourceType type) {

    public PluginStoreSourceRef(String rootUrl, String token) {
        this(rootUrl, token, MarketSourceType.STATIC_INDEX);
    }

    public PluginStoreSourceRef {
        type = type == null ? MarketSourceType.STATIC_INDEX : type;
    }
}
