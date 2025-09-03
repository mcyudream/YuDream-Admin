package online.yudream.spring.entity.system;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryInfo {
    private double totalMemory;
    private double usedMemory;
    private double freeMemory;
    private double usageRate;

}
