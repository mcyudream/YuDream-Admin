package online.yudream.base.plugin.spi.theme;

import java.util.List;

/**
 * 站点贡献者：插件向公开静态站点注册菜单项与插槽静态片段的扩展点。
 *
 * <p>插件在 {@code onEnable} 中通过
 * {@code context.registerExtension(PluginSiteContribution.class, provider)}
 * 注册；禁用/卸载时宿主自动回收，贡献随之从模板上下文消失。</p>
 *
 * <p>消费方式：模板上下文聚合后，菜单项进入 {@code contributions.menu}，
 * 插槽片段进入 {@code contributions.slots.<slot>}；静态页以
 * {@code <div data-yb-slot="home.hero-bottom"></div>} 占位，由运行时库填充。
 * 片段 HTML 由宿主做净化（剥离 script 与事件属性），实现方仍应只输出
 * 站点可公开的标记与文案。</p>
 */
public interface PluginSiteContribution {

    /** 插件编码（用于来源标记与排序去重）。 */
    String pluginCode();

    /** 注册的导航菜单项；无则返回空列表。 */
    default List<MenuItem> menuItems() {
        return List.of();
    }

    /** 注册的插槽静态片段；无则返回空列表。 */
    default List<SlotFragment> slotFragments() {
        return List.of();
    }

    /** 导航菜单项：label 为展示文案，path 为站点内路径，order 越小越靠前。 */
    record MenuItem(String label, String path, String icon, int order) {
    }

    /** 插槽片段：slot 为占位标识（如 home.hero-bottom），html 为静态片段内容。 */
    record SlotFragment(String slot, String html, String title, int order) {
    }
}
