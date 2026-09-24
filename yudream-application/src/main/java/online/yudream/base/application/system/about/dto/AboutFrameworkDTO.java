package online.yudream.base.application.system.about.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 框架运行时信息：版本来自构建期注入的 about.properties，其余取自 JVM/系统属性。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AboutFrameworkDTO {

    private String name;
    private String version;
    private String buildTime;
    private String springBootVersion;
    private String javaVersion;
    private String osName;
    private String osArch;
}
