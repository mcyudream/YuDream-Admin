package online.yudream.base.interfaces.system.about.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 框架信息视图：版本来自构建期注入，running 信息来自 JVM。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AboutFrameworkRes {

    private String name;
    private String version;
    private String buildTime;
    private String springBootVersion;
    private String javaVersion;
    private String osName;
    private String osArch;
}
