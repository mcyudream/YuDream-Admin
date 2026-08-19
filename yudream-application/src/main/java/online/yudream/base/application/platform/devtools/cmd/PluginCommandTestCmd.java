package online.yudream.base.application.platform.devtools.cmd;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 插件指令模拟触发命令。
 */
@Data
public class PluginCommandTestCmd {

    /** 指令名（不含斜杠前缀） */
    private String command;
    /** 指令参数 */
    private List<String> arguments = new ArrayList<>();
    /** 模拟事件原文，为空时按指令与参数拼接 */
    private String content;
}
