package online.yudream.base.domain.platform.plugin.service;

import online.yudream.base.domain.platform.plugin.aggregate .PluginModule;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendAssetInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginDescriptorInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendModuleInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpDispatchRequest;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpDispatchResult;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpEndpointInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginDashboardCardInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginPermissionInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginCommandInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginCommandTestResult;
import online.yudream.base.domain.platform.plugin.valobj.PluginDevDirectoryBrowseInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginDevProjectInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginRuntimeAssets;
import online.yudream.base.domain.platform.plugin.valobj.PluginScaffoldResult;
import online.yudream.base.domain.platform.plugin.valobj.PluginScaffoldSpec;

import java.util.List;
import java.util.Optional;
import java.nio.file.Path;

public interface PluginRuntimeGateway {

    List<PluginDescriptorInfo> discover();

    Optional<PluginDescriptorInfo> describe(Path jarPath);

    void load(PluginModule module);

    void enable(PluginModule module);

    void disable(String code);

    void unload(String code);

    boolean loaded(String code);

    boolean enabled(String code);

    List<PluginPermissionInfo> permissions(String code);

    List<PluginFrontendModuleInfo> frontendModules();

    List<PluginDashboardCardInfo> dashboardCards();

    List<PluginHttpEndpointInfo> httpEndpoints();

    List<PluginCommandInfo> commands();

    Optional<PluginFrontendAssetInfo> frontendAsset(String code, String assetPath);

    PluginHttpDispatchResult dispatch(PluginHttpDispatchRequest request);

    /** 单个插件运行时贡献资产快照，供开发者工具面板聚合展示。 */
    PluginRuntimeAssets runtimeAssets(String code);

    /** 插件是否来自开发模式的源码目录加载。 */
    default boolean devModePlugin(String code) {
        return false;
    }

    /** 开发模式是否启用（独立于是否配置了项目）。 */
    default boolean devModeEnabled() {
        return false;
    }

    /** 开发模式配置的项目清单，未启用开发模式时为空。 */
    default List<PluginDevProjectInfo> devModeProjects() {
        return List.of();
    }

    /** 开发项目管理视图：不受启用开关过滤，供面板在开发模式关闭时预登记。 */
    default List<PluginDevProjectInfo> managedDevProjects() {
        return List.of();
    }

    /** 宿主运行形态：SOURCE（IDE / spring-boot:run）或 JAR（java -jar）。 */
    default String hostRunMode() {
        return "JAR";
    }

    /** 开发模式生效值是否来自运行形态自动检测（而非显式配置）。 */
    default boolean devModeAutoDetected() {
        return false;
    }

    /** 面板维护的开发项目清单文件绝对路径，未启用文件源时为 null。 */
    default String devProjectStoreFile() {
        return null;
    }

    /**
     * 登记开发模式项目到面板清单文件：path 必填；code 为空时从目录内 plugin.yml 推断。
     * 返回登记后的项目快照；路径不存在或与配置文件登记冲突时抛出业务异常。
     */
    default PluginDevProjectInfo registerDevProject(String code, String path, String frontendDist,
                                                    boolean autoCompile, String compileCommand) {
        throw new UnsupportedOperationException("当前运行时网关不支持开发项目管理");
    }

    /** 移除面板登记的开发模式项目；配置文件登记的项目不可移除。 */
    default void removeDevProject(String code) {
        throw new UnsupportedOperationException("当前运行时网关不支持开发项目管理");
    }

    /**
     * 浏览宿主机目录，供面板登记开发项目时选择插件源码目录。
     * 仅列子目录与插件模块标记，不读取文件内容；path 为空时返回文件系统根（Windows 盘符）。
     */
    default PluginDevDirectoryBrowseInfo browseDevDirectories(String path) {
        throw new UnsupportedOperationException("当前运行时网关不支持目录浏览");
    }

    /**
     * 在宿主机生成插件 Maven 模块骨架（pom.xml/plugin.yml/入口类/分层分包目录）。
     * 目标目录已存在且非空时抛出业务异常；只写文件，不触发编译与登记。
     */
    default PluginScaffoldResult scaffoldPlugin(PluginScaffoldSpec spec) {
        throw new UnsupportedOperationException("当前运行时网关不支持插件脚手架生成");
    }

    /**
     * 开发调试用：在指定插件作用域内同步模拟触发指令处理器，绕过权限与匿名检查并捕获异常。
     * content 用于构造模拟事件原文，为空时按指令与参数拼接。
     */
    default PluginCommandTestResult testCommand(String pluginCode, String command, List<String> arguments, String content) {
        throw new UnsupportedOperationException("当前运行时网关不支持指令模拟");
    }
}
