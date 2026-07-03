package online.yudream.base.domain.platform.integration.service;

import online.yudream.base.domain.platform.integration.aggregate.RuntimeScript;
import online.yudream.base.domain.platform.integration.valobj.RuntimeExecutionResult;

public interface RuntimeExecutor {

    RuntimeExecutionResult execute(RuntimeScript script, String stdin);
}
