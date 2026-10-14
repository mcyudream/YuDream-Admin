package online.yudream.base.interfaces.platform.chat.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import online.yudream.base.interfaces.platform.chat.assembler.ChatWebAssembler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatFrontendContextRequestTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll static void closeValidator() { FACTORY.close(); }

    @Test void acceptsFullPluginNamespaceAndRouteWithoutTruncation() {
        String action = "plugin:" + "p".repeat(48) + ":" + "a".repeat(119);
        String route = "plugin:" + "r".repeat(160);
        var request = new ChatFrontendContextRequest(
                new ChatFrontendContextRequest.Route(route, "插件功能", "/plugin-page"),
                List.of(), List.of(new ChatFrontendContextRequest.Action(action, "新建内容", route)));
        assertTrue(VALIDATOR.validate(request).isEmpty());
    }

    @Test void rejectsOversizedOrSelectorLikeActionIdentities() {
        for (String code : List.of("p" + "a".repeat(180), "button:nth-child(2)", "#submit", "create item")) {
            var request = new ChatFrontendContextRequest(null, List.of(),
                    List.of(new ChatFrontendContextRequest.Action(code, "创建", "plugin-page")));
            assertFalse(VALIDATOR.validate(request).isEmpty(), code);
        }
    }

    @Test void preservesPageOnlyControlsAtTheHttpBoundary() {
        var request = new ChatSendRequest();
        request.setContextEnabled(true);
        request.setFrontendContext(new ChatFrontendContextRequest(null, List.of(),
                List.of(new ChatFrontendContextRequest.Action("system:user:create", "新增用户", "users")), false, false));
        var command = ChatWebAssembler.send(request);
        assertTrue(command.isContextEnabled());
        assertFalse(command.isFrontendToolsEnabled());
        assertFalse(command.getFrontendContext().navigationToolsEnabled());
        assertFalse(command.getFrontendContext().highlightToolsEnabled());
        assertTrue(command.getFrontendContext().actions().stream().anyMatch(action -> action.code().equals("system:user:create")));
    }
}
