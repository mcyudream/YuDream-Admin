package online.yudream.base.application.platform.cms;

import online.yudream.base.application.platform.cms.service.*;
import online.yudream.base.application.platform.cms.cmd.*;
import online.yudream.base.application.platform.theme.service.SiteThemeQueryService;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.cms.aggregate.StudioWorkspace;
import online.yudream.base.domain.platform.cms.repo.StudioWorkspaceRepo;
import online.yudream.base.domain.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudioAppServiceTest {
    final CapabilityModuleRepo capability = mock(CapabilityModuleRepo.class);
    final SiteThemeQueryService theme = mock(SiteThemeQueryService.class);
    final MemoryRepo repo = new MemoryRepo();
    final StudioAppService service = new StudioAppService(capability, repo, theme, new StudioDocumentValidator(), new com.fasterxml.jackson.databind.ObjectMapper());
    @BeforeEach void setup() {
        CapabilityModule module = mock(CapabilityModule.class);
        when(module.getEnabled()).thenReturn(true);
        when(capability.findByCode("cms")).thenReturn(Optional.of(module));
        when(theme.activeSiteThemeCode()).thenReturn("alpha");
    }
    @Test void themePackageSeedsAndPublishesOnlyWhenThemeHasNoWorkspace() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var pkg = mapper.createObjectNode().put("type", "yudream-site-package").put("version", 1).put("kind", "site");
        pkg.set("document", mapper.readTree(StudioDocumentValidatorTest.DOCUMENT));
        assertThat(service.seedThemePackage("beta", pkg)).isTrue();
        var state = service.state("beta");
        assertThat(state.revision()).isEqualTo("2");
        when(theme.activeSiteThemeCode()).thenReturn("beta");
        assertThat(service.publicDocument()).isNotNull();
        // 已有工作区（管理员已编辑）时不覆盖
        service.saveDraft("beta", new StudioDraftSaveCmd("2", StudioDocumentValidatorTest.DOCUMENT.replace("你好", "管理员修改")));
        assertThat(service.seedThemePackage("beta", pkg)).isFalse();
        assertThat(service.state("beta").revision()).isEqualTo("3");
        var bad = mapper.createObjectNode().put("type", "other");
        assertThatThrownBy(() -> service.seedThemePackage("gamma", bad)).isInstanceOf(BizException.class);
    }
    @Test void builtinSiteSeedsOnceAndPassesBackendValidation() {
        assertThat(service.seedBuiltinSite()).isTrue();
        when(theme.activeSiteThemeCode()).thenReturn(StudioAppService.BUILTIN_THEME_CODE);
        assertThat(service.publicDocument()).isNotNull();
        assertThat(service.state(StudioAppService.BUILTIN_THEME_CODE).revision()).isEqualTo("2");
        assertThat(service.seedBuiltinSite()).isFalse();
    }
    @Test void roundTripPublishRestoreNeverLeaksDraftAndIsThemeIsolated() {
        assertThat(service.state("alpha").revision()).isEqualTo("0");
        assertThat(service.publicDocument()).isNull();
        String first = StudioDocumentValidatorTest.DOCUMENT;
        service.saveDraft("alpha", new StudioDraftSaveCmd("0", first));
        var published = service.publish("alpha", new StudioRevisionCmd("1"));
        assertThat(published.revision()).isEqualTo("2");
        String second = first.replace("你好", "修改草稿");
        service.saveDraft("alpha", new StudioDraftSaveCmd("2", second));
        assertThat(service.publicDocument().documentJson()).isEqualTo(first);
        var secondRelease = service.publish("alpha", new StudioRevisionCmd("3"));
        var restored = service.restore("alpha", published.publishedReleaseId(), new StudioRevisionCmd("4"));
        assertThat(restored.documentJson()).isEqualTo(first);
        assertThat(restored.publishedReleaseId()).isEqualTo(secondRelease.publishedReleaseId());
        assertThat(service.publicDocument().documentJson()).isEqualTo(second);
        assertThat(service.state("beta").documentJson()).isNull();
        assertThatThrownBy(() -> service.restore("beta", published.publishedReleaseId(), new StudioRevisionCmd("0"))).isInstanceOf(BizException.class);
        when(theme.activeSiteThemeCode()).thenReturn("beta");
        assertThat(service.publicDocument()).isNull();
        var releases = service.releases("alpha", 2, 1);
        assertThat(releases.getTotal()).isEqualTo(2);
        assertThat(releases.getRecords()).singleElement().satisfies(item -> assertThat(item.id()).isEqualTo(published.publishedReleaseId()));
    }
    @Test void unpublishRetainsDraftAndSnapshotsAndCanPublishAgain() {
        String document = StudioDocumentValidatorTest.DOCUMENT;
        service.saveDraft("alpha", new StudioDraftSaveCmd("0", document));
        var published = service.publish("alpha", new StudioRevisionCmd("1"));
        assertThatThrownBy(() -> service.unpublish("alpha", new StudioRevisionCmd("1"))).hasMessageContaining("冲突");
        var unpublished = service.unpublish("alpha", new StudioRevisionCmd("2"));
        assertThat(unpublished.revision()).isEqualTo("3");
        assertThat(unpublished.publishedReleaseId()).isNull();
        assertThat(unpublished.documentJson()).isEqualTo(document);
        assertThat(service.publicDocument()).isNull();
        assertThat(service.releases("alpha", 1, 10).getRecords()).singleElement().satisfies(item -> assertThat(item.id()).isEqualTo(published.publishedReleaseId()));
        var republished = service.publish("alpha", new StudioRevisionCmd("3"));
        assertThat(republished.revision()).isEqualTo("4");
        assertThat(service.publicDocument().documentJson()).isEqualTo(document);
        assertThat(service.releases("alpha", 1, 10).getTotal()).isEqualTo(2);
    }
    @Test void staleRevisionRejectsEveryWriteAndNeverRetriesCas() {
        service.saveDraft("alpha", new StudioDraftSaveCmd("0", StudioDocumentValidatorTest.DOCUMENT));
        assertThatThrownBy(() -> service.saveDraft("alpha", new StudioDraftSaveCmd("0", StudioDocumentValidatorTest.DOCUMENT))).isInstanceOf(BizException.class).hasMessageContaining("冲突");
        assertThatThrownBy(() -> service.publish("alpha", new StudioRevisionCmd("0"))).isInstanceOf(BizException.class).hasMessageContaining("冲突");
        StudioWorkspaceRepo conflictRepo = mock(StudioWorkspaceRepo.class);
        when(conflictRepo.findByThemeCode("alpha")).thenReturn(Optional.empty());
        var conflict = new StudioAppService(capability, conflictRepo, theme, new StudioDocumentValidator(), new com.fasterxml.jackson.databind.ObjectMapper());
        assertThatThrownBy(() -> conflict.saveDraft("alpha", new StudioDraftSaveCmd("0", StudioDocumentValidatorTest.DOCUMENT))).isInstanceOf(BizException.class).hasMessageContaining("冲突");
        verify(conflictRepo, times(1)).compareAndSet(eq(0L), any());
    }
    @Test void allUseCasesAreGuardedBeforeStorage() {
        when(capability.findByCode("cms")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.state("alpha")).hasMessageContaining("未启用");
        assertThatThrownBy(() -> service.saveDraft("alpha", new StudioDraftSaveCmd("0", "{}"))).hasMessageContaining("未启用");
        assertThatThrownBy(() -> service.publish("alpha", new StudioRevisionCmd("0"))).hasMessageContaining("未启用");
        assertThatThrownBy(() -> service.unpublish("alpha", new StudioRevisionCmd("0"))).hasMessageContaining("未启用");
        assertThatThrownBy(() -> service.restore("alpha", "x", new StudioRevisionCmd("0"))).hasMessageContaining("未启用");
        assertThatThrownBy(() -> service.releases("alpha", 1, 10)).hasMessageContaining("未启用");
        assertThatThrownBy(service::publicDocument).hasMessageContaining("未启用");
        assertThat(repo.values).isEmpty();
    }
    @Test void immutableSnapshotsHaveCountAndByteBudgetAndKeepOnlinePointer() {
        Instant now = Instant.now();
        var workspace = StudioWorkspace.empty("alpha").saveDraft("0", "中".repeat(600000), now);
        for (int i = 0; i < 30; i++) workspace = workspace.publish(Long.toString(workspace.revision()), "r" + i, now);
        assertThat(workspace.releases()).hasSizeLessThanOrEqualTo(20);
        assertThat(workspace.releases()).hasSizeLessThan(10);
        assertThat(workspace.published().id()).isEqualTo("r29");
        String online = workspace.publishedReleaseId();
        workspace = workspace.saveDraft(Long.toString(workspace.revision()), "新草稿", now);
        assertThat(workspace.published().id()).isEqualTo(online);
        var retained = workspace.releases();
        assertThatThrownBy(retained::clear).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void smallReleaseRetentionIsExactlyTwentyAndRevisionNeverLosesLongPrecision() {
        Instant now = Instant.now();
        var workspace = StudioWorkspace.empty("alpha").saveDraft("0", "{}", now);
        for (int i = 0; i < 30; i++) workspace = workspace.publish(Long.toString(workspace.revision()), "r" + i, now);
        assertThat(workspace.releases()).hasSize(20);
        assertThat(workspace.releases().getFirst().id()).isEqualTo("r29");
        assertThat(workspace.releases().getLast().id()).isEqualTo("r10");
        var large = new StudioWorkspace("alpha", 9007199254740993L, "{}", null, now, java.util.List.of());
        assertThat(large.saveDraft("9007199254740993", "{}", now).revision()).isEqualTo(9007199254740994L);
        assertThatThrownBy(() -> large.saveDraft("9007199254740992", "{}", now)).hasMessageContaining("冲突");
    }
    @Test void concurrentSameRevisionHasExactlyOneWinner() throws Exception {
        var start = new CountDownLatch(1);
        var next = StudioWorkspace.empty("alpha").saveDraft("0", "{}", Instant.now());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(() -> { start.await(); return repo.compareAndSet(0, next); });
            var b = executor.submit(() -> { start.await(); return repo.compareAndSet(0, next); });
            start.countDown();
            assertThat(a.get() ^ b.get()).isTrue();
        }
    }
    static class MemoryRepo implements StudioWorkspaceRepo {
        final ConcurrentHashMap<String, StudioWorkspace> values = new ConcurrentHashMap<>();
        public Optional<StudioWorkspace> findByThemeCode(String themeCode) { return Optional.ofNullable(values.get(themeCode)); }
        public synchronized boolean compareAndSet(long expected, StudioWorkspace workspace) {
            var current = values.get(workspace.themeCode());
            if ((current == null ? 0 : current.revision()) != expected) return false;
            values.put(workspace.themeCode(), workspace); return true;
        }
    }
}
