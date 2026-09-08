package online.yudream.base.domain.platform.milky.model;

import online.yudream.base.domain.platform.plugin.valobj.PluginCommandInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 把系统/插件指令编成官方 C2C 自定义菜单。官方限制：仅单聊底部菜单；
 * 一级最多 10 项，子菜单最多 5 项，一级名称最多 10 个字符单位（汉字算 2），子项名称最多 14。
 * 群聊、文字子频道、频道私信走 {@link OfficialQqBotCommandPanel}。
 */
public final class OfficialQqBotCommandMenu {
    public static final int MAX_TOP_ITEMS = 10;
    public static final int MAX_SUB_ITEMS = 5;
    public static final int MAX_TOP_NAME_UNITS = 10;
    public static final int MAX_SUB_NAME_UNITS = 14;
    public static final String MORE_LABEL = "更多";

    private OfficialQqBotCommandMenu() {
    }

    public static Map<String, Object> payload(List<PluginCommandInfo> commands) {
        return Map.of("menu", Map.of("items", items(commands)));
    }

    /** 远端菜单与期望菜单是否语义相同：只比 items，忽略官方回写的其它字段。 */
    public static boolean sameItems(Object remoteMenu, List<PluginCommandInfo> commands) {
        return canonicalItems(menuItems(remoteMenu)).equals(canonicalItems(items(commands)));
    }

    public static Object menuItems(Object remoteMenu) {
        Map<String, Object> body = asMap(remoteMenu);
        Object menu = body.get("menu");
        if (menu instanceof Map<?, ?>) {
            return asMap(menu).get("items");
        }
        return body.get("items");
    }

    static List<Object> canonicalItems(Object items) {
        if (!(items instanceof List<?> list)) {
            return List.of();
        }
        List<Object> canonical = new ArrayList<>(list.size());
        for (Object item : list) {
            canonical.add(canonicalItem(item));
        }
        return canonical;
    }

    private static Object canonicalItem(Object item) {
        Map<String, Object> source = asMap(item);
        if (source.isEmpty()) {
            return item == null ? "" : String.valueOf(item);
        }
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("type", String.valueOf(source.getOrDefault("type", "")));
        canonical.put("name", String.valueOf(source.getOrDefault("name", "")));
        if (source.containsKey("send_message")) {
            canonical.put("send_message", String.valueOf(source.get("send_message")));
        }
        if (source.containsKey("desc")) {
            canonical.put("desc", String.valueOf(source.get("desc")));
        }
        if (source.containsKey("only_admin")) {
            canonical.put("only_admin", String.valueOf(source.get("only_admin")));
        }
        if (source.containsKey("sub_menu_items")) {
            canonical.put("sub_menu_items", canonicalItems(source.get("sub_menu_items")));
        }
        return canonical;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> raw) {
            return (Map<String, Object>) raw;
        }
        return Map.of();
    }

    public static List<Map<String, Object>> items(List<PluginCommandInfo> commands) {
        List<MenuEntry> entries = uniqueEntries(commands);
        if (entries.isEmpty()) {
            return List.of(sendMessageItem("菜单", "菜单", MAX_TOP_NAME_UNITS));
        }
        if (entries.size() <= MAX_TOP_ITEMS) {
            return entries.stream()
                    .map(entry -> sendMessageItem(entry.name(), entry.trigger(), MAX_TOP_NAME_UNITS))
                    .toList();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        int topCount = MAX_TOP_ITEMS - 1;
        for (int index = 0; index < topCount; index++) {
            MenuEntry entry = entries.get(index);
            items.add(sendMessageItem(entry.name(), entry.trigger(), MAX_TOP_NAME_UNITS));
        }
        List<Map<String, Object>> subItems = new ArrayList<>();
        int end = Math.min(entries.size(), topCount + MAX_SUB_ITEMS);
        for (int index = topCount; index < end; index++) {
            MenuEntry entry = entries.get(index);
            subItems.add(sendMessageItem(entry.name(), entry.trigger(), MAX_SUB_NAME_UNITS));
        }
        Map<String, Object> more = new LinkedHashMap<>();
        more.put("type", "menu");
        more.put("name", clip(MORE_LABEL, MAX_TOP_NAME_UNITS));
        more.put("sub_menu_items", List.copyOf(subItems));
        items.add(more);
        return List.copyOf(items);
    }

    public static List<MenuEntry> uniqueEntries(List<PluginCommandInfo> commands) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<MenuEntry> entries = new ArrayList<>();
        for (PluginCommandInfo command : commands) {
            if (command == null) {
                continue;
            }
            String trigger = trigger(command.command());
            if (trigger == null) {
                continue;
            }
            String key = trigger.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                continue;
            }
            String name = firstNonBlank(command.name(), command.command(), trigger);
            if (name == null) {
                continue;
            }
            entries.add(new MenuEntry(name, trigger));
        }
        return entries;
    }

    static String trigger(String command) {
        if (command == null || command.isBlank()) {
            return null;
        }
        String value = command.trim();
        if (isMenuAlias(value) || value.startsWith("/") || value.startsWith("!")) {
            return value;
        }
        return "/" + value;
    }

    static boolean isMenuAlias(String value) {
        return "菜单".equals(value) || "帮助".equals(value) || "菜单指令".equals(value);
    }

    public static String clip(String text, int maxUnits) {
        if (text == null) {
            return "";
        }
        String source = text.trim();
        if (source.isEmpty() || maxUnits <= 0) {
            return "";
        }
        int units = 0;
        StringBuilder clipped = new StringBuilder();
        for (int index = 0; index < source.length(); ) {
            int codePoint = source.codePointAt(index);
            int cost = codePoint > 0x7F ? 2 : 1;
            if (units + cost > maxUnits) {
                break;
            }
            clipped.appendCodePoint(codePoint);
            units += cost;
            index += Character.charCount(codePoint);
        }
        return clipped.toString();
    }

    private static Map<String, Object> sendMessageItem(String name, String trigger, int maxNameUnits) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "send_message");
        item.put("name", clip(name, maxNameUnits));
        item.put("send_message", trigger);
        return item;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    public record MenuEntry(String name, String trigger) {
    }
}
