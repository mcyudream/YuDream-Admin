package online.yudream.base.domain.platform.milky.sandbox;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QqSandboxCaseTest {

    private static QqSandboxCaseSetup setup() {
        return new QqSandboxCaseSetup("demo", "1", "10000", "10001", "用户", "20001", "group", null, false, null);
    }

    private static QqSandboxCaseStep step(String content) {
        return new QqSandboxCaseStep(null, null, content, true, null, null, null, null);
    }

    @Test
    void stepDefaultsTypeToMessage() {
        QqSandboxCaseStep step = step("你好");
        // 旧版用例 JSON 没有 type 字段，反序列化后必须归一化为 message
        assertEquals("message", step.type());
        assertNull(step.buttonId());
    }

    @Test
    void setupDefaultsRandomModeAndKeepsNullRoles() {
        QqSandboxCaseSetup setup = setup();
        assertEquals(QqSandboxRandomMode.REAL, setup.randomMode());
        // null simulateRoles 表示走真实角色，不能被归一化为空列表
        assertNull(setup.simulateRoles());
    }

    @Test
    void caseNormalizesNameAndSteps() {
        List<QqSandboxCaseStep> steps = new ArrayList<>();
        steps.add(step("你好"));
        QqSandboxCase sandboxCase = new QqSandboxCase("c1", "  冒烟用例  ", " ", Instant.now(), Instant.now(),
                setup(), steps);
        assertEquals("冒烟用例", sandboxCase.name());
        assertNull(sandboxCase.description());
        steps.add(step("第二条"));
        assertEquals(1, sandboxCase.steps().size());
        assertEquals(List.of(), sandboxCase.steps().get(0).mentions());
    }

    @Test
    void caseRejectsBlankNameAndMissingSetup() {
        Instant now = Instant.now();
        assertThrows(IllegalArgumentException.class,
                () -> new QqSandboxCase("c1", " ", null, now, now, setup(), List.of(step("x"))));
        assertThrows(IllegalArgumentException.class,
                () -> new QqSandboxCase("c1", "名称", null, now, now, null, List.of(step("x"))));
    }
}
