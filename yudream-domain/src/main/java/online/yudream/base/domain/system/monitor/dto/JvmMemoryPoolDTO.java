package online.yudream.base.domain.system.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JvmMemoryPoolDTO {

    private String name;

    private String type;

    private Long usedBytes;

    private Long committedBytes;

    private Long maxBytes;

    private Double usagePercent;
}
