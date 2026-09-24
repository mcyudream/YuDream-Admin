package online.yudream.base.domain.system.about.valobj;

/**
 * 最新版本探测目标：MAVEN 坐标为 groupId:artifactId，NPM 坐标为包名（支持 @scope/name）。
 */
public record LatestVersionTarget(String key, String name, Kind kind, String coordinate) {

    public enum Kind {
        MAVEN,
        NPM
    }
}
