package online.yudream.base.application.platform.plugin;

import online.yudream.base.application.platform.plugin.dto.PluginMarketplaceUpdatePlanDTO;
import online.yudream.base.application.platform.plugin.dto.PluginModuleDTO;
import online.yudream.base.application.platform.plugin.dto.PluginModuleSummaryDTO;
import online.yudream.base.application.platform.plugin.query.PluginModulePageQuery;
import online.yudream.base.application.platform.plugin.service.PluginAppService;
import online.yudream.base.application.platform.plugin.service.PluginDirectoryAppService;
import online.yudream.base.application.platform.plugin.service.PluginStoreAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.plugin.enumerate.PluginStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 插件目录服务端分页：筛选、排序、分页与状态概览都在服务端完成，
 * 前端只提交查询条件，行数据不再全量下发。
 */
class PluginDirectoryAppServiceTest {

    @Test
    void pagesFilteredRecordsAndKeepsTotal() {
        PluginDirectoryAppService service = service(List.of(
                module("alpha", "Alpha", PluginStatus.ENABLED),
                module("beta", "Beta", PluginStatus.LOADED),
                module("gamma", "Gamma", PluginStatus.ERROR)
        ), List.of());

        PluginModulePageQuery query = new PluginModulePageQuery();
        query.setSize(2);
        query.setPage(1);
        PageResult<PluginModuleDTO> first = service.page(query);

        assertEquals(3, first.getTotal());
        assertEquals(2, first.getRecords().size());
        assertEquals(1, first.getPage());
        assertEquals(2, first.getSize());
        assertEquals(List.of("alpha", "beta"), first.getRecords().stream().map(PluginModuleDTO::getCode).toList());

        query.setPage(2);
        PageResult<PluginModuleDTO> second = service.page(query);
        assertEquals(3, second.getTotal());
        assertEquals(List.of("gamma"), second.getRecords().stream().map(PluginModuleDTO::getCode).toList());
    }

    @Test
    void pageBeyondRangeReturnsEmptyRecordsWithTotal() {
        PluginDirectoryAppService service = service(List.of(module("alpha", "Alpha", PluginStatus.ENABLED)), List.of());

        PluginModulePageQuery query = new PluginModulePageQuery();
        query.setPage(9);
        query.setSize(5);
        PageResult<PluginModuleDTO> page = service.page(query);

        assertEquals(9, page.getPage());
        assertEquals(1, page.getTotal());
        assertTrue(page.getRecords().isEmpty());
    }

    @Test
    void filtersByKeywordAcrossNameCodeVersionAndDescription() {
        PluginDirectoryAppService service = service(List.of(
                module("alpha", "Alpha 面板", PluginStatus.ENABLED),
                module("yudream-wallet", "钱包", PluginStatus.LOADED, "站点积分钱包"),
                module("mc-wiki", "MC 百科", PluginStatus.ENABLED)
        ), List.of());

        assertEquals(List.of("yudream-wallet"), codes(service, query(null, "WALLET", null, 10)));
        assertEquals(List.of("alpha"), codes(service, query(null, "面板", null, 10)));
        assertEquals(List.of("yudream-wallet"), codes(service, query(null, "积分", null, 10)));
    }

    @Test
    void filtersByStatusAndTreatsUnknownStatusAsAll() {
        PluginDirectoryAppService service = service(List.of(
                module("alpha", "Alpha", PluginStatus.ENABLED),
                module("beta", "Beta", PluginStatus.ERROR),
                module("gamma", "Gamma", PluginStatus.ERROR)
        ), List.of());

        assertEquals(List.of("beta", "gamma"), codes(service, query("ERROR", null, null, 10)));
        assertEquals(List.of("alpha", "beta", "gamma"), codes(service, query("NOT_A_STATUS", null, null, 10)));
        assertEquals(List.of("alpha", "beta", "gamma"), codes(service, query("all", null, null, 10)));
    }

    @Test
    void filtersByUpdateStateUsingMarketPlans() {
        PluginDirectoryAppService service = service(List.of(
                module("alpha", "Alpha", PluginStatus.ENABLED),
                module("beta", "Beta", PluginStatus.ENABLED),
                module("gamma", "Gamma", PluginStatus.ENABLED)
        ), List.of(
                plan("alpha", null),
                plan("beta", "依赖 minecraft-server 需要 >= 2.4.0")
        ));

        assertEquals(List.of("alpha"), codes(service, query(null, null, "AVAILABLE", 10)));
        assertEquals(List.of("beta"), codes(service, query(null, null, "BLOCKED", 10)));
        assertEquals(List.of("alpha", "beta", "gamma"), codes(service, query(null, null, "ALL", 10)));
    }

