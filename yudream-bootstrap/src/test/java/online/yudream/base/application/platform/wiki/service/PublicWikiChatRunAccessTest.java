package online.yudream.base.application.platform.wiki.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.ai.dto.AiRunDTO;
import online.yudream.base.application.platform.ai.service.AiRunAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.repo.PublicAiRunGrantRepo;
import online.yudream.base.domain.platform.ai.valobj.PublicAiRunGrant;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PublicWikiChatRunAccessTest {
    @Test void failedObserverGrantCancelsTheOtherwiseUnobservableBackendRun() {
        var grants = mock(PublicAiRunGrantRepo.class);
        doThrow(new IllegalStateException("storage unavailable")).when(grants).save(any());
        var runs = mock(AiRunAppService.class);
        var run = mock(AiRunDTO.class); when(run.id()).thenReturn("run-orphan");
        when(runs.start(eq(0L), eq("wiki-public-chat"), anyString(), any(), any())).thenReturn(run);
        var released = new java.util.concurrent.atomic.AtomicBoolean();
        var service = new PublicWikiChatRunAppService(mock(WikiPublicAppService.class), mock(WikiChatAppService.class), runs, grants, new ObjectMapper());
        assertThrows(IllegalStateException.class, () -> service.start("docs", null, "question", List.of(), () -> released.set(true)));
        verify(runs).cancel(0L, "run-orphan");
        assertTrue(released.get());
    }
    @Test void randomTokenOnlyReadsItsOwnSpaceAndRunAndDatabaseKeepsOnlyItsHash() {
        var grants = new Grants();
        var runs = mock(AiRunAppService.class);
        var run = mock(AiRunDTO.class); when(run.id()).thenReturn("run-1"); when(run.module()).thenReturn("wiki-public-chat");
        when(runs.start(eq(0L), eq("wiki-public-chat"), anyString(), any(), any())).thenReturn(run);
        when(runs.get(0L, "run-1")).thenReturn(run);
        var service = new PublicWikiChatRunAppService(mock(WikiPublicAppService.class), mock(WikiChatAppService.class), runs, grants, new ObjectMapper());
        var result = service.start("docs", null, "question", List.of(), () -> {});
        assertEquals(64, result.observerToken().length());
        assertNotEquals(result.observerToken(), grants.items.getFirst().tokenHash());
        assertSame(run, service.get("docs", "run-1", result.observerToken()));
        assertThrows(BizException.class, () -> service.get("private", "run-1", result.observerToken()));
        assertThrows(BizException.class, () -> service.get("docs", "run-2", result.observerToken()));
        assertThrows(BizException.class, () -> service.get("docs", "run-1", "a".repeat(64)));
        assertThrows(BizException.class, () -> service.get("docs", "run-1", null));
        var prior = grants.items.removeFirst();
        grants.items.add(new PublicAiRunGrant(prior.runId(), prior.tokenHash(), prior.sessionId(), prior.spaceSlug(), Instant.now().minusSeconds(1)));
        assertThrows(BizException.class, () -> service.get("docs", "run-1", result.observerToken()));
    }

    private static final class Grants implements PublicAiRunGrantRepo {
        final List<PublicAiRunGrant> items = new ArrayList<>();
        public void save(PublicAiRunGrant grant) { items.add(grant); }
        public Optional<PublicAiRunGrant> find(String id, String hash, String slug, Instant now) {
            return conversation(hash, slug, now).filter(item -> item.runId().equals(id));
        }
        public Optional<PublicAiRunGrant> conversation(String hash, String slug, Instant now) {
            return items.stream().filter(item -> item.tokenHash().equals(hash) && item.spaceSlug().equals(slug) && item.expiresAt().isAfter(now)).findFirst();
        }
    }
}
