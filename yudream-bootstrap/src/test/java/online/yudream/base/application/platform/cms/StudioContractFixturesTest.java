package online.yudream.base.application.platform.cms;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.cms.service.StudioDocumentValidator;
import online.yudream.base.domain.common.exception.BizException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** 与TypeScript读取同一fixture，不复制跨端安全预期。 */
class StudioContractFixturesTest {
    @Test void sharedDocumentsHaveIdenticalAcceptanceBoundary() throws Exception {
        var cases = new ObjectMapper().readTree(getClass().getClassLoader()
                .getResourceAsStream("cms/site-studio-contract.json"));
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
