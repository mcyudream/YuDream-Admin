package online.yudream.base.plugin.spi.theme;

/**
 * 插件主题作用范围：决定主题样式注入到哪一侧界面。
 */
public enum PluginThemeScope {

    /** 公开站（/site 及插件公开页），主题 CSS 以 .site-page/.site-chrome 容器为作用域。 */
    SITE,

    /** 管理后台，主题 CSS 以 :root/.dark 全局变量为作用域。 */
    ADMIN
}
