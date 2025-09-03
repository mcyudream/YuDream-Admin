package online.yudream.spring.entity.system;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DiskInfo {
    private String diskPath;
    private String fileSystem;
    private String diskType;
    private double totalSize;
    private double availableSize;
    private double usedSize;
    private double usedPercentage;
}
