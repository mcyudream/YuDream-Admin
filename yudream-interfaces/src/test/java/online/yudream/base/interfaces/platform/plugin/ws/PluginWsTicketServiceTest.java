package online.yudream.base.interfaces.platform.plugin.ws;

import online.yudream.base.domain.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 插件 WS 票据：强绑定 code+path、单次原子消费、TTL、限速与日志脱敏。
 */
class PluginWsTicketServiceTest {

    private PluginWsTicketService service;

    @BeforeEach
    void setUp() {
        service = new PluginWsTicketService();
    }

    private PluginWsTicketService.IssuedTicket issue() {
        return service.issue(7L, List.of("plugin:demo:use"), "demo", "/chat");
    }

    @Test
    @DisplayName("正常发放并在精确 code+path 匹配下单次消费成功")
    void issueAndConsumeOnce() {
        PluginWsTicketService.IssuedTicket issued = issue();
        assertTrue(issued.token().length() >= 32);
        assertEquals(60, issued.expiresIn());

        var consumed = service.consume(issued.token(), "demo", "/chat");
        assertTrue(consumed.isPresent());
        assertEquals(7L, consumed.get().userId());
        assertEquals(List.of("plugin:demo:use"), consumed.get().permissions());

        // 单次有效：第二次消费必须失败
        assertTrue(service.consume(issued.token(), "demo", "/chat").isEmpty());
    }

    @Test
    @DisplayName("跨插件或跨路径消费一律拒绝；token 任何消费尝试即按单次语义销毁（防试探）")
    void rejectCrossPluginAndCrossPath() {
        PluginWsTicketService.IssuedTicket issued = issue();
        assertTrue(service.consume(issued.token(), "other", "/chat").isEmpty());
        assertTrue(service.consume(issued.token(), "demo", "/other").isEmpty());
        // 原子 remove 先于绑定校验：错误目标的消费尝试同样销毁票据（防探测重放）
        assertTrue(service.consume(issued.token(), "demo", "/chat").isEmpty());
    }

    @Test
    @DisplayName("路径按宿主注册规范归一后匹配")
    void normalizePathOnBinding() {
        var issued = service.issue(7L, List.of(), "demo", "chat");
        assertTrue(service.consume(issued.token(), "demo", "/chat").isPresent());
    }

    @Test
    @DisplayName("票据对象 toString 不泄露 token")
    void redactTokenInToString() {
        PluginWsTicketService.IssuedTicket issued = issue();
        assertFalse(issued.toString().contains(issued.token()));
    }

    @Test
    @DisplayName("容量上限与按用户限速")
    void capacityAndThrottle() {
        assertThrows(BizException.class, () -> service.issue(null, List.of(), "demo", "/chat"));
        assertThrows(BizException.class, () -> service.issue(7L, List.of(), " ", "/chat"));
        assertThrows(BizException.class, () -> service.issue(7L, List.of(), "demo", " "));
        // 同一用户连发超过窗口限额后拒绝
        BizException throttled = assertThrows(BizException.class, () -> {
            for (int i = 0; i <= PluginWsTicketService.MAX_ISSUE_PER_USER_PER_WINDOW; i++) {
                service.issue(42L, List.of(), "demo", "/chat");
            }
        });
        assertTrue(throttled.getMessage().contains("频繁"));
    }

    @Test
    @DisplayName("绑定校验：code 规范、路径长度与非法字符（编码/查询/空白）")
    void bindingValidation() {
        // code 含斜杠/查询/空格拒绝
        assertThrows(BizException.class, () -> service.issue(7L, List.of(), "a/b", "/chat"));
        assertThrows(BizException.class, () -> service.issue(7L, List.of(), "a?b=1", "/chat"));
        assertThrows(BizException.class, () -> service.issue(7L, List.of(), "a b", "/chat"));
        // 路径含 %、?、#、空白、控制字符拒绝（不做 URL 解码，明确拒绝编码形态）
        assertThrows(BizException.class, () -> service.issue(7L, List.of(), "demo", "/cha%20t"));
        assertThrows(BizException.class, () -> service.issue(7L, List.of(), "demo", "/chat?x=1"));
        assertThrows(BizException.class, () -> service.issue(7L, List.of(), "demo", "/chat#f"));
        assertThrows(BizException.class, () -> service.issue(7L, List.of(), "demo", "/chat x"));
        // 超长路径拒绝
        assertThrows(BizException.class, () -> service.issue(7L, List.of(), "demo", "/" + "a".repeat(2048)));
        // 合法 code 与路径通过
        assertTrue(service.issue(7L, List.of(), "my-plugin_1.0", "/chat/room-1").token().length() >= 32);
    }
}
