package online.yudream.base.domain.system.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HostThreadHotspotDTO {

    private Long threadId;

    private String name;

    private String state;

    private Long cpuTimeMs;

    private Long userTimeMs;

    private boolean daemon;

    private List<String> stackTop;
}
