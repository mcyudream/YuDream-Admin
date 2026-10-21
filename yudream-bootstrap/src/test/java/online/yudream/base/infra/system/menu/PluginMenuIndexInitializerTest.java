package online.yudream.base.infra.system.menu;

import online.yudream.base.domain.system.menu.enumerate.MenuSource;
import online.yudream.base.domain.system.menu.service.MenuDomainService;
import online.yudream.base.infra.system.menu.bootstrap.PluginMenuIndexInitializer;
import online.yudream.base.infra.system.menu.dataobj.MenuDO;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.Indexed;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginMenuIndexInitializerTest {

    private static final String CODE_INDEX_NAME = "uk_menu_code";

    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private MenuDomainService menuDomainService;

    private IndexOperations indexOperations;

    private PluginMenuIndexInitializer initializer;

    @BeforeEach
    void setUp() {
        indexOperations = mock(IndexOperations.class);
        lenient().when(mongoTemplate.indexOps(MenuDO.class)).thenReturn(indexOperations);
        initializer = new PluginMenuIndexInitializer(mongoTemplate, menuDomainService);
    }

    @Test
    void healsDuplicatesBeforeCreatingUniqueIndexes() {
        initializer.onApplicationEvent(null);

        InOrder order = inOrder(menuDomainService, indexOperations);
        order.verify(menuDomainService).dedupeByCode();
        order.verify(indexOperations, times(2)).ensureIndex(any(IndexDefinition.class));
    }

    @Test
    void stillEnsuresIndexesAfterHealingDuplicates() {
        when(menuDomainService.dedupeByCode()).thenReturn(1);

        initializer.onApplicationEvent(null);

        InOrder order = inOrder(menuDomainService, indexOperations);
        order.verify(menuDomainService).dedupeByCode();
        order.verify(indexOperations, times(2)).ensureIndex(any(IndexDefinition.class));
    }

    @Test
    void abortsIndexCreationWhenHealingFails() {
        when(menuDomainService.dedupeByCode()).thenThrow(new IllegalStateException("cleanup failed"));

        try {
            initializer.onApplicationEvent(null);
        } catch (IllegalStateException ignored) {
            // 允许失败上抛，启动期不得在脏数据上建唯一索引
        }

        verify(indexOperations, never()).ensureIndex(any(IndexDefinition.class));
    }

    @Test
    void ensuresUniqueCodeIndexMatchingDeclaredDefinition() {
        initializer.onApplicationEvent(null);

        ArgumentCaptor<IndexDefinition> captor = ArgumentCaptor.forClass(IndexDefinition.class);
        verify(indexOperations, times(2)).ensureIndex(captor.capture());
        IndexDefinition codeIndex = captor.getAllValues().getFirst();

        assertThat(codeIndex.getIndexKeys()).containsEntry("code", 1);
        Document options = codeIndex.getIndexOptions();
        assertThat(options)
                .containsEntry("name", CODE_INDEX_NAME)
                .containsEntry("unique", true);

        Indexed declared = declaredCodeIndex();
        assertThat(declared.name()).isEqualTo(options.get("name"));
        assertThat(declared.unique()).isEqualTo(options.get("unique"));
    }

    @Test
    void ensuresPluginRegistrationPartialUniqueIndex() {
        initializer.onApplicationEvent(null);

        ArgumentCaptor<IndexDefinition> captor = ArgumentCaptor.forClass(IndexDefinition.class);
        verify(indexOperations, times(2)).ensureIndex(captor.capture());
        IndexDefinition pluginIndex = captor.getAllValues().get(1);

        assertThat(pluginIndex.getIndexKeys())
                .containsEntry("source", 1)
                .containsEntry("pluginCode", 1)
                .containsEntry("pluginRegistrationKey", 1);
        Document options = pluginIndex.getIndexOptions();
        CompoundIndex declaredIndex = MenuDO.class.getAnnotation(CompoundIndex.class);
        assertThat(options)
                .containsEntry("name", declaredIndex.name())
                .containsEntry("unique", true);
        assertThat((Document) options.get("partialFilterExpression"))
                .containsEntry("source", MenuSource.PLUGIN);
    }

    private Indexed declaredCodeIndex() {
        try {
            Field field = MenuDO.class.getDeclaredField("code");
            return field.getAnnotation(Indexed.class);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }
}
