package online.yudream.base.application.system.dashboard.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.dashboard.assembler.DashboardAssembler;
import online.yudream.base.application.system.dashboard.cmd.DashboardLayoutSaveCmd;
import online.yudream.base.application.system.dashboard.dto.DashboardCardDTO;
import online.yudream.base.application.system.dashboard.dto.DashboardLayoutDTO;
import online.yudream.base.application.system.dashboard.dto.DashboardWorkspaceDTO;
import online.yudream.base.application.system.user.service.PermissionAppService;
import online.yudream.base.domain.system.dashboard.aggregate.DashboardLayout;
import online.yudream.base.domain.system.dashboard.enumerate.DashboardLayoutOwnerType;
import online.yudream.base.domain.system.dashboard.repo.DashboardLayoutRepo;
import online.yudream.base.domain.system.dashboard.valobj.DashboardCardDefinition;
import online.yudream.base.domain.system.dashboard.valobj.DashboardGridPlacement;
import online.yudream.base.domain.system.dashboard.valobj.DashboardLayoutItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardAppService {

    private static final List<String> BREAKPOINTS = List.of("lg", "md", "sm", "xs");
    private static final Map<String, Integer> BREAKPOINT_COLUMNS = Map.of(
            "lg", 12,
            "md", 8,
            "sm", 4,
            "xs", 1
    );

    private final DashboardLayoutRepo dashboardLayoutRepo;
    private final DashboardCardRegistry dashboardCardRegistry;
    private final PermissionAppService permissionAppService;

    @Transactional(readOnly = true)
    public DashboardWorkspaceDTO workspace(Long userId) {
        List<String> permissions = permissionAppService.getUserPermissions(userId);
        List<DashboardCardDefinition> cards = visibleCards(permissions);
        DashboardLayout defaultLayout = defaultLayout(cards);
        DashboardLayout userLayout = dashboardLayoutRepo.findByOwner(DashboardLayoutOwnerType.USER, userId).orElse(null);
        DashboardLayout overrideLayout = isLegacyAutoStackedLayout(userLayout, cards) ? null : userLayout;
        DashboardLayout effectiveLayout = mergeLayout(userId, defaultLayout, overrideLayout, cards, DashboardLayoutOwnerType.USER);
        return DashboardWorkspaceDTO.builder()
                .cards(cards.stream().map(DashboardAssembler::toDTO).toList())
                .defaultLayout(DashboardAssembler.toDTO(defaultLayout))
                .userLayout(DashboardAssembler.toDTO(userLayout))
                .effectiveLayout(DashboardAssembler.toDTO(effectiveLayout))
                .build();
    }

    @Transactional(readOnly = true)
    public DashboardLayoutDTO defaultLayout() {
        List<DashboardCardDefinition> cards = dashboardCardRegistry.allCards();
        return DashboardAssembler.toDTO(defaultLayout(cards));
    }

    @Transactional(readOnly = true)
    public List<DashboardCardDTO> cards() {
        return dashboardCardRegistry.allCards().stream().map(DashboardAssembler::toDTO).toList();
    }

    @Transactional
    public DashboardLayoutDTO saveDefaultLayout(DashboardLayoutSaveCmd cmd) {
        List<DashboardCardDefinition> cards = dashboardCardRegistry.allCards();
        DashboardLayout layout = dashboardLayoutRepo.findByOwner(DashboardLayoutOwnerType.DEFAULT, null)
                .orElseGet(() -> DashboardLayout.create(DashboardLayoutOwnerType.DEFAULT, null, List.of()));
        layout.replaceItems(normalizeSubmittedItems(cmd.getItems(), cards));
        return DashboardAssembler.toDTO(dashboardLayoutRepo.save(layout));
    }

    @Transactional
    public DashboardLayoutDTO saveUserLayout(Long userId, DashboardLayoutSaveCmd cmd) {
        List<String> permissions = permissionAppService.getUserPermissions(userId);
        List<DashboardCardDefinition> cards = visibleCards(permissions);
        DashboardLayout layout = dashboardLayoutRepo.findByOwner(DashboardLayoutOwnerType.USER, userId)
                .orElseGet(() -> DashboardLayout.create(DashboardLayoutOwnerType.USER, userId, List.of()));
        layout.replaceItems(normalizeSubmittedItems(cmd.getItems(), cards));
        return DashboardAssembler.toDTO(dashboardLayoutRepo.save(layout));
    }

    @Transactional
    public void resetUserLayout(Long userId) {
        dashboardLayoutRepo.deleteByOwner(DashboardLayoutOwnerType.USER, userId);
    }

    private List<DashboardCardDefinition> visibleCards(List<String> permissions) {
        return dashboardCardRegistry.allCards().stream()
                .filter(card -> canView(card, permissions))
                .toList();
    }

    private boolean canView(DashboardCardDefinition card, List<String> permissions) {
        return !StringUtils.hasText(card.permission())
                || permissions.contains("*")
                || permissions.contains(card.permission());
    }

    private DashboardLayout defaultLayout(List<DashboardCardDefinition> cards) {
        DashboardLayout stored = dashboardLayoutRepo.findByOwner(DashboardLayoutOwnerType.DEFAULT, null).orElse(null);
        DashboardLayout override = isLegacyAutoStackedLayout(stored, cards) ? null : stored;
        if (override == null) {
            return DashboardLayout.create(DashboardLayoutOwnerType.DEFAULT, null, normalizeSubmittedItems(defaultItems(cards), cards));
        }
        return DashboardLayout.create(DashboardLayoutOwnerType.DEFAULT, null, normalizeSubmittedItems(override.getItems(), cards));
    }

    private DashboardLayout mergeLayout(Long ownerId, DashboardLayout defaultLayout, DashboardLayout overrideLayout,
                                        List<DashboardCardDefinition> cards, DashboardLayoutOwnerType ownerType) {
        Map<String, DashboardCardDefinition> cardMap = cards.stream()
                .collect(Collectors.toMap(DashboardCardDefinition::code, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<String, DashboardLayoutItem> defaults = defaultLayout.getItems().stream()
                .filter(item -> cardMap.containsKey(item.cardCode()))
                .collect(Collectors.toMap(DashboardLayoutItem::cardCode, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        if (overrideLayout != null) {
            overrideLayout.getItems().stream()
                    .filter(item -> cardMap.containsKey(item.cardCode()))
                    .forEach(item -> defaults.put(item.cardCode(), item));
        }
        List<DashboardLayoutItem> merged = new ArrayList<>(defaults.values());
        return DashboardLayout.create(ownerType, ownerType == DashboardLayoutOwnerType.USER ? ownerId : null, normalizeSubmittedItems(merged, cards));
    }

    private List<DashboardLayoutItem> normalizeSubmittedItems(List<DashboardLayoutItem> items, List<DashboardCardDefinition> cards) {
        Map<String, DashboardCardDefinition> cardMap = cards.stream()
                .collect(Collectors.toMap(DashboardCardDefinition::code, Function.identity(), (left, right) -> left));
        List<DashboardLayoutItem> source = items == null ? List.of() : items;
        Set<String> seen = new HashSet<>();
        List<DashboardLayoutItem> normalized = source.stream()
                .filter(item -> cardMap.containsKey(item.cardCode()))
                .filter(item -> seen.add(item.cardCode()))
                .map(item -> normalizeItem(item, cardMap.get(item.cardCode())))
                .sorted(Comparator.comparingInt(item -> item.placements().getOrDefault("lg", new DashboardGridPlacement(0, 9999, 1, 1)).y()))
                .collect(Collectors.toCollection(ArrayList::new));
        return normalized;
    }

    private DashboardLayoutItem normalizeItem(DashboardLayoutItem item, DashboardCardDefinition card) {
        Map<String, DashboardGridPlacement> placements = new HashMap<>();
        for (String breakpoint : BREAKPOINTS) {
            DashboardGridPlacement placement = item.placements().get(breakpoint);
            if (placement == null) {
                placement = defaultPlacement(card, breakpoint, 0);
            }
            placements.put(breakpoint, clampPlacement(placement, card, breakpoint));
        }
        return new DashboardLayoutItem(item.cardCode(), item.visible(), placements);
    }

    private List<DashboardLayoutItem> defaultItems(List<DashboardCardDefinition> cards) {
        List<DashboardLayoutItem> items = new ArrayList<>();
        Map<String, int[]> columnHeights = new HashMap<>();
        for (String breakpoint : BREAKPOINTS) {
            columnHeights.put(breakpoint, new int[BREAKPOINT_COLUMNS.getOrDefault(breakpoint, 12)]);
        }
        for (DashboardCardDefinition card : cards) {
            if (!"SYSTEM".equals(card.source())) {
                continue;
            }
            Map<String, DashboardGridPlacement> placements = new HashMap<>();
            for (String breakpoint : BREAKPOINTS) {
                DashboardGridPlacement placement = nextDefaultPlacement(card, breakpoint, columnHeights.get(breakpoint));
                placements.put(breakpoint, placement);
            }
            items.add(new DashboardLayoutItem(card.code(), true, placements));
        }
        return items;
    }

    private DashboardGridPlacement defaultPlacement(DashboardCardDefinition card, String breakpoint, int y) {
        int columns = BREAKPOINT_COLUMNS.getOrDefault(breakpoint, 12);
        int width = "xs".equals(breakpoint) ? 1 : Math.min(card.defaultW(), columns);
        return new DashboardGridPlacement(0, y, width, card.defaultH());
    }

    private boolean isLegacyAutoStackedLayout(DashboardLayout stored, List<DashboardCardDefinition> cards) {
        if (stored == null || stored.getItems().size() < 3) {
            return false;
        }
        if (stored.getItems().stream().anyMatch(item -> !item.visible())) {
            return false;
        }
        Map<String, DashboardCardDefinition> cardMap = cards.stream()
                .collect(Collectors.toMap(DashboardCardDefinition::code, Function.identity(), (left, right) -> left));
        return isExactLegacyStackedLayout(stored, cardMap) || isSingleColumnStackedLayout(stored);
    }

    private boolean isExactLegacyStackedLayout(DashboardLayout stored, Map<String, DashboardCardDefinition> cardMap) {
        int expectedY = 0;
        for (DashboardLayoutItem item : stored.getItems()) {
            DashboardCardDefinition card = cardMap.get(item.cardCode());
            DashboardGridPlacement placement = item.placements().get("lg");
            if (card == null || !item.visible() || placement == null) {
                return false;
            }
            int width = Math.min(card.defaultW(), BREAKPOINT_COLUMNS.get("lg"));
            int height = Math.max(card.defaultH(), card.minH());
            if (placement.x() != 0 || placement.y() != expectedY || placement.w() != width || placement.h() != height) {
                return false;
            }
            expectedY += height;
        }
        return true;
    }

    private boolean isSingleColumnStackedLayout(DashboardLayout stored) {
        for (String breakpoint : List.of("lg", "md", "sm")) {
            List<DashboardGridPlacement> placements = stored.getItems().stream()
                    .filter(DashboardLayoutItem::visible)
                    .map(item -> item.placements().get(breakpoint))
                    .toList();
            if (placements.size() < 3 || placements.stream().anyMatch(placement -> placement == null || placement.x() != 0)) {
                continue;
            }
            List<DashboardGridPlacement> sorted = placements.stream()
                    .sorted(Comparator.comparingInt(DashboardGridPlacement::y))
                    .toList();
            int previousBottom = 0;
            boolean stacked = true;
            boolean hasVerticalOffset = false;
            for (DashboardGridPlacement placement : sorted) {
                if (placement.y() < previousBottom) {
                    stacked = false;
                    break;
                }
                hasVerticalOffset = hasVerticalOffset || placement.y() > 0;
                previousBottom = Math.max(previousBottom, placement.y() + placement.h());
            }
            if (stacked && hasVerticalOffset) {
                return true;
            }
        }
        return false;
    }

    private DashboardGridPlacement nextDefaultPlacement(DashboardCardDefinition card, String breakpoint, int[] heights) {
        int columns = BREAKPOINT_COLUMNS.getOrDefault(breakpoint, 12);
        int width = "xs".equals(breakpoint) ? 1 : Math.min(card.defaultW(), columns);
        int height = Math.max(card.defaultH(), card.minH());
        int bestX = 0;
        int bestY = Integer.MAX_VALUE;
        for (int x = 0; x <= columns - width; x++) {
            int y = 0;
            for (int col = x; col < x + width; col++) {
                y = Math.max(y, heights[col]);
            }
            if (y < bestY) {
                bestX = x;
                bestY = y;
            }
        }
        for (int col = bestX; col < bestX + width; col++) {
            heights[col] = bestY + height;
        }
        return new DashboardGridPlacement(bestX, bestY, width, height);
    }

    private DashboardGridPlacement clampPlacement(DashboardGridPlacement placement, DashboardCardDefinition card, String breakpoint) {
        int columns = BREAKPOINT_COLUMNS.getOrDefault(breakpoint, 12);
        int minW = "xs".equals(breakpoint) ? 1 : Math.min(card.minW(), columns);
        int width = "xs".equals(breakpoint) ? 1 : Math.min(Math.max(placement.w(), minW), columns);
        int x = Math.min(Math.max(placement.x(), 0), Math.max(columns - width, 0));
        int height = Math.max(placement.h(), card.minH());
        return new DashboardGridPlacement(x, Math.max(placement.y(), 0), width, height);
    }
}
