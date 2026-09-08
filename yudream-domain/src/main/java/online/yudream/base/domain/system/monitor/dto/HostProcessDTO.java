package online.yudream.base.domain.system.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HostProcessDTO {

    private Integer pid;

    private String name;

    private String user;

    private Long rssBytes;

    private Long virtualBytes;

    private Double cpuPercent;

    private boolean currentJvm;

    private String commandLine;
}
