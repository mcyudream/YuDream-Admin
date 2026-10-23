package online.yudream.base.application.system.dashboard.service;

import online.yudream.base.application.system.dashboard.dto.DashboardLayoutDTO;
import online.yudream.base.application.system.dashboard.dto.DashboardWorkspaceDTO;
import online.yudream.base.application.system.user.service.PermissionAppService;
import online.yudream.base.domain.system.dashboard.aggregate.DashboardLayout;
import online.yudream.base.domain.system.dashboard.enumerate.DashboardLayoutOwnerType;
import online.yudream.base.domain.system.dashboard.repo.DashboardLayoutRepo;
import online.yudream.base.domain.system.dashboard.valobj.DashboardCardDefinition;
import online.yudream.base.domain.system.dashboard.valobj.DashboardGridPlacement;
import online.yudream.base.domain.system.dashboard.valobj.DashboardLayoutItem;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardAppServiceTest {

    private static final long USER_ID = 356058647538831360L;

    @Test
    void defaultLayoutAppendsMessagingBindingAndKeepsSavedPositions() {
        List<DashboardCardDefinition> cards = List.of(
                card("system.profile", 4, 3, 1, 1, 10),
                messagingBindingCard());
        DashboardLayout storedDefault = DashboardLayout.create(DashboardLayoutOwnerType.DEFAULT, null, List.of(
                item("system.profile", true, placement(2, 4, 6, 7), placement(0, 0, 4, 3), placement(0, 0, 4, 3))));
        storedDefault.setId(918273645L);
        DashboardAppService service = service(cards, storedDefault, null);

        DashboardLayoutDTO defaultLayout = service.defaultLayout();

        assertEquals(918273645L, defaultLayout.getId());
        // 默认布局自动补入新增卡
        assertTrue(defaultLayout.getItems().stream()
                .anyMatch(item -> "system.messaging-binding".equals(item.cardCode()) && item.visible()));
        // 已保存位置不被覆盖
        DashboardLayoutItem profileItem = defaultLayout.getItems().stream()
                .filter(item -> "system.profile".equals(item.cardCode()))
                .findFirst()
                .orElseThrow();
        assertEquals(placement(2, 4, 6, 7), profileItem.placements().get("lg"));
    }

    @Test
    void hiddenMessagingBindingCardIsNotRevivedInDefaultLayout() {
        List<DashboardCardDefinition> cards = List.of(
                card("system.profile", 4, 3, 1, 1, 10),
                messagingBindingCard());
        DashboardLayout storedDefault = DashboardLayout.create(DashboardLayoutOwnerType.DEFAULT, null, List.of(
                item("system.profile", true, placement(0, 0, 4, 3), placement(0, 0, 4, 3), placement(0, 0, 4, 3)),
                item("system.messaging-binding", false, placement(0, 3, 4, 4), placement(0, 3, 4, 4), placement(0, 3, 4, 4))));
        DashboardAppService service = service(cards, storedDefault, null);

        DashboardLayoutDTO defaultLayout = service.defaultLayout();

        assertEquals(2, defaultLayout.getItems().size());
        assertTrue(defaultLayout.getItems().stream()
                .filter(item -> "system.messaging-binding".equals(item.cardCode()))
                .allMatch(item -> !item.visible()));
    }

    @Test
    void workspaceShowsMessagingBindingWithoutAdminPermissionAndKeepsUserLayout() {
        List<DashboardCardDefinition> cards = List.of(
                card("system.profile", 4, 3, 1, 1, 10),
                messagingBindingCard());
        DashboardLayout userLayout = DashboardLayout.create(DashboardLayoutOwnerType.USER, USER_ID, List.of(
                item("system.profile", true, placement(1, 2, 5, 6), placement(0, 0, 4, 3), placement(0, 0, 4, 3)),
                item("system.messaging-binding", false, placement(0, 8, 4, 4), placement(0, 3, 4, 4), placement(0, 3, 4, 4))));
        userLayout.setId(24680L);
        DashboardAppService service = service(cards, null, userLayout);

        DashboardWorkspaceDTO workspace = service.workspace(USER_ID);

        // 无任何权限码的登录用户也能看到该卡（permission 为空）
        assertTrue(workspace.getCards().stream()
                .anyMatch(card -> "system.messaging-binding".equals(card.getCode())));
        // 用户显式隐藏的卡保持隐藏，已保存位置原样保留
        DashboardLayoutItem bindingItem = workspace.getEffectiveLayout().getItems().stream()
                .filter(item -> "system.messaging-binding".equals(item.cardCode()))
                .findFirst()
                .orElseThrow();
        assertFalse(bindingItem.visible());
        DashboardLayoutItem profileItem = workspace.getEffectiveLayout().getItems().stream()
                .filter(item -> "system.profile".equals(item.cardCode()))
                .findFirst()
                .orElseThrow();
        assertEquals(placement(1, 2, 5, 6), profileItem.placements().get("lg"));
        assertEquals(24680L, workspace.getEffectiveLayout().getId());
    }

    private static DashboardAppService service(List<DashboardCardDefinition> cards, DashboardLayout defaultLayout,
                                               DashboardLayout userLayout) {
        return new DashboardAppService(new InMemoryLayouts(defaultLayout, userLayout),
                new StubRegistry(cards), new FixedPermissions());
    }

    private static DashboardCardDefinition card(String code, int defaultW, int defaultH, int minW, int minH, int sort) {
        return new DashboardCardDefinition(code, code, "desc", "i-ri:layout-grid-line", "系统", "SYSTEM",
                null, null, "ACTION_CARD", null, null, "blue", defaultW, defaultH, minW, minH, sort, false);
    }

    private static DashboardCardDefinition messagingBindingCard() {
        return new DashboardCardDefinition("system.messaging-binding", "消息协议绑定", "desc", "morph:bot", "个人",
                "SYSTEM", null, null, "MESSAGING_BINDING", null, null, "gray", 4, 4, 3, 3, 25, false);
    }

    private static DashboardLayoutItem item(String cardCode, boolean visible, DashboardGridPlacement lg,
                                            DashboardGridPlacement md, DashboardGridPlacement sm) {
        Map<String, DashboardGridPlacement> placements = new HashMap<>();
        placements.put("lg", lg);
        placements.put("md", md);
        placements.put("sm", sm);
        placements.put("xs", new DashboardGridPlacement(0, sm.y(), 1, sm.h()));
        return new DashboardLayoutItem(cardCode, visible, placements);
    }

    private static DashboardGridPlacement placement(int x, int y, int w, int h) {
        return new DashboardGridPlacement(x, y, w, h);
    }

    private static final class StubRegistry extends DashboardCardRegistry {

        private final List<DashboardCardDefinition> cards;

        private StubRegistry(List<DashboardCardDefinition> cards) {
            super(null, null, List.of());
            this.cards = cards;
        }

        @Override
        public List<DashboardCardDefinition> allCards() {
            return cards;
        }
    }

    private static final class InMemoryLayouts implements DashboardLayoutRepo {

        private DashboardLayout defaultLayout;
        private DashboardLayout userLayout;

        private InMemoryLayouts(DashboardLayout defaultLayout, DashboardLayout userLayout) {
            this.defaultLayout = defaultLayout;
            this.userLayout = userLayout;
        }

        @Override
        public DashboardLayout save(DashboardLayout layout) {
            if (layout.getOwnerType() == DashboardLayoutOwnerType.DEFAULT) {
                defaultLayout = layout;
            } else {
                userLayout = layout;
            }
            return layout;
        }

        @Override
        public Optional<DashboardLayout> findByOwner(DashboardLayoutOwnerType ownerType, Long ownerId) {
            return Optional.ofNullable(ownerType == DashboardLayoutOwnerType.DEFAULT ? defaultLayout : userLayout);
        }

        @Override
        public void deleteByOwner(DashboardLayoutOwnerType ownerType, Long ownerId) {
            if (ownerType == DashboardLayoutOwnerType.DEFAULT) {
                defaultLayout = null;
            } else {
                userLayout = null;
            }
        }
    }

    private static final class FixedPermissions extends PermissionAppService {

        private FixedPermissions() {
            super(null, null, null);
        }

        @Override
        public List<String> getUserPermissions(Long userId) {
            return List.of();
        }
    }
}
