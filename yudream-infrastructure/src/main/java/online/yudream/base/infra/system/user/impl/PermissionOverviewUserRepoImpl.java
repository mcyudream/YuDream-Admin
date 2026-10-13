package online.yudream.base.infra.system.user.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.repo.PermissionOverviewUserRepo;
import online.yudream.base.domain.system.user.valobj.UserPermissionStatistics;
import online.yudream.base.infra.system.user.dataobj.UserDO;
import online.yudream.base.infra.system.user.mapper.UserInfraMapper;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PermissionOverviewUserRepoImpl implements PermissionOverviewUserRepo {

    private final MongoTemplate mongoTemplate;

    @Override
    public PageResult<User> page(String keyword, Long roleId, boolean unassignedOnly, int page, int size) {
        Query query = new Query();
        List<Criteria> filters = new ArrayList<>();
        if (StringUtils.hasText(keyword)) {
            String pattern = Pattern.quote(keyword.trim());
            filters.add(new Criteria().orOperator(
                    Criteria.where("username").regex(pattern, "i"),
                    Criteria.where("nickname").regex(pattern, "i")));
        }
        if (roleId != null) {
            filters.add(Criteria.where("roleIds").is(roleId));
        }
        if (unassignedOnly) {
            filters.add(new Criteria().orOperator(
                    Criteria.where("roleIds").is(null), Criteria.where("roleIds").size(0)));
        }
        if (!filters.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(filters));
        }
        long total = mongoTemplate.count(query, UserDO.class);
        int pageSize = Math.min(Math.max(size, 1), 100);
        long lastPage = Math.max(1, (total - 1) / pageSize + 1);
        int currentPage = (int) Math.min(Math.max(page, 1), lastPage);
        query.with(Sort.by(Sort.Order.asc("username"), Sort.Order.asc("id")))
                .skip((long) (currentPage - 1) * pageSize).limit(pageSize);
        query.fields().include("id", "username", "nickname", "roleIds", "depts", "status", "emailVerified");
        List<User> users = mongoTemplate.find(query, UserDO.class).stream().map(UserInfraMapper::toDomain).toList();
        return new PageResult<>(users, total, currentPage, pageSize);
    }

    @Override
    public UserPermissionStatistics statistics() {
        // 数据库全量聚合只处理角色 ID；同一用户重复保存的 ID 只计一条配置关系。
        Document normalized = new Document("$project", new Document("roleIds", new Document("$setUnion",
                List.of(new Document("$ifNull", List.of("$roleIds", List.of())), List.of()))));
        Document size = new Document("$size", "$roleIds");
        Document stats = new Document("$group", new Document("_id", null)
                .append("userCount", new Document("$sum", 1))
                .append("assignmentCount", new Document("$sum", size))
                .append("unassignedUserCount", new Document("$sum", new Document("$cond", List.of(
                        new Document("$eq", List.of(size, 0)), 1, 0)))));
        Document facet = new Document("$facet", new Document("stats", List.of(stats))
                .append("assignedRoles", List.of(new Document("$unwind", "$roleIds"),
                        new Document("$group", new Document("_id", "$roleIds")))));
        Document result = mongoTemplate.aggregate(Aggregation.newAggregation(
                context -> normalized, context -> facet), "sysUser", Document.class).getUniqueMappedResult();
        if (result == null) {
            return new UserPermissionStatistics(0, 0, 0, Set.of());
        }
        List<Document> counts = result.getList("stats", Document.class, List.of());
        Document count = counts.isEmpty() ? new Document() : counts.getFirst();
        Set<Long> roleIds = result.getList("assignedRoles", Document.class, List.of()).stream()
                .map(row -> row.get("_id")).filter(Number.class::isInstance)
                .map(value -> ((Number) value).longValue()).collect(Collectors.toSet());
        return new UserPermissionStatistics(number(count, "userCount"), number(count, "unassignedUserCount"),
                number(count, "assignmentCount"), roleIds);
    }

    private long number(Document document, String key) {
        Object value = document.get(key);
        return value instanceof Number number ? number.longValue() : 0;
    }
}
