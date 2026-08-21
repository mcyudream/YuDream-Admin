package online.yudream.base.application.platform.milky.sandbox.cmd;

import online.yudream.base.domain.platform.milky.sandbox.QqSandboxCaseSetup;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxCaseStep;

import java.util.List;

public record QqSandboxCaseSaveCmd(
        String id,
        String name,
        String description,
        QqSandboxCaseSetup setup,
        List<QqSandboxCaseStep> steps
) { }
