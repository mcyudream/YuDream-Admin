package online.yudream.spring.entity.system;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemData {
    private CpuInfo cpuInfo;
    private MemoryInfo memoryInfo;
    private List<DiskInfo> diskInfo;
    private JavaVmInfo javaVmInfo;
    private ServerInfo serverInfo;
}
