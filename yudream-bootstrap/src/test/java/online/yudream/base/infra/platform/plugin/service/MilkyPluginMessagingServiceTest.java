package online.yudream.base.infra.platform.plugin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.domain.platform.milky.aggregate.MilkyConnection;
import online.yudream.base.domain.platform.milky.repo.MilkyConnectionRepo;
import online.yudream.base.domain.platform.milky.service.MilkyApiGateway;
import online.yudream.base.plugin.spi.system.messaging.PluginMessageContent;
import online.yudream.base.plugin.spi.system.messaging.PluginMessageRequest;
import online.yudream.base.plugin.spi.system.user.PluginUserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MilkyPluginMessagingServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void textAttachmentsCanBeEmbeddedAtMarkedPositions() {
        MilkyConnectionRepo connections = mock(MilkyConnectionRepo.class);
        MilkyApiGateway gateway = mock(MilkyApiGateway.class);
        MilkyConnection connection = MilkyConnection.create("QQ", "http://milky.local", "token", "base64", null);
        connection.setId(100L);
        when(connections.findById(100L)).thenReturn(Optional.of(connection));
        when(gateway.invoke(any(), eq("send_group_message"), any())).thenReturn(Map.of("message_seq", 42));
        MilkyPluginMessagingService service = new MilkyPluginMessagingService(
                connections, gateway, mock(PluginUserService.class), new ObjectMapper());

        service.send(new PluginMessageRequest("100", "qq", "bot", "200", new PluginMessageContent(
                PluginMessageContent.Type.TEXT,
                "第一步。\n[[wiki-image:1]]\n第二步。",
                List.of(new PluginMessageContent.Attachment("base64://aW1hZ2U=", "wiki-image:1", "image/png")),
                Map.of()))).toCompletableFuture().join();

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(gateway).invoke(any(), eq("send_group_message"), payload.capture());
        List<Map<String, Object>> segments = (List<Map<String, Object>>) payload.getValue().get("message");
        assertThat(segments).hasSize(3);
        assertThat(segments.get(0)).containsEntry("type", "text");
        assertThat(segments.get(1)).containsEntry("type", "image");
        assertThat(segments.get(2)).containsEntry("type", "text");
    }
}
