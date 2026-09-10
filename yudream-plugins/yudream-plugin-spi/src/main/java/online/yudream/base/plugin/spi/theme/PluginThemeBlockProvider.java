package online.yudream.base.plugin.spi.theme;

import java.util.Set;

/**
 * 主题块提供者：插件向公开站主题模板贡献动态数据的扩展点。
 *
 * <p>插件在 {@code onEnable} 中通过
 * {@code context.registerExtension(PluginThemeBlockProvider.class, provider)}
 * 注册；禁用/卸载时宿主自动回收。主题模板以
 * {@code data-yb-for="item in blocks.{code}.<字段>"}、
 * {@code data-yb-if="blocks.{code}"} 等形式消费返回的数据；
 * 块不可用（插件未安装/未启用/不支持当前主题/执行异常）时模板引用为空，
 * 主题应自行用 {@code data-yb-if} 做降级。</p>
 *
 * <p><b>数据安全约定：</b>返回对象会被匿名公开站渲染，实现方必须只暴露
 * 可公开字段（标题、摘要、状态等），严禁携带内部 ID 之外的敏感信息。
 * 返回对象必须可被 Jackson 序列化为 JSON（Map/record/POJO 均可）。</p>
 */
public interface PluginThemeBlockProvider {

    /** 块编码，插件内唯一；模板经 {@code blocks.{code}} 引用。 */
    String code();

    /** 块展示名（后台诊断展示用）。 */
    String name();

    /**
     * 支持的主题 code 集合；返回空集合表示所有主题可用。
     * 不在集合内的主题激活时宿主不会调用本提供者。
     */
    default Set<String> supportedThemes() {
        return Set.of();
    }

    /**
     * 产出块数据。每次公开站模板渲染按需调用，实现应走自身缓存/快照，
     * 不得在本方法内做实时外部调用（如实时 ping）。
     *
     * @param ctx 调用上下文（激活主题与条数上限）
     * @return JSON 可序列化数据；返回 {@code null} 视为块不可用
     */
    Object data(PluginThemeBlockContext ctx);
}
