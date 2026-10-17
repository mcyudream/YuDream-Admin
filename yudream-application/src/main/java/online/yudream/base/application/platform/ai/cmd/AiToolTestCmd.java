package online.yudream.base.application.platform.ai.cmd;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class AiToolTestCmd {
    /** 平台工具编码（系统工具为 descriptor.name()，Python 工具为工具编码）。 */
    private String code;
    /** 调用参数，按工具输入 JSON Schema。 */
    private Map<String, Object> arguments;
    /** 调用方权限快照（由接口层按当前登录用户捕获，执行前复核工具权限码）。 */
    private java.util.List<String> permissionCodes;
}
