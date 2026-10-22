package online.yudream.base.domain.platform.workspace.service;

import online.yudream.base.domain.platform.workspace.valobj.CodeRunRequest;
import online.yudream.base.domain.platform.workspace.valobj.CodeRunResult;

/**
 * 代码运行器端口：在独立的运行器容器中执行 Python / Node 代码（临时目录、资源限制、无外网），
 * 只在部署配置启用时存在（项目配置闸门）；{@link #unavailableReason()} 是每次使用前的可用性闸门。
 */
public interface CodeRunnerGateway {

    /** 运行器不可用的原因（未连通、令牌错误…）；可用时返回 null。结果短时缓存。 */
    String unavailableReason();

    /** 运行器版本信息（python / node），不可用时为空 */
    java.util.Map<String, String> runtimes();

    CodeRunResult run(CodeRunRequest request);
}
