package online.yudream.base.domain.platform.integration.service;

import online.yudream.base.domain.platform.integration.aggregate.RuntimeScript;
import online.yudream.base.domain.platform.integration.valobj.RuntimeExecutionResult;

public interface RuntimeExecutor {

    RuntimeExecutionResult execute(RuntimeScript script, String stdin);

    /**
     * 运行环境不可用的原因（如部署镜像没有 Python 解释器）；可用时返回 null。
     * 调用方据此不向模型暴露依赖该运行时的工具，并给出可操作的提示，而不是在执行时才报底层进程错误。
     */
    default String unavailableReason() {
        return null;
    }
}
