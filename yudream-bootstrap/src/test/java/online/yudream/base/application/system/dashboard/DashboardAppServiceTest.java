package online.yudream.base.application.system.dashboard;

import online.yudream.base.application.system.dashboard.dto.DashboardWorkspaceDTO;
import online.yudream.base.application.system.dashboard.service.DashboardAppService;
import online.yudream.base.application.system.dashboard.service.DashboardCardRegistry;
import online.yudream.base.application.system.user.service.PermissionAppService;
import online.yudream.base.domain.system.dashboard.aggregate.DashboardLayout;
import online.yudream.base.domain.system.dashboard.enumerate.DashboardLayoutOwnerType;
import online.yudream.base.domain.system.dashboard.repo.DashboardLayoutRepo;
import online.yudream.base.domain.system.dashboard.valobj.DashboardCardDefinition;
import online.yudream.base.domain.system.dashboard.valobj.DashboardGridPlacement;
import online.yudream.base.domain.system.dashboard.valobj.DashboardLayoutItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardAppServiceTest {

    private static final long USER_ID = 356058647538831360L;

    @Mock
    private DashboardLayoutRepo dashboardLayoutRepo;
    @Mock
    private DashboardCardRegistry dashboardCardRegistry;
    @Mock
    private PermissionAppService permissionAppService;

    private DashboardAppService service;

    @BeforeEach
    void setUp() {
        service = new DashboardAppService(dashboardLayoutRepo, dashboardCardRegistry, permissionAppService);
    }

    @Test
    void workspaceKeepsUserAddedCardWhenOnlyNarrowBreakpointsAreStacked() {
        DashboardCardDefinition profile = systemCard("system.profile", 4, 3, 10);
        DashboardCardDefinition quickActions = systemCard("system.quick-actions", 8, 3, 20);
        DashboardCardDefinition endpoints = pluginCard("yggc.yggc-endpoints", "yggc", 4, 2, 36);
        List<DashboardCardDefinition> cards = List.of(profile, quickActions, endpoints);
        when(permissionAppService.getUserPermissions(USER_ID)).thenReturn(List.of());
        when(dashboardCardRegistry.allCards()).thenReturn(cards);
        when(dashboardLayoutRepo.findByOwner(DashboardLayoutOwnerType.DEFAULT, null)).thenReturn(Optional.empty());
        when(dashboardLayoutRepo.findByOwner(DashboardLayoutOwnerType.USER, USER_ID)).thenReturn(Optional.of(
                DashboardLayout.create(DashboardLayoutOwnerType.USER, USER_ID, List.of(
                        item("system.profile", true, placement(0, 0, 4, 3), placement(0, 0, 4, 3), placement(0, 0, 4, 3)),
                        item("system.quick-actions", true, placement(4, 0, 8, 3), placement(0, 3, 8, 3), placement(0, 3, 4, 3)),
                        item("yggc.yggc-endpoints", true, placement(0, 3, 4, 2), placement(0, 6, 4, 2), placement(0, 6, 4, 2))
                ))));

        DashboardWorkspaceDTO workspace = service.workspace(USER_ID);

        assertThat(workspace.getUserLayout().getItems()).extracting(DashboardLayoutItem::cardCode)
                .containsExactly("system.profile", "system.quick-actions", "yggc.yggc-endpoints");
        assertThat(workspace.getEffectiveLayout().getItems()).extracting(DashboardLayoutItem::cardCode)
                .contains("yggc.yggc-endpoints");
        assertThat(workspace.getEffectiveLayout().getItems()).anySatisfy(item -> {
            assertThat(item.cardCode()).isEqualTo("yggc.yggc-endpoints");
            assertThat(item.visible()).isTrue();
            assertThat(item.placements().get("lg")).isEqualTo(placement(0, 3, 4, 2));
        });
    }

    @Test
    void workspaceIgnoresLegacySingleColumnUserLayoutOnLargeBreakpoint() {
        DashboardCardDefinition profile = systemCard("system.profile", 4, 3, 10);
        DashboardCardDefinition quickActions = systemCard("system.quick-actions", 8, 3, 20);
        DashboardCardDefinition monitor = systemCard("system.monitor", 4, 3, 30);
        List<DashboardCardDefinition> cards = List.of(profile, quickActions, monitor);
        when(permissionAppService.getUserPermissions(USER_ID)).thenReturn(List.of());
        when(dashboardCardRegistry.allCards()).thenReturn(cards);
        when(dashboardLayoutRepo.findByOwner(DashboardLayoutOwnerType.DEFAULT, null)).thenReturn(Optional.empty());
        when(dashboardLayoutRepo.findByOwner(DashboardLayoutOwnerType.USER, USER_ID)).thenReturn(Optional.of(
                DashboardLayout.create(DashboardLayoutOwnerType.USER, USER_ID, List.of(
                        item("system.profile", true, placement(0, 0, 4, 3), placement(0, 0, 4, 3), placement(0, 0, 4, 3)),
                        item("system.quick-actions", true, placement(0, 3, 8, 3), placement(0, 3, 8, 3), placement(0, 3, 4, 3)),
                        item("system.monitor", true, placement(0, 6, 4, 3), placement(0, 6, 4, 3), placement(0, 6, 4, 3))
                ))));

        DashboardWorkspaceDTO workspace = service.workspace(USER_ID);

        assertThat(workspace.getEffectiveLayout().getItems().get(1).placements().get("lg").x()).isNotZero();
    }

    private DashboardCardDefinition systemCard(String code, int defaultW, int defaultH, int sort) {
        return new DashboardCardDefinition(
                code, code, "desc", "i-ri:layout-grid-line", "系统", "SYSTEM", null, null,
                "ACTION_CARD", null, null, "blue", defaultW, defaultH, 1, 1, sort, false);
    }

    private DashboardCardDefinition pluginCard(String code, String pluginCode, int defaultW, int defaultH, int sort) {
        return new DashboardCardDefinition(
                code, code, "desc", "i-ri:layout-grid-line", "插件", "PLUGIN", pluginCode, null,
                pluginCode + "/Card", null, null, "violet", defaultW, defaultH, 1, 1, sort, false);
    }

    private DashboardLayoutItem item(String cardCode, boolean visible, DashboardGridPlacement lg,
                                     DashboardGridPlacement md, DashboardGridPlacement sm) {
        Map<String, DashboardGridPlacement> placements = new HashMap<>();
        placements.put("lg", lg);
        placements.put("md", md);
        placements.put("sm", sm);
        placements.put("xs", new DashboardGridPlacement(0, sm.y(), 1, sm.h()));
        return new DashboardLayoutItem(cardCode, visible, placements);
    }

    private DashboardGridPlacement placement(int x, int y, int w, int h) {
        return new DashboardGridPlacement(x, y, w, h);
    }
}
