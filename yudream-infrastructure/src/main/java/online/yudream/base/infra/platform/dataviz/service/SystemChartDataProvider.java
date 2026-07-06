package online.yudream.base.infra.platform.dataviz.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDataSeries;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDatasetQuery;
import online.yudream.base.domain.system.user.enumerate.DeptStatus;
import online.yudream.base.domain.system.user.enumerate.UserStatus;
import online.yudream.base.infra.system.monitor.dataobj.ApiLogDO;
import online.yudream.base.infra.system.monitor.dataobj.LoginLogDO;
import online.yudream.base.infra.system.user.dataobj.DeptDO;
import online.yudream.base.infra.system.user.dataobj.UserDO;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
public class SystemChartDataProvider implements ChartDataProvider {

    private static final String METRIC_USER_STATUS = "user-status";
    private static final String METRIC_USER_REGISTRATION = "user-registration";
    private static final String METRIC_DEPT_STATUS = "dept-status";
    private static final String METRIC_DEPT_CREATED = "dept-created";
    private static final String METRIC_LOG_RESULT = "log-result";
    private static final String METRIC_LOG_ACTIVITY = "log-activity";
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy/M/d");
    private static final int TREND_DAYS = 8;

    private final MongoTemplate mongoTemplate;

    @Override
    public String source() {
        return "system";
    }

    @Override
    public List<ChartDataSeries> query(ChartDatasetQuery query) {
        return switch (query.metric()) {
            case METRIC_USER_STATUS -> userStatus();
            case METRIC_USER_REGISTRATION -> userRegistration();
            case METRIC_DEPT_STATUS -> deptStatus();
            case METRIC_DEPT_CREATED -> deptCreated();
            case METRIC_LOG_RESULT -> logResult();
            case METRIC_LOG_ACTIVITY -> logActivity();
            default -> throw new BizException("不支持的系统统计指标：" + query.metric());
        };
    }

    private List<ChartDataSeries> userRegistration() {
        return trend("用户注册", UserDO.class);
    }

    private List<ChartDataSeries> userStatus() {
        return List.of(new ChartDataSeries(
                "用户数",
                List.of("启用用户", "停用用户"),
                List.of(count(UserDO.class, Criteria.where("status").is(UserStatus.ACTIVE)),
                        count(UserDO.class, Criteria.where("status").is(UserStatus.DISABLED))),
                null,
                null
        ));
    }

    private List<ChartDataSeries> deptCreated() {
        return trend("新增部门", DeptDO.class);
    }

    private List<ChartDataSeries> deptStatus() {
        return List.of(new ChartDataSeries(
                "部门数",
                List.of("启用部门", "停用部门"),
                List.of(count(DeptDO.class, Criteria.where("status").is(DeptStatus.ACTIVE)),
                        count(DeptDO.class, Criteria.where("status").is(DeptStatus.DEPRECATED))),
                null,
                null
        ));
    }

    private List<ChartDataSeries> logActivity() {
        return trend("接口调用", ApiLogDO.class);
    }

    private List<ChartDataSeries> logResult() {
        return List.of(new ChartDataSeries(
                "日志数",
                List.of("接口成功", "接口异常", "登录成功", "登录失败"),
                List.of(count(ApiLogDO.class, Criteria.where("success").is(true)),
                        count(ApiLogDO.class, Criteria.where("success").is(false)),
                        count(LoginLogDO.class, Criteria.where("success").is(true)),
                        count(LoginLogDO.class, Criteria.where("success").is(false))),
                null,
                null
        ));
    }

    private List<ChartDataSeries> trend(String name, Class<?> dataType) {
        LocalDate today = LocalDate.now();
        List<LocalDate> days = IntStream.rangeClosed(1 - TREND_DAYS, 0)
                .mapToObj(today::plusDays)
                .toList();
        List<String> categories = days.stream()
                .map(day -> day.format(DAY_FORMATTER))
                .toList();
        List<Number> values = days.stream()
                .map(day -> count(dataType, Criteria.where("createTime")
                        .gte(day.atStartOfDay())
                        .lt(day.plusDays(1).atStartOfDay())))
                .map(Number.class::cast)
                .toList();
        return List.of(new ChartDataSeries(name, categories, values, null, null));
    }

    private Number count(Class<?> dataType, Criteria criteria) {
        return mongoTemplate.count(Query.query(criteria), dataType);
    }
}
