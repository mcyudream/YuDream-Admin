package online.yudream.base.application.platform.wiki.service;

import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.domain.platform.wiki.service.WikiIngestProgressGateway;
import online.yudream.base.domain.platform.wiki.valobj.WikiIngestProgress;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WikiIngestProgressAppServiceTest {

    @Test
    void checksCapabilityAndMapsLongIdsToStrings() {
        CapabilityAppService capabilities = mock(CapabilityAppService.class);
        WikiIngestProgressGateway gateway = mock(WikiIngestProgressGateway.class);
        AtomicReference<Consumer<WikiIngestProgress>> listener = new AtomicReference<>();
        AutoCloseable subscription = mock(AutoCloseable.class);
        when(gateway.subscribe(org.mockito.ArgumentMatchers.eq(9007199254740993L), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    listener.set(invocation.getArgument(1));
                    return subscription;
                });
        WikiIngestProgressAppService service = new WikiIngestProgressAppService(capabilities, gateway);
        AtomicReference<online.yudream.base.application.platform.wiki.dto.WikiIngestProgressDTO> result = new AtomicReference<>();

        assertThat(service.subscribe(9007199254740993L, result::set)).isSameAs(subscription);
        listener.get().accept(new WikiIngestProgress(
                9007199254740995L, 9007199254740993L, 9007199254740997L,
                "extract", "处理中", 40, false));

        verify(capabilities).ensureEnabled("wiki", "Wiki 知识库");
        assertThat(result.get().taskId()).isEqualTo("9007199254740995");
        assertThat(result.get().spaceId()).isEqualTo("9007199254740993");
        assertThat(result.get().sourceId()).isEqualTo("9007199254740997");
    }
}
