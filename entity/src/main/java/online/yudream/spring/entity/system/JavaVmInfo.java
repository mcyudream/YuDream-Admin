package online.yudream.spring.entity.system;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JavaVmInfo {
    private String javaName;
    private String javaVersion;
    private LocalDateTime startTime;
    private String installPath;
    private List<String> runParameters;
}
