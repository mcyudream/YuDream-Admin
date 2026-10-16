package online.yudream.base.infra.platform.plugin.service;

/**
 * 站点目录跨插件读取的宿主授权判定口。
 * <p>
 * SPI 不绑定按插件实例粒度的权限码（宿主没有 {@code plugin:{owner}:view} 这类授权语义）；
 * 跨插件读取是否放行由宿主授权服务裁决，实现可替换。默认实现按现行宿主规则
 * （插件管理查看权限 {@code platform:plugin:view}）判定且无认证主体时 fail-closed。
 */
public interface SiteFileReadAuthorizer {

    /** 当前认证主体是否可跨插件读取同一站点下的插件目录文件。 */
    boolean canReadOtherPluginFiles();
}
