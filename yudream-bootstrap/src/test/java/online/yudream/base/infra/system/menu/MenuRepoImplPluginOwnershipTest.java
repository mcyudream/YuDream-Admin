package online.yudream.base.infra.system.menu;

import online.yudream.base.domain.system.menu.enumerate.MenuSource;
import online.yudream.base.infra.system.menu.dataobj.MenuDO;
import online.yudream.base.infra.system.menu.impl.MenuRepoImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MenuRepoImplPluginOwnershipTest {

    @Test
    void registrationLookupOnlyMatchesPluginMenus() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.findOne(any(Query.class), eq(MenuDO.class))).thenReturn(null);
        MenuRepoImpl repo = new MenuRepoImpl(mongoTemplate);

        repo.findByPluginCodeAndRegistrationKey("wallet", "route:wallet:home");

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findOne(captor.capture(), eq(MenuDO.class));
        assertThat(captor.getValue().getQueryObject())
                .containsEntry("source", MenuSource.PLUGIN)
                .containsEntry("pluginCode", "wallet")
                .containsEntry("pluginRegistrationKey", "route:wallet:home");
    }

    @Test
    void pluginListOnlyMatchesPluginMenus() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.find(any(Query.class), eq(MenuDO.class))).thenReturn(List.of());
        MenuRepoImpl repo = new MenuRepoImpl(mongoTemplate);

        repo.findByPluginCode("wallet");

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(captor.capture(), eq(MenuDO.class));
        assertThat(captor.getValue().getQueryObject())
                .containsEntry("source", MenuSource.PLUGIN)
                .containsEntry("pluginCode", "wallet");
    }
}
