package online.yudream.base.interfaces.platform.cms;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.cms.dto.*;
import online.yudream.base.application.platform.cms.service.StudioAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.platform.cms.controller.*;
import online.yudream.base.interfaces.platform.cms.request.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudioControllerContractTest {
    @Test void stateAndReleaseListAreContractShapedAndLongRevisionRemainsString() throws Exception {
        var app = mock(StudioAppService.class);
        when(app.state("alpha")).thenReturn(new StudioStateDTO("alpha", "9007199254740993", null, null, null));
        when(app.releases("alpha", 1, 10)).thenReturn(new PageResult<>(List.of(new StudioReleaseDTO("9007199254740993", "9007199254740994", Instant.parse("2026-10-19T00:00:00Z"))), 1, 1, 10));
        var controller = new StudioController(app);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var state = mapper.valueToTree(controller.state("alpha")).get("data");
        assertThat(state.get("revision").isTextual()).isTrue();
        assertThat(state.get("revision").asText()).isEqualTo("9007199254740993");
        assertThat(state.get("documentJson").isNull()).isTrue();
        var releases = mapper.valueToTree(controller.releases("alpha", 1, 10)).get("data");
        assertThat(releases.has("list")).isTrue();
        assertThat(releases.has("records")).isFalse();
        assertThat(releases.get("list").get(0).get("id").isTextual()).isTrue();
    }
    @Test void publicUnpublishedIsNullAndRestoreUsesEditNotPublish() throws Exception {
        var app = mock(StudioAppService.class);
        assertThat(new PublicStudioController(app).document().getData()).isNull();
        var restore = StudioController.class.getMethod("restore", String.class, String.class, StudioRevisionRequest.class);
        assertThat(restore.getAnnotation(PermissionRegister.class).code()).isEqualTo("platform:cms:edit");
        var publish = StudioController.class.getMethod("publish", String.class, StudioRevisionRequest.class);
        assertThat(publish.getAnnotation(PermissionRegister.class).code()).isEqualTo("platform:cms:publish");
        var unpublish = StudioController.class.getMethod("unpublish", String.class, StudioRevisionRequest.class);
        assertThat(unpublish.getAnnotation(PermissionRegister.class).code()).isEqualTo("platform:cms:publish");
    }
    @Test void rejectsNumericExpectedRevisionInsteadOfJacksonStringCoercion() throws Exception {
        var mapper = new ObjectMapper();
        assertThat(mapper.readValue("{\"expectedRevision\":\"9007199254740993\"}", StudioRevisionRequest.class).expectedRevision()).isEqualTo("9007199254740993");
        assertThatThrownBy(() -> mapper.readValue("{\"expectedRevision\":9007199254740993}", StudioRevisionRequest.class)).isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
        assertThatThrownBy(() -> mapper.readValue("{\"expectedRevision\":1,\"documentJson\":\"{}\"}", StudioDraftSaveRequest.class)).isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
    }
    @Test void endpointsAndApplicationAreAbsentWhenProjectGateIsOff() {
        for (Class<?> type : List.of(StudioController.class, PublicStudioController.class, StudioAppService.class)) {
            var gate = type.getAnnotation(ConditionalOnProperty.class);
            assertThat(gate).isNotNull();
            assertThat(gate.prefix()).isEqualTo("yudream.platform.capabilities.cms");
            assertThat(gate.name()).containsExactly("enabled");
            assertThat(gate.havingValue()).isEqualTo("true");
            assertThat(gate.matchIfMissing()).isFalse();
        }
    }
}
