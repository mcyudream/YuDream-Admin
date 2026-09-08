package online.yudream.base.interfaces.system.monitor.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HostProcessRes {
    private Integer pid;
    private String name;
    private String user;
    private Long rssBytes;
    private Long virtualBytes;
    private Double cpuPercent;
    private boolean currentJvm;
    private String commandLine;
}
