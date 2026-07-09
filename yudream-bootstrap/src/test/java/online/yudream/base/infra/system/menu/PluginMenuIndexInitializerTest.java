package online.yudream.base.infra.system.menu;

import online.yudream.base.domain.system.menu.enumerate.MenuSource;
import online.yudream.base.infra.system.menu.bootstrap.PluginMenuIndexInitializer;
import online.yudream.base.infra.system.menu.dataobj.MenuDO;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginMenuIndexInitializerTest {

    @Test
    void ensuresPluginRegistrationPartialUniqueIndex() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        IndexOperations indexOperations = mock(IndexOperations.class);
        when(mongoTemplate.indexOps(MenuDO.class)).thenReturn(indexOperations);
        when(indexOperations.ensureIndex(any(IndexDefinition.class))).thenReturn("uk_plugin_menu_registration");
        PluginMenuIndexInitializer initializer = new PluginMenuIndexInitializer(mongoTemplate);

        initializer.onApplicationEvent(null);

        ArgumentCaptor<IndexDefinition> captor = ArgumentCaptor.forClass(IndexDefinition.class);
        verify(indexOperations).ensureIndex(captor.capture());
        IndexDefinition definition = captor.getValue();
        assertThat(definition.getIndexKeys())
                .containsEntry("source", 1)
                .containsEntry("pluginCode", 1)
                .containsEntry("pluginRegistrationKey", 1);
        Document options = definition.getIndexOptions();
        CompoundIndex declaredIndex = MenuDO.class.getAnnotation(CompoundIndex.class);
        assertThat(options)
                .containsEntry("name", declaredIndex.name())
                .containsEntry("unique", true);
        assertThat((Document) options.get("partialFilterExpression"))
                .containsEntry("source", MenuSource.PLUGIN);
    }
}