    @Test
    void sortsByStatusNameAndRecentLoad() {
        PluginDirectoryAppService service = service(List.of(
                module("alpha", "Alpha", PluginStatus.DISABLED, LocalDateTime.of(2026, 10, 1, 9, 0)),
                module("beta", "Beta", PluginStatus.ERROR, LocalDateTime.of(2026, 10, 3, 9, 0)),
                module("gamma", "Gamma", PluginStatus.ENABLED, LocalDateTime.of(2026, 10, 2, 9, 0))
        ), List.of());

        assertEquals(List.of("beta", "gamma", "alpha"), codes(service, sortedQuery("STATUS")));
        assertEquals(List.of("alpha", "beta", "gamma"), codes(service, sortedQuery("NAME")));
        assertEquals(List.of("beta", "gamma", "alpha"), codes(service, sortedQuery("RECENT")));
        assertEquals(List.of("alpha", "beta", "gamma"), codes(service, sortedQuery("UNKNOWN_SORT")));
    }

    @Test
    void clampsPageSizeToConfiguredBounds() {
        PluginDirectoryAppService service = service(List.of(
                module("alpha", "Alpha", PluginStatus.ENABLED),
                module("beta", "Beta", PluginStatus.ENABLED)
        ), List.of());

        PluginModulePageQuery query = new PluginModulePageQuery();
        query.setSize(0);
        query.setPage(-3);
        PageResult<PluginModuleDTO> page = service.page(query);

        assertEquals(1, page.getSize());
        assertEquals(1, page.getPage());
        assertEquals(1, page.getRecords().size());
    }

    @Test
    void summaryCountsEveryStatusIncludingEmptyOnes() {
        PluginDirectoryAppService service = service(List.of(
                module("alpha", "Alpha", PluginStatus.ENABLED),
                module("beta", "Beta", PluginStatus.LOADED),
                module("gamma", "Gamma", PluginStatus.ERROR),
                module("delta", "Delta", PluginStatus.ERROR)
        ), List.of());

        PluginModuleSummaryDTO summary = service.summary();

        assertEquals(4, summary.getTotal());
        assertEquals(1L, summary.getStatusCounts().get("ENABLED"));
        assertEquals(1L, summary.getStatusCounts().get("LOADED"));
        assertEquals(0L, summary.getStatusCounts().get("INSTALLED"));
        assertEquals(0L, summary.getStatusCounts().get("DISABLED"));
        assertEquals(2L, summary.getStatusCounts().get("ERROR"));
    }

    private List<String> codes(PluginDirectoryAppService service, PluginModulePageQuery query) {
        return service.page(query).getRecords().stream().map(PluginModuleDTO::getCode).toList();
    }

    private PluginModulePageQuery query(String status, String keyword, String updateState, int size) {
        PluginModulePageQuery query = new PluginModulePageQuery();
        query.setSize(size);
        query.setStatus(status);
        query.setKeyword(keyword);
        query.setUpdateState(updateState);
        return query;
    }

    private PluginModulePageQuery sortedQuery(String sort) {
        PluginModulePageQuery query = new PluginModulePageQuery();
        query.setSize(10);
        query.setSort(sort);
        return query;
    }

    private PluginDirectoryAppService service(List<PluginModuleDTO> modules, List<PluginMarketplaceUpdatePlanDTO> plans) {
        return new PluginDirectoryAppService(new StubPluginAppService(modules), new StubStoreAppService(plans));
    }

    private static PluginModuleDTO module(String code, String name, PluginStatus status) {
        return module(code, name, status, null, null);
    }

    private static PluginModuleDTO module(String code, String name, PluginStatus status, String description) {
        return module(code, name, status, description, null);
    }

    private static PluginModuleDTO module(String code, String name, PluginStatus status, LocalDateTime loadedAt) {
        return module(code, name, status, null, loadedAt);
    }

    private static PluginModuleDTO module(String code, String name, PluginStatus status, String description, LocalDateTime loadedAt) {
        return PluginModuleDTO.builder()
                .code(code)
                .name(name)
                .version("1.0.0")
                .description(description)
                .status(status)
                .loadedAt(loadedAt)
                .loaded(status == PluginStatus.LOADED || status == PluginStatus.ENABLED)
                .enabled(status == PluginStatus.ENABLED)
                .build();
    }

    private static PluginMarketplaceUpdatePlanDTO plan(String code, String blockedReason) {
        return PluginMarketplaceUpdatePlanDTO.builder()
                .code(code)
                .fromVersion("1.0.0")
                .toVersion("1.1.0")
                .changeType("MINOR")
                .blockedReason(blockedReason)
                .build();
    }

    private static class StubPluginAppService extends PluginAppService {

        private final List<PluginModuleDTO> modules;

        StubPluginAppService(List<PluginModuleDTO> modules) {
            super(null, null, null, null, null, null);
            this.modules = modules;
        }

        @Override
        public List<PluginModuleDTO> list() {
            return modules;
        }
    }

    private static class StubStoreAppService extends PluginStoreAppService {

        private final List<PluginMarketplaceUpdatePlanDTO> plans;

        StubStoreAppService(List<PluginMarketplaceUpdatePlanDTO> plans) {
            super(null, null, null);
            this.plans = plans;
        }

        @Override
        public List<PluginMarketplaceUpdatePlanDTO> updatePlans() {
            return plans;
        }
    }
}
