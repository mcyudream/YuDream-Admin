package online.yudream.base.interfaces.platform.devtools.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 插件指令模拟请求。
 */
@Data
public class PluginCommandTestRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 指令名（不含斜杠前缀） */
    private String command;
    /** 指令参数 */
    private List<String> arguments = new ArrayList<>();
    /** 模拟事件原文，为空时按指令与参数拼接 */
    private String content;
}
