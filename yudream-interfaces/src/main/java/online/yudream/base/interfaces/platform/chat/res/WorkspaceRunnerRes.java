package online.yudream.base.interfaces.platform.chat.res;

import java.util.Map;

/** 代码运行器状态：available 为 false 时 reason 说明原因；runtimes 为 python / node 版本 */
public record WorkspaceRunnerRes(boolean available, String reason, Map<String, String> runtimes) {
}
