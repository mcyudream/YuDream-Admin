package online.yudream.spring.entity.system;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CpuInfo {
    private int coreCount;
    private double userUsageRate;
    private double systemUsageRate;
    private double free;
}
