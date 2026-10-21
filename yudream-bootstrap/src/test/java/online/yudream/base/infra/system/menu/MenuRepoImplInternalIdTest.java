package online.yudream.base.infra.system.menu;

import online.yudream.base.domain.system.menu.aggregate.Menu;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuSource;
import online.yudream.base.infra.common.baseobj.BaseDO;
import online.yudream.base.infra.system.menu.dataobj.MenuDO;
import online.yudream.base.infra.system.menu.impl.MenuRepoImpl;
import online.yudream.base.infra.system.menu.mapper.MenuInfraMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MenuRepoImplInternalIdTest {

    private static final Long RECORD_ID = 100L;

    @Test
    void findByIdReturnsDomainWithStableInternalId() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        MenuDO menuDO = new MenuDO();
        menuDO.setId(RECORD_ID);
        menuDO.setCode("system:report");
        menuDO.setName("报表");
        menuDO.setType(MenuNodeType.MENU);
        menuDO.setSource(MenuSource.SYSTEM);
        when(mongoTemplate.findById(RECORD_ID, MenuDO.class)).thenReturn(menuDO);
        MenuRepoImpl repo = new MenuRepoImpl(mongoTemplate);

        Optional<Menu> found = repo.findById(RECORD_ID);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(RECORD_ID);
        assertThat(found.get().getCode()).isEqualTo("system:report");
    }

    @Test
    void findByNullIdIsEmpty() {
        MenuRepoImpl repo = new MenuRepoImpl(mock(MongoTemplate.class));

        assertThat(repo.findById(null)).isEmpty();
    }

    @Test
    void saveReattachesExistingRecordIdWithVersionAndCreateTime() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        MenuDO existing = new MenuDO();
        existing.setId(RECORD_ID);
        existing.setCode("system:report");
        existing.setVersion(3);
        existing.setCreateTime(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
        when(mongoTemplate.findOne(any(org.springframework.data.mongodb.core.query.Query.class), eq(MenuDO.class)))
                .thenReturn(existing);
        when(mongoTemplate.save(any(MenuDO.class))).thenAnswer(inv -> inv.getArgument(0));
        MenuRepoImpl repo = new MenuRepoImpl(mongoTemplate);

        Menu updated = Menu.builder()
                .id(999L) // 调用方携带的 id 与既有记录不同时按 code 重新挂接
                .code("system:report")
                .name("报表(v2)")
                .type(MenuNodeType.MENU)
                .source(MenuSource.SYSTEM)
                .build();

        Menu saved = repo.save(updated);

        ArgumentCaptor<MenuDO> captor = ArgumentCaptor.forClass(MenuDO.class);
        verify(mongoTemplate).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(RECORD_ID);
        assertThat(captor.getValue().getVersion()).isEqualTo(3);
        assertThat(captor.getValue().getCreateTime()).isEqualTo(existing.getCreateTime());
        assertThat(saved.getId()).isEqualTo(RECORD_ID);
        assertThat(saved.getName()).isEqualTo("报表(v2)");
    }

    @Test
    void mapperKeepsInternalIdOnBothDirections() {
        MenuDO menuDO = new MenuDO();
        menuDO.setId(RECORD_ID);
        menuDO.setCode("system:report");

        Menu domain = MenuInfraMapper.toDomain(menuDO);
        assertThat(domain.getId()).isEqualTo(RECORD_ID);

        MenuDO back = MenuInfraMapper.toDataObj(domain);
        assertThat(back.getId()).isEqualTo(RECORD_ID);
        assertThat(BaseDO.class.isAssignableFrom(back.getClass())).isTrue();
    }
}
