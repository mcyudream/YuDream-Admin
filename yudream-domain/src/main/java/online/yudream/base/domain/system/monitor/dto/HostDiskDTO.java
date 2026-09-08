package online.yudream.base.domain.system.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HostDiskDTO {

    private String name;

    private String mount;

    private String type;

    private Long totalBytes;

    private Long usedBytes;

    private Long usableBytes;

    private Double usagePercent;
}
