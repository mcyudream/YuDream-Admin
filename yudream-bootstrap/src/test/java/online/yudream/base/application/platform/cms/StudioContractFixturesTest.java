package online.yudream.base.application.platform.cms;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.cms.service.StudioDocumentValidator;
import online.yudream.base.domain.common.exception.BizException;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

/** 与TypeScript读取同一fixture，不复制跨端安全预期。 */
class StudioContractFixturesTest {
    @Test void sharedDocumentsHaveIdenticalAcceptanceBoundary() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("AGENTS.md"))) root = root.getParent();
        assertThat(root).as("workspace root").isNotNull();
        Path fixture = root.resolve("tests/fixtures/site-studio-contract.json");
        var cases = new ObjectMapper().readTree(Files.readString(fixture));
        var validator = new StudioDocumentValidator();
        assertThat(cases.path("valid").isArray()).isTrue();
        assertThat(cases.path("invalid").isArray()).isTrue();
        for (var item : cases.get("valid")) {
            assertThatCode(() -> validator.validate(item.get("document").toString())).as(item.path("name").asText()).doesNotThrowAnyException();
        }
        for (var item : cases.get("invalid")) {
            assertThatThrownBy(() -> validator.validate(item.get("document").toString())).as(item.path("name").asText()).isInstanceOf(BizException.class);
        }
    }
}
