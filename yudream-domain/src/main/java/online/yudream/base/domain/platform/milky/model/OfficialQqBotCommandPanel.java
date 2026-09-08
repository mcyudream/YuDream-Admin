package online.yudream.base.domain.platform.milky.model;

import online.yudream.base.domain.platform.plugin.valobj.PluginCommandInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把系统/插件指令编成官方指令面板。面板覆盖单聊、群聊、文字子频道、频道私信；
 * 每面板最多 20 项，名称最多 14 个字符单位（汉字算 2），描述最多 30。
 * command 类型点击后把 name 填入输入框，因此 name 必须是可分发的命令字面量。
 */
public final class OfficialQqBotCommandPanel {
    public static final int MAX_ITEMS = 20;
    public static final int MAX_NAME_UNITS = 14;
    public static final int MAX_DESC_UNITS = 30;
    public static final String REMARK = "yudream-system-commands";
    public static final List<String> SCOPES = List.of("c2c", "group", "channel", "dm");

    private OfficialQqBotCommandPanel() {
    }

    public static Map<String, Object> createPayload(String scope, List<PluginCommandInfo> commands) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scope", scope);
        body.put("target_type", "all");
        body.put("panel", panel(commands));
        return body;
    }

    public static Map<String, Object> updatePayload(List<PluginCommandInfo> commands) {
        return Map.of("panel", panel(commands));
    }

    public static Map<String, Object> panel(List<PluginCommandInfo> commands) {
        Map<String, Object> panel = new LinkedHashMap<>();
        panel.put("items", items(commands));
        panel.put("remark", REMARK);
        return panel;
    }

    public static List<Map<String, Object>> items(List<PluginCommandInfo> commands) {
        List<OfficialQqBotCommandMenu.MenuEntry> entries = OfficialQqBotCommandMenu.uniqueEntries(commands);
        if (entries.isEmpty()) {
            return List.of(commandItem("菜单", "查看可用指令"));
        }
        int end = Math.min(entries.size(), MAX_ITEMS);
        List<Map<String, Object>> items = new ArrayList<>(end);
        for (int index = 0; index < end; index++) {
            OfficialQqBotCommandMenu.MenuEntry entry = entries.get(index);
            items.add(commandItem(entry.trigger(), entry.name()));
        }
        return List.copyOf(items);
    }

    public static String panelId(Object response) {
        Object panelId = asMap(response).get("panel_id");
        if (panelId == null || String.valueOf(panelId).isBlank()) {
            return null;
        }
        return String.valueOf(panelId);
    }

    public static boolean managed(Object remark) {
        return REMARK.equals(remark == null ? null : String.valueOf(remark));
    }

    public static String findManagedPanelId(Object listResponse) {
        Snapshot snapshot = findManagedSnapshot(listResponse);
        return snapshot == null ? null : snapshot.panelId();
    }

    public static Snapshot findManagedSnapshot(Object listResponse) {
        for (Map<String, Object> record : records(listResponse)) {
            Object panel = record.get("panel");
            Object remark = panel instanceof Map<?, ?> map ? map.get("remark") : record.get("remark");
            if (managed(remark)) {
                Object panelId = record.get("panel_id");
                if (panelId != null && !String.valueOf(panelId).isBlank()) {
                    return new Snapshot(String.valueOf(panelId), panel instanceof Map<?, ?> ? asMap(panel) : Map.of());
                }
            }
        }
        return null;
    }

    /** 远端托管面板与期望面板是否语义相同：只比 remark + items。 */
    public static boolean samePanel(Object remotePanel, List<PluginCommandInfo> commands) {
        Map<String, Object> expected = panel(commands);
        Map<String, Object> actual = asMap(remotePanel);
        if (!REMARK.equals(String.valueOf(actual.get("remark")))) {
            return false;
        }
        return OfficialQqBotCommandMenu.canonicalItems(actual.get("items"))
                .equals(OfficialQqBotCommandMenu.canonicalItems(expected.get("items")));
    }

    public record Snapshot(String panelId, Map<String, Object> panel) {
        public Snapshot {
            panel = panel == null ? Map.of() : Map.copyOf(panel);
        }
    }

    public static boolean listEnded(Object listResponse) {
        Map<String, Object> body = asMap(listResponse);
        Object ended = body.get("is_end");
        if (Boolean.TRUE.equals(ended) || "true".equalsIgnoreCase(String.valueOf(ended))) {
            return true;
        }
        String cursor = nextCursor(listResponse);
        return cursor == null || cursor.isBlank();
    }

    public static String nextCursor(Object listResponse) {
        Object cursor = asMap(listResponse).get("next_cursor");
        return cursor == null ? null : String.valueOf(cursor);
    }

    private static List<Map<String, Object>> records(Object listResponse) {
        Object records = asMap(listResponse).get("records");
        if (!(records instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> row = asMap(item);
            if (!row.isEmpty()) {
                rows.add(row);
            }
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> raw) {
            return (Map<String, Object>) raw;
        }
        return Map.of();
    }

    private static Map<String, Object> commandItem(String trigger, String description) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "command");
        item.put("name", OfficialQqBotCommandMenu.clip(trigger, MAX_NAME_UNITS));
        item.put("desc", OfficialQqBotCommandMenu.clip(firstNonBlank(description, trigger), MAX_DESC_UNITS));
        item.put("only_admin", false);
        return item;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
