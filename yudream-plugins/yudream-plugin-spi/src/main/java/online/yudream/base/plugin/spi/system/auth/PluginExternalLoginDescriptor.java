package online.yudream.base.plugin.spi.system.auth;

import java.util.List;

/**
 * 插件外部登录提供方的展示元数据。providerCode 是稳定唯一编码（建议与插件 code 一致），
 * displayName/icon 供登录页渲染入口按钮；supportedTypes 声明该提供方当前启用的平台类型
 * （如 cas 或 oidc），每个 type 在登录页渲染为一个入口，并出现在
 * /api/external-login/{providerCode}/{type}/authorize 路径中；sort 控制多个入口间的排序。
 */
public record PluginExternalLoginDescriptor(String providerCode, String displayName, String icon,
                                            List<String> supportedTypes, int sort) {

    public PluginExternalLoginDescriptor {
        supportedTypes = supportedTypes == null ? List.of() : List.copyOf(supportedTypes);
    }
}
