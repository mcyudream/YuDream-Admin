package online.yudream.base.infra.system.user.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.system.user.repo.UserStatsRepo;
import online.yudream.base.domain.system.user.valobj.UserStatistics;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户统计只读实现：一条 $facet 管道在 sysUser 全量数据上聚合，禁止从分页结果推导。
 * 今日/本周/趋势的日期一律按服务器本地时区（JVM 默认时区）折算为 yyyy-MM-dd 再比较，
 * 与用户可见的「本地当天」「本地周一」保持一致。
 */
@Repository
@RequiredArgsConstructor
public class UserStatsRepoImpl implements UserStatsRepo {

    private static final String COLLECTION = "sysUser";

    private final MongoTemplate mongoTemplate;

    @Override
    public UserStatistics statistics(LocalDate trendFrom, String monthlyFrom) {
        ZoneId zone = ZoneId.systemDefault();
        String timezone = mongoTimezone(zone);
        LocalDate today = LocalDate.now(zone);
        // 本地日期串是 ISO 格式，字典序等于时间序，可直接作为聚合内的边界比较值
        String todayText = today.toString();
        String weekStartText = today.with(DayOfWeek.MONDAY).toString();

        Document basic = new Document("$group", new Document("_id", null)
                .append("total", new Document("$sum", 1))
                .append("verifiedCount", new Document("$sum", new Document("$cond", List.of("$emailVerified", 1, 0))))
                .append("activeCount", new Document("$sum", new Document("$cond",
                        List.of(new Document("$eq", List.of("$status", "ACTIVE")), 1, 0))))
                .append("disabledCount", new Document("$sum", new Document("$cond",
                        List.of(new Document("$eq", List.of("$status", "DISABLED")), 1, 0))))
                // createTime 缺失时 localDay 结果为 null，两类比较均不成立，自然不会计入
                .append("newToday", new Document("$sum", new Document("$cond",
                        List.of(new Document("$eq", List.of(localDay("$createTime", timezone), todayText)), 1, 0))))
                .append("newThisWeek", new Document("$sum", new Document("$cond",
                        List.of(new Document("$gte", List.of(localDay("$createTime", timezone), weekStartText)), 1, 0)))));

        Document completeness = new Document("$group", new Document("_id", null)
                .append("deptFilledCount", nonEmptyArray("$depts"))
                .append("roleFilledCount", nonEmptyArray("$roleIds"))
                .append("emailFilledCount", filledText("$email"))
                .append("phoneFilledCount", filledText("$phone"))
                .append("qqFilledCount", filledText("$qq")));

        List<Document> trend = List.of(
                new Document("$group", new Document("_id", localDay("$createTime", timezone))
                        .append("count", new Document("$sum", 1))),
                new Document("$match", new Document("_id", new Document("$gte", trendFrom.toString()))),
                new Document("$sort", new Document("_id", 1)));

        // 同一用户重复保存的 roleId 先用 $setUnion 归一，再 unwind 统计，保证只计一次
        List<Document> roleCounts = List.of(
                new Document("$project", new Document("roleIds", new Document("$setUnion",
                        List.of(new Document("$ifNull", List.of("$roleIds", List.of())), List.of())))),
                new Document("$unwind", "$roleIds"),
                new Document("$group", new Document("_id", "$roleIds").append("count", new Document("$sum", 1))));

        // 月度趋势：与日趋势同一时区折算，月份串（yyyy-MM）字典序等于时间序，可直接做边界比较
        List<Document> monthly = List.of(
                new Document("$group", new Document("_id", localMonth("$createTime", timezone))
                        .append("count", new Document("$sum", 1))),
                new Document("$match", new Document("_id", new Document("$gte", monthlyFrom))),
                new Document("$sort", new Document("_id", 1)));

        // 部门分布：先把用户-部门映射成 deptId 集合去重，再 unwind 统计，同一用户同一部门只计一次
        List<Document> deptCounts = List.of(
                new Document("$project", new Document("deptIds", new Document("$setUnion", List.of(
                        new Document("$map", new Document("input", new Document("$ifNull", List.of("$depts", List.of())))
                                .append("as", "dept").append("in", "$$dept.deptId")), List.of())))),
                new Document("$unwind", "$deptIds"),
                new Document("$group", new Document("_id", "$deptIds").append("count", new Document("$sum", 1))));

        Document facet = new Document("$facet", new Document("basic", List.of(basic))
                .append("trend", trend)
                .append("roleCounts", roleCounts)
                .append("monthly", monthly)
                .append("deptCounts", deptCounts)
                .append("completeness", List.of(completeness)));

        Document result = mongoTemplate.aggregate(Aggregation.newAggregation(context -> facet), COLLECTION, Document.class)
                .getUniqueMappedResult();
        if (result == null) {
            return empty();
        }
        Document count = firstOf(result, "basic");
        Document completenessCount = firstOf(result, "completeness");
        List<UserStatistics.DailyNew> dailyNews = new ArrayList<>();
        for (Document row : result.getList("trend", Document.class, List.of())) {
            if (row.get("_id") instanceof String date) {
                dailyNews.add(new UserStatistics.DailyNew(LocalDate.parse(date), number(row, "count")));
            }
        }
        Map<Long, Long> roleCountsById = new LinkedHashMap<>();
        for (Document row : result.getList("roleCounts", Document.class, List.of())) {
            if (row.get("_id") instanceof Number roleId) {
                roleCountsById.merge(roleId.longValue(), number(row, "count"), Long::sum);
            }
        }
        List<UserStatistics.MonthlyNew> monthlyNews = new ArrayList<>();
        for (Document row : result.getList("monthly", Document.class, List.of())) {
            if (row.get("_id") instanceof String month) {
                monthlyNews.add(new UserStatistics.MonthlyNew(month, number(row, "count")));
            }
        }
        Map<Long, Long> deptCountsById = new LinkedHashMap<>();
        for (Document row : result.getList("deptCounts", Document.class, List.of())) {
            if (row.get("_id") instanceof Number deptId) {
                deptCountsById.merge(deptId.longValue(), number(row, "count"), Long::sum);
            }
        }
        Map<Long, Long> sortedRoleCounts = roleCountsById.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left,
                        LinkedHashMap::new));
        return new UserStatistics(number(count, "total"), number(count, "verifiedCount"),
                number(count, "activeCount"), number(count, "disabledCount"), number(count, "newToday"),
                number(count, "newThisWeek"), number(completenessCount, "deptFilledCount"),
                number(completenessCount, "roleFilledCount"), number(completenessCount, "emailFilledCount"),
                number(completenessCount, "phoneFilledCount"), number(completenessCount, "qqFilledCount"),
                dailyNews, sortedRoleCounts, monthlyNews, deptCountsById);
    }

    /** 按服务器本地时区把日期字段折算成 yyyy-MM-dd 字符串。 */
    private static Document localDay(String field, String timezone) {
        return new Document("$dateToString", new Document("format", "%Y-%m-%d")
                .append("date", field).append("timezone", timezone));
    }

    /** 按服务器本地时区把日期字段折算成 yyyy-MM 字符串。 */
    private static Document localMonth(String field, String timezone) {
        return new Document("$dateToString", new Document("format", "%Y-%m")
                .append("date", field).append("timezone", timezone));
    }

    /** 数组非空计数：字段缺失或空数组都视为未填写。 */
    private static Document nonEmptyArray(String field) {
        Document size = new Document("$size", new Document("$ifNull", List.of(field, List.of())));
        return new Document("$sum", new Document("$cond", List.of(new Document("$gt", List.of(size, 0)), 1, 0)));
    }

    /** 非空字符串计数：字段缺失、null 与空串都视为未填写。 */
    private static Document filledText(String field) {
        Document text = new Document("$ifNull", List.of(field, ""));
        return new Document("$sum", new Document("$cond",
                List.of(new Document("$ne", List.of(text, "")), 1, 0)));
    }

    /**
     * Mongo 的 timezone 表达式只接受 Olson 时区名或 ±hh:mm 固定偏移；
     * JVM 以 GMT+08:00、Etc/GMT-8 等运行时不识别为 Olson 名，退化为偏移写法（当前偏移，不随夏令时变化）。
     */
    private static String mongoTimezone(ZoneId zone) {
        String id = zone.getId();
        boolean olsonLike = !(id.startsWith("GMT") || id.startsWith("UTC") || id.startsWith("Etc/")
                || id.startsWith("+") || id.startsWith("-"));
        if (olsonLike) {
            return id;
        }
        String offset = zone.getRules().getOffset(Instant.now()).getId();
        return "Z".equals(offset) ? "+00:00" : offset;
    }

    private static Document firstOf(Document result, String key) {
        List<Document> rows = result.getList(key, Document.class, List.of());
        return rows.isEmpty() ? new Document() : rows.getFirst();
    }

    private static long number(Document document, String key) {
        Object value = document.get(key);
        return value instanceof Number number ? number.longValue() : 0;
    }

    private static UserStatistics empty() {
        return new UserStatistics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), Map.of(), List.of(), Map.of());
    }
}
