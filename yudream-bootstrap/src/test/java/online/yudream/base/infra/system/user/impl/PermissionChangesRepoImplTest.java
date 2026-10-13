package online.yudream.base.infra.system.user.impl;

import online.yudream.base.domain.system.user.enumerate.SystemRoleType;
import online.yudream.base.infra.system.user.dataobj.RoleDO;
import online.yudream.base.infra.system.user.dataobj.UserDO;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PermissionChangesRepoImplTest {

    @Test
    void roleCasMatchesTheExactPermissionArrayAndOnlyUpdatesPermissions() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        RoleDO updated = new RoleDO();
        updated.setId(9007199254740993L);
        updated.setName("并发修改后的角色名称");
        updated.setPermissions(List.of("unknown", "system:user:view"));
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(RoleDO.class)))
                .thenReturn(updated);
        var result = new RolePermissionChangesRepoImpl(mongo).compareAndSet(updated.getId(),
                List.of("unknown"), updated.getPermissions());
        assertEquals(updated.getName(), result.orElseThrow().getName());
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        ArgumentCaptor<FindAndModifyOptions> options = ArgumentCaptor.forClass(FindAndModifyOptions.class);
        verify(mongo).findAndModify(query.capture(), update.capture(), options.capture(), eq(RoleDO.class));
        List<Document> guards = query.getValue().getQueryObject().getList("$and", Document.class);
        assertEquals(9007199254740993L, guards.get(0).get("id"));
        assertEquals(List.of("unknown"), guards.get(1).get("permissions"));
        assertEquals(SystemRoleType.SUPER_ADMIN, guards.get(2).get("systemType", Document.class).get("$ne"));
        assertEquals(new Document("$set", new Document("permissions", updated.getPermissions())), update.getValue().getUpdateObject());
        assertTrue(options.getValue().isReturnNew());
        assertFalse(options.getValue().isUpsert());
    }

    @Test
    void aRoleCasMissReturnsAConflictWithoutSavingTheWholeRole() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        assertTrue(new RolePermissionChangesRepoImpl(mongo).compareAndSet(1L, List.of(), List.of("new")).isEmpty());
        verify(mongo, never()).save(any());
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).findAndModify(query.capture(), any(Update.class), any(FindAndModifyOptions.class), eq(RoleDO.class));
        assertTrue(query.getValue().getQueryObject().toString().contains("$or"));
    }

    @Test
    void userCasOnlyUpdatesRoleIdsAndRechecksRequiredDepartmentMembership() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(UserDO.class)))
                .thenReturn(new UserDO());
        assertTrue(new UserRoleChangesRepoImpl(mongo).compareAndSet(9007199254740993L,
                List.of(7L, 999L), List.of(7L, 999L, 8L), List.of(10L)));
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).findAndModify(query.capture(), update.capture(), any(FindAndModifyOptions.class), eq(UserDO.class));
        List<Document> guards = query.getValue().getQueryObject().getList("$and", Document.class);
        assertEquals(List.of(7L, 999L), guards.get(1).get("roleIds"));
        assertEquals(List.of(10L), query.getValue().getQueryObject().get("depts.deptId", Document.class).get("$all"));
        assertEquals(new Document("$set", new Document("roleIds", List.of(7L, 999L, 8L))), update.getValue().getUpdateObject());
        verify(mongo, never()).save(any());
    }

    @Test
    void revokingAnUnknownRoleDoesNotRequireAnUnrelatedDepartmentAndCasMissIsFalse() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        assertFalse(new UserRoleChangesRepoImpl(mongo).compareAndSet(1L, List.of(999L), List.of(), List.of()));
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).findAndModify(query.capture(), any(Update.class), any(FindAndModifyOptions.class), eq(UserDO.class));
        assertFalse(query.getValue().getQueryObject().containsKey("depts.deptId"));
    }
}
