package online.yudream.base.domain.platform.ai.valobj;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统工具模块归属与展示元数据：平台工具名 → 模块编码的唯一换算点。
 * 模块是「按需加载」的单位：常驻模块（alwaysOn）的工具始终进入模型视野，
 * 其余模块由 load_tools 元工具在运行时按模块加载后才可见。
 */
public final class AiToolModules {

    /** 工具目录模块：只承载 load_tools 元工具，不可关闭。 */
    public static final String MODULE_META = "meta";
    public static final String MODULE_WEB = "web";
    public static final String MODULE_CMS = "cms";
    public static final String MODULE_WIKI = "wiki";
    public static final String MODULE_UI = "ui";
    /** 无模块归属的宿主工具兜底模块。 */
    public static final String MODULE_GENERAL = "general";
    /** Python 自定义工具（工作流路径），仅管理端展示。 */
    public static final String MODULE_CUSTOM = "custom";
    public static final String PLUGIN_PREFIX = "plugin:";
    public static final String MCP_PREFIX = "mcp:";

    /** load_tools 元工具的平台工具名（模型侧同名 safeName）。 */
    public static final String LOAD_TOOLS = "load_tools";

    private static final Map<String, ModuleMeta> PLATFORM_MODULES = platformModules();

    private AiToolModules() {
    }

    /**
     * 模块元数据：编码、名称、描述、图标、是否常驻。
     */
    public record ModuleMeta(String code, String name, String description, String icon, boolean alwaysOn) {
        public ModuleMeta {
            code = code == null ? MODULE_GENERAL : code.trim();
            name = name == null || name.isBlank() ? code : name;
            description = description == null ? "" : description;
            icon = icon == null ? "" : icon;
        }
    }

    private static Map<String, ModuleMeta> platformModules() {
        Map<String, ModuleMeta> modules = new LinkedHashMap<>();
        put(modules, new ModuleMeta(MODULE_META, "工具目录", "工具模块目录与按需加载入口（load_tools）。", "morph:list", true));
        put(modules, new ModuleMeta(MODULE_WEB, "网页访问", "抓取公开网页作为分析参考。", "morph:globe", true));
        put(modules, new ModuleMeta(MODULE_WIKI, "知识库", "检索知识库与公开 Wiki 内容。", "morph:book", true));
        put(modules, new ModuleMeta(MODULE_UI, "界面助手", "页面跳转、元素高亮与引导播放。", "morph:cursor", true));
        put(modules, new ModuleMeta(MODULE_CMS, "站点画布", "CMS 画布的选取、读写、样式与区块工具。", "morph:layout", false));
        put(modules, new ModuleMeta(MODULE_GENERAL, "其他工具", "未归入其他模块的宿主工具。", "morph:puzzle", false));
        return Map.copyOf(modules);
    }

    private static void put(Map<String, ModuleMeta> modules, ModuleMeta meta) {
        modules.put(meta.code(), meta);
    }

    public static boolean knownModule(String code) {
        return code != null && PLATFORM_MODULES.containsKey(code.trim());
    }

    public static ModuleMeta platformModule(String code) {
        if (code == null || code.isBlank()) {
            return PLATFORM_MODULES.get(MODULE_GENERAL);
        }
        return PLATFORM_MODULES.getOrDefault(code.trim(), new ModuleMeta(code.trim(), code.trim(), "", "", false));
    }

    public static ModuleMeta pluginModule(String pluginCode, String name, String description, String icon) {
        String code = pluginCode == null || pluginCode.isBlank() ? "plugin:unknown" : PLUGIN_PREFIX + pluginCode.trim();
        return new ModuleMeta(code,
                name == null || name.isBlank() ? code : name,
                description == null ? "" : description,
                icon == null ? "" : icon,
                true);
    }

    public static ModuleMeta mcpModule(String serverCode, String name, String description, boolean alwaysOn) {
        String code = mcpModuleName(serverCode);
        return new ModuleMeta(code,
                name == null || name.isBlank() ? code : name,
                description == null ? "" : description,
                "morph:plug",
                alwaysOn);
    }

    /** MCP 服务模块编码：mcp:&lt;serverCode&gt;。 */
    public static String mcpModuleName(String serverCode) {
        return MCP_PREFIX + (serverCode == null || serverCode.isBlank() ? "unknown" : serverCode.trim());
    }

    public static ModuleMeta customModule() {
        return new ModuleMeta(MODULE_CUSTOM, "Python 自定义", "工作流自定义 Python 工具，由应用显式授权调用。", "morph:code", false);
    }

    /**
     * 宿主系统工具的模块归属：load_tools 归工具目录，其余取工具名首段（web/cms/wiki/ui/...）。
     */
    public static String platformToolModule(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return MODULE_GENERAL;
        }
        String name = toolName.trim();
        if (LOAD_TOOLS.equals(name)) {
            return MODULE_META;
        }
        int index = name.indexOf('.');
        if (index <= 0) {
            return MODULE_GENERAL;
        }
        return name.substring(0, index);
    }

    public static boolean mcpModuleCode(String code) {
        return code != null && code.startsWith(MCP_PREFIX);
    }

    /** MCP 工具的平台工具名：mcp.&lt;serverCode&gt;.&lt;toolName&gt;（模块编码用冒号，工具名用点）。 */
    public static String mcpToolName(String serverCode, String toolName) {
        return MCP_PREFIX.replace(':', '.') + serverCode + "." + toolName;
    }

    public static boolean pluginModuleCode(String code) {
        return code != null && code.startsWith(PLUGIN_PREFIX);
    }

    public static String mcpServerCode(String moduleCode) {
        return moduleCode != null && mcpModuleCode(moduleCode)
                ? moduleCode.substring(MCP_PREFIX.length())
                : "";
    }

    public static List<ModuleMeta> platformModuleList() {
        return List.copyOf(PLATFORM_MODULES.values());
    }
}
