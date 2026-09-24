package online.yudream.base.domain.system.about.valobj;

/**
 * 框架构建信息：来自构建期过滤的 about.properties；读取不到（如 IDE 未走 Maven 资源过滤）返回空。
 */
public record AboutBuildInfo(String version, String buildTime) {
}
