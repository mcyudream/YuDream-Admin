package online.yudream.base.domain.platform.plugin.valobj;

/**
 * 市场源寻址引用：rootUrl 为目录根（index.json 所在地址），token 可空（私有源 Bearer 凭据）。
 * 网关负责校验 rootUrl 合法性；token 不得写入日志或对外返回。
 */
public record PluginStoreSourceRef(String rootUrl, String token) {
}
