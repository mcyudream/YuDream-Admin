package online.yudream.base.domain.installer.valobj;

/**
 * MongoDB 探测规格：安装向导对给定连接串做连通性与认证探测。
 */
public record MongoProbeSpec(String uri) {
}
