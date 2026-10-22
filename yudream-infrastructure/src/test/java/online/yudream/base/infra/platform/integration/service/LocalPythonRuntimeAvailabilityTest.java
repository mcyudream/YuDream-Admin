package online.yudream.base.infra.platform.integration.service;

import online.yudream.base.domain.platform.integration.aggregate.RuntimeScript;
import online.yudream.base.domain.platform.integration.enumerate.ExecutionStatus;
import online.yudream.base.domain.platform.integration.enumerate.RuntimeLanguage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalPythonRuntimeAvailabilityTest {

    @Test
    void missingInterpreterIsReportedClearlyAndExecutionFailsWithoutStartingAProcess() {
        LocalPythonRuntimeExecutor executor = new LocalPythonRuntimeExecutor();
        executor.configurePythonCommand("yudream-no-such-python-xyz");

        String reason = executor.unavailableReason();
        assertThat(reason).contains("yudream-no-such-python-xyz").contains("集成能力");

        var result = executor.execute(RuntimeScript.create("t", "t", RuntimeLanguage.PYTHON, "print(1)"), "");
        assertThat(result.status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(result.errorMessage()).isEqualTo(reason);
        assertThat(result.durationMillis()).isZero();
    }

    @Test
    void reconfiguringClearsTheCachedProbe() {
        LocalPythonRuntimeExecutor executor = new LocalPythonRuntimeExecutor();
        executor.configurePythonCommand("yudream-no-such-python-a");
        assertThat(executor.unavailableReason()).contains("yudream-no-such-python-a");
        executor.configurePythonCommand("yudream-no-such-python-b");
        assertThat(executor.unavailableReason()).contains("yudream-no-such-python-b");
    }
}
