package online.yudream.base.plugin.spi.system.auth;

import java.util.List;

/**
 * 插件外部登录提供方的展示元数据。providerCode 是稳定唯一编码（建议与插件 code 一致），
 * displayName/icon 供登录页渲染入口（图标按钮，或由
 * {@link PluginExternalLoginProvider#presentation()} 声明为 Tab 时的 Tab 标签与图标）；
 * supportedTypes 声明该提供方当前启用的平台类型
 * （如 cas 或 oidc），每个 type 在登录页渲染为一个入口，并出现在
 * /api/external-login/{providerCode}/{type}/authorize 路径中；sort 控制多个入口间的排序，
 * 同时对 {@link PluginExternalLoginPresentation#TAB} 的登录 Tab 与内置 Tab 一起排序
 * （内置基线：账号密码 100、Passkey 200，插件入口 sort 小于 100 即排到账号密码之前）。
 */
public record PluginExternalLoginDescriptor(String providerCode, String displayName, String icon,
                                            List<String> supportedTypes, int sort) {

    public PluginExternalLoginDescriptor {
        supportedTypes = supportedTypes == null ? List.of() : List.copyOf(supportedTypes);
    }
}
