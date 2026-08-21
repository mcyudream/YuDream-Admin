package online.yudream.base.application.platform.milky.sandbox.dto;

import online.yudream.base.domain.platform.milky.sandbox.QqSandboxCaseSetup;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxCaseStep;

import java.time.Instant;
import java.util.List;

public record QqSandboxCaseDTO(
        String id,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt,
        QqSandboxCaseSetup setup,
        List<QqSandboxCaseStep> steps
) { }
