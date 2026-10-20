package online.yudream.base.application.platform.cms;

import online.yudream.base.application.platform.cms.cmd.StudioBlockTemplateSaveCmd;
import online.yudream.base.application.platform.cms.service.StudioBlockTemplateAppService;
import online.yudream.base.application.platform.cms.service.StudioDocumentValidator;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.cms.aggregate.StudioBlockTemplate;
import online.yudream.base.domain.platform.cms.repo.StudioBlockTemplateRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StudioBlockTemplateAppServiceTest {
    static final String NODES = "[{\"id\":\"n1\",\"type\":\"heading\",\"tag\":\"h2\",\"text\":\"标题\",\"attributes\":{},\"styles\":{\"color\":\"red\"},\"children\":[]}]";

    final CapabilityModuleRepo capability = mock(CapabilityModuleRepo.class);
    final MemoryRepo repo = new MemoryRepo();
    final StudioBlockTemplateAppService service = new StudioBlockTemplateAppService(capability, repo, new StudioDocumentValidator());
    final CapabilityModule module = mock(CapabilityModule.class);

    @BeforeEach
    void setup() {
        when(module.getEnabled()).thenReturn(true);
        when(capability.findByCode("cms")).thenReturn(Optional.of(module));
    }

    @Test
    void createUpdateDeleteWithRevisionCheck() {
        var created = service.create(new StudioBlockTemplateSaveCmd("页首横幅", "layout", "说明", List.of("hero", "hero", " "), NODES, null), 371011642424692736L);
        assertThat(created.revision()).isEqualTo("1");
        assertThat(created.createdBy()).isEqualTo("371011642424692736");
        assertThat(created.keywords()).containsExactly("hero");
        var renamed = service.update(created.id(), new StudioBlockTemplateSaveCmd("新名称", "design", "", List.of(), null, "1"));
        assertThat(renamed.revision()).isEqualTo("2");
        assertThat(renamed.nodesJson()).isEqualTo(NODES);
        assertThatThrownBy(() -> service.update(created.id(), new StudioBlockTemplateSaveCmd("旧版本覆盖", "design", "", List.of(), null, "1")))
                .isInstanceOf(BizException.class).hasMessageContaining("已被他人修改");
        assertThat(service.list()).extracting("name").containsExactly("新名称");
        service.delete(created.id());
        assertThat(service.list()).isEmpty();
        assertThatThrownBy(() -> service.delete(created.id())).isInstanceOf(BizException.class);
    }

    @Test
    void rejectsUnsafeOrMalformedContentAndInvalidMetadata() {
        String script = NODES.replace("\"tag\":\"h2\"", "\"tag\":\"script\"");
        assertThatThrownBy(() -> service.create(new StudioBlockTemplateSaveCmd("x", "layout", "", List.of(), script, null), 1L)).isInstanceOf(BizException.class);
        String handler = NODES.replace("\"attributes\":{}", "\"attributes\":{\"onclick\":\"alert(1)\"}");
        assertThatThrownBy(() -> service.create(new StudioBlockTemplateSaveCmd("x", "layout", "", List.of(), handler, null), 1L)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.create(new StudioBlockTemplateSaveCmd("x", "layout", "", List.of(), "[]", null), 1L)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.create(new StudioBlockTemplateSaveCmd("x", "layout", "", List.of(), "{\"id\":1}", null), 1L)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.create(new StudioBlockTemplateSaveCmd("x", "unknown", "", List.of(), NODES, null), 1L)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.create(new StudioBlockTemplateSaveCmd(" ", "layout", "", List.of(), NODES, null), 1L)).isInstanceOf(BizException.class);
        assertThat(repo.items).isEmpty();
    }

    @Test
    void capabilityGateAppliesToEveryUseCase() {
        when(module.getEnabled()).thenReturn(false);
        assertThatThrownBy(service::list).isInstanceOf(BizException.class).hasMessageContaining("未启用");
        assertThatThrownBy(() -> service.create(new StudioBlockTemplateSaveCmd("x", "layout", "", List.of(), NODES, null), 1L)).isInstanceOf(BizException.class);
    }

    static class MemoryRepo implements StudioBlockTemplateRepo {
        final Map<String, StudioBlockTemplate> items = new LinkedHashMap<>();
        @Override public List<StudioBlockTemplate> findAll() { return new ArrayList<>(items.values()); }
        @Override public Optional<StudioBlockTemplate> findById(String id) { return Optional.ofNullable(items.get(id)); }
        @Override public long count() { return items.size(); }
        @Override public void insert(StudioBlockTemplate template) { items.put(template.id(), template); }
        @Override public boolean compareAndSet(long expectedRevision, StudioBlockTemplate template) {
            StudioBlockTemplate current = items.get(template.id());
            if (current == null || current.revision() != expectedRevision) return false;
            items.put(template.id(), template);
            return true;
        }
        @Override public boolean delete(String id) { return items.remove(id) != null; }
    }
}
