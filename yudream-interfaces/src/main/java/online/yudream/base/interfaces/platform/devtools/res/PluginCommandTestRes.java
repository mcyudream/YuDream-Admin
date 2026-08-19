package online.yudream.base.interfaces.platform.devtools.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 插件指令模拟执行结果响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginCommandTestRes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String pluginCode;
    private String command;
    private boolean matched;
    private boolean success;
    private String errorMessage;
    private Long durationMs;
}
