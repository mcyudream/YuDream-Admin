package online.yudream.base.plugin.spi.system.auth;

/**
 * 插件外部登录入口在宿主登录页的呈现方式。
 *
 * <p>由 {@link PluginExternalLoginProvider#presentation()} 声明，缺省为 {@link #ICON}，
 * 保持既有插件无需改动即可继续工作。</p>
 */
public enum PluginExternalLoginPresentation {

    /**
     * 登录表单下方的一排图标按钮（历史行为，也是缺省值）。
     * 每个 supportedType 渲染成一个圆形图标按钮，标题取 displayName。
     */
    ICON,

    /**
     * 与「账号密码登录 / Passkey 登录」并列的登录方式 Tab。
     * 每个 supportedType 渲染成一个 Tab：标签取 displayName，图标取 icon；
     * 选中该 Tab 后进入整行登录按钮，此时隐藏账号密码表单与 Passkey 入口。
     * 第三方账号绑定流程（bindingToken）与 Passkey 一样不参与 Tab 渲染。
     *
     * <p>Tab 顺序按 {@link PluginExternalLoginDescriptor#sort()} 升序与内置 Tab 同列排序，
     * 内置基线为「账号密码登录」100、「Passkey 登录」200；因此 sort 小于 100 的插件入口
     * 会排在账号密码之前（以统一身份认证为主的站点可把主入口置顶）。</p>
     */
    TAB
}
