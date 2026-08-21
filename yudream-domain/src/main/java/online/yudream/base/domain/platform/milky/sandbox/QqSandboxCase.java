package online.yudream.base.domain.platform.milky.sandbox;

import java.time.Instant;
import java.util.List;

// 一条可复用的沙盒测试用例：会话初始参数 + 有序消息步骤
public record QqSandboxCase(
        String id,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt,
        QqSandboxCaseSetup setup,
        List<QqSandboxCaseStep> steps
) {
    public QqSandboxCase {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("沙盒用例 ID 不能为空");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("沙盒用例名称不能为空");
        name = name.trim();
        description = description == null || description.isBlank() ? null : description.trim();
        if (setup == null) throw new IllegalArgumentException("沙盒用例缺少会话初始参数");
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public QqSandboxCase rename(String newName, String newDescription) {
        return new QqSandboxCase(id, newName, newDescription, createdAt, Instant.now(), setup, steps);
    }
}
