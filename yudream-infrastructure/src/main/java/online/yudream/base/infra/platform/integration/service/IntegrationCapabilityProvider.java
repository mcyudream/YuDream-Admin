package online.yudream.base.infra.platform.integration.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.capability.enumerate.CapabilityType;
import online.yudream.base.domain.platform.capability.service.CapabilityProvider;
import online.yudream.base.domain.platform.capability.valobj.CapabilityDescriptor;
import online.yudream.base.domain.platform.capability.valobj.CapabilityHealth;
import online.yudream.base.domain.platform.capability.valobj.CapabilityTestResult;
import online.yudream.base.domain.platform.integration.enumerate.ExecutionStatus;
import online.yudream.base.domain.platform.integration.valobj.RuntimeExecutionResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
public class IntegrationCapabilityProvider implements CapabilityProvider {

    public static final String CODE = "integration";
    private static final String DEFAULT_PYTHON_COMMAND = "python";

    private final LocalPythonRuntimeExecutor localPythonRuntimeExecutor;
    private final AtomicBoolean enabled = new AtomicBoolean(false);

    @Override
    public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor(
                CODE,
                "集成调用",
                CapabilityType.INTEGRATION,
                "提供 HTTP 调用和 Python 运行脚本能力",
                "i-ri:terminal-box-line",
                70,
                Map.of("pythonCommand", DEFAULT_PYTHON_COMMAND)
        );
    }

    @Override
    public CapabilityHealth health() {
        return enabled.get()
                ? CapabilityHealth.enabled("集成调用已启用", Map.of(
                "runtime", "python",
                "pythonCommand", localPythonRuntimeExecutor.pythonCommand()
        ))
                : CapabilityHealth.disabled("集成调用未启用");
    }

    @Override
    public void enable(Map<String, String> config) {
        localPythonRuntimeExecutor.configurePythonCommand(config == null
                ? DEFAULT_PYTHON_COMMAND
                : config.getOrDefault("pythonCommand", DEFAULT_PYTHON_COMMAND));
        enabled.set(true);
    }

    @Override
    public void disable() {
        enabled.set(false);
        localPythonRuntimeExecutor.resetPythonCommand();
    }

    @Override
    public CapabilityTestResult test(String message) {
        if (!enabled.get()) {
            return CapabilityTestResult.failure("集成调用未启用");
        }
        RuntimeExecutionResult result = localPythonRuntimeExecutor.checkPythonCommand();
        if (result.status() == ExecutionStatus.SUCCESS) {
            return CapabilityTestResult.success("集成调用能力可用，" + result.stdout());
        }
        String reason = result.errorMessage() == null || result.errorMessage().isBlank()
                ? "Python 命令不可用"
                : result.errorMessage();
        return CapabilityTestResult.failure("集成调用能力检测失败：" + reason);
    }
}
