package online.yudream.base.interfaces.system.monitor.res;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class HostThreadHotspotRes {
    private Long threadId;
    private String name;
    private String state;
    private Long cpuTimeMs;
    private Long userTimeMs;
    private boolean daemon;
    private List<String> stackTop;
}
