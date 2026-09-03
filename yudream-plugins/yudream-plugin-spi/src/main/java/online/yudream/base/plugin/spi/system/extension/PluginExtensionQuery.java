package online.yudream.base.plugin.spi.system.extension;

import java.util.List;

/**
 * 扩展点查询端口：按扩展点接口查询当前所有已启用插件注册的扩展实现，
 * 返回值已按注册优先级从小到大排序（同优先级按注册先后）。
 * 宿主应用层通过该端口消费扩展，无需感知插件基础设施；
 * 扩展点接口既可以由宿主定义（如 system/auth 下的契约），也可以由
 * provider 插件在自己的 *.api 包中定义。
 */
public interface PluginExtensionQuery {

    <I> List<I> extensions(Class<I> extensionPoint);
}
