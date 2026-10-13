package online.yudream.base.infra.system.user.impl;

import online.yudream.base.infra.system.user.dataobj.UserDO;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PermissionOverviewUserRepoImplTest {

    @Test
    void combinesKeywordAndUnassignedFiltersAndReadsOnlySafeFieldsWithBoundedPagination() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.count(any(Query.class), eq(UserDO.class))).thenReturn(900L);
        when(mongo.find(any(Query.class), eq(UserDO.class))).thenReturn(List.of());
        var result = new PermissionOverviewUserRepoImpl(mongo).page("a.*", null, true, 2, 500);
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(query.capture(), eq(UserDO.class));
        assertEquals(900, result.getTotal());
        assertEquals(100, query.getValue().getLimit());
        assertEquals(100, query.getValue().getSkip());
        Document fields = query.getValue().getFieldsObject();
        assertEquals(Set.of("id", "username", "nickname", "roleIds", "depts", "status", "emailVerified"), fields.keySet());
        String filter = query.getValue().getQueryObject().toJson();
        assertTrue(filter.contains("$and"));
        assertTrue(filter.contains("$size"));
        assertTrue(filter.contains("$regularExpression"));
        assertFalse(filter.contains("email"));
        assertFalse(filter.contains("phone"));
    }

    @Test
    void clampsToTheLastPageAfterFilteredRelationshipsChange() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.count(any(Query.class), eq(UserDO.class))).thenReturn(29L);
        when(mongo.find(any(Query.class), eq(UserDO.class))).thenReturn(List.of());
        var result = new PermissionOverviewUserRepoImpl(mongo).page(null, null, true, 2, 30);
        assertEquals(1, result.getPage());
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(query.capture(), eq(UserDO.class));
        assertEquals(0, query.getValue().getSkip());
    }

    @Test
    void roleFilterUsesExactLongIdInsteadOfFuzzyMatching() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.find(any(Query.class), eq(UserDO.class))).thenReturn(List.of());
        new PermissionOverviewUserRepoImpl(mongo).page(null, 9007199254740993L, false, 1, 30);
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(query.capture(), eq(UserDO.class));
        List<Document> filters = query.getValue().getQueryObject().getList("$and", Document.class);
        assertEquals(9007199254740993L, filters.getFirst().get("roleIds"));
    }

    @Test
    void statisticsAggregateAllUsersAndOnlyProjectRoleRelationships() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        Document facet = new Document("stats", List.of(new Document("userCount", 800L)
                .append("assignmentCount", 1234L).append("unassignedUserCount", 19L)))
                .append("assignedRoles", List.of(new Document("_id", 1L), new Document("_id", 2L)));
        when(mongo.aggregate(any(Aggregation.class), eq("sysUser"), eq(Document.class)))
                .thenReturn(new AggregationResults<>(List.of(facet), new Document()));
        var result = new PermissionOverviewUserRepoImpl(mongo).statistics();
        assertEquals(800, result.userCount());
        assertEquals(1234, result.assignmentCount());
        assertEquals(19, result.unassignedUserCount());
        assertEquals(Set.of(1L, 2L), result.assignedRoleIds());
        ArgumentCaptor<Aggregation> aggregation = ArgumentCaptor.forClass(Aggregation.class);
        verify(mongo).aggregate(aggregation.capture(), eq("sysUser"), eq(Document.class));
        String pipeline = aggregation.getValue().toPipeline(Aggregation.DEFAULT_CONTEXT).toString();
        assertTrue(pipeline.contains("$setUnion"));
        assertFalse(pipeline.contains("$match"));
        assertFalse(pipeline.contains("status"));
        assertFalse(pipeline.contains("email"));
        assertFalse(pipeline.contains("phone"));
    }
}
