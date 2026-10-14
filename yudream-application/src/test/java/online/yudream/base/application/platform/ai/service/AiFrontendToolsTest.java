package online.yudream.base.application.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.ai.dto.AiFrontendContextDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.service.AiExternalAgentTool;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AiFrontendToolsTest {
    private static final List<AiFrontendContextDTO.Route> ROUTES = List.of(new AiFrontendContextDTO.Route("users", "用户管理", "/system/user"));
    private static final List<AiFrontendContextDTO.Action> ACTIONS = List.of(new AiFrontendContextDTO.Action("system:user:create", "新增用户", "users"));

    @Test void awarenessAndNavigationDoNotImplicitlyGrantHighlight() {
        assertTrue(AiFrontendTools.create(new AiFrontendContextDTO(null, ROUTES, ACTIONS, false, false), null).isEmpty());
        var tools = AiFrontendTools.create(new AiFrontendContextDTO(null, ROUTES, ACTIONS, true, false), null);
        assertEquals(List.of("ui.navigate"), tools.stream().map(tool -> tool.descriptor().name()).toList());
    }

    @Test void explicitHighlightRemainsAvailableAfterNavigationToAnotherPage() {
        var tools = AiFrontendTools.create(new AiFrontendContextDTO(null, List.of(), List.of(), false, true), null);
        assertEquals(List.of("ui.highlight"), tools.stream().map(tool -> tool.descriptor().name()).toList());
        @SuppressWarnings("unchecked") var schema = (Map<String, Object>) tools.getFirst().descriptor().inputSchema().get("actionCode");
        assertFalse(schema.containsKey("enum"));
        assertEquals(180, schema.get("maxLength"));
        var highlight = (AiExternalAgentTool) tools.getFirst();
        assertThrows(BizException.class, () -> highlight.prepare("invalid", new AiAgentToolCall("ui.highlight", Map.of("actionCode", "button:nth-child(2)"))));
    }

    @Test void legacyContextRetainsExplicitlyDeclaredTools() {
        var tools = AiFrontendTools.create(new AiFrontendContextDTO(null, ROUTES, ACTIONS), null);
        assertEquals(List.of("ui.navigate", "ui.highlight"), tools.stream().map(tool -> tool.descriptor().name()).toList());
    }

    @Test void durableContextRoundTripPreservesDisabledToolControls() {
        ObjectMapper json = new ObjectMapper();
        var original = new AiFrontendContextDTO(null, ROUTES, ACTIONS, true, false);
        var saved = json.convertValue(original, Map.class);
        var restored = json.convertValue(saved, AiFrontendContextDTO.class);
        assertEquals(List.of("ui.navigate"), AiFrontendTools.create(restored, null).stream().map(tool -> tool.descriptor().name()).toList());
    }
}
