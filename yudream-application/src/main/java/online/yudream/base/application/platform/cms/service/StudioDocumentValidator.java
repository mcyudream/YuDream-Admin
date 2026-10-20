package online.yudream.base.application.platform.cms.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.domain.common.exception.BizException;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** 结构化文档信任边界；精确schema拒绝未知字段，不解释HTML、脚本或全局CSS。 */
@Component
public class StudioDocumentValidator {
    public static final int MAX_DOCUMENT_BYTES = 2 * 1024 * 1024;
    private static final Set<String> TAGS = Set.of("a abbr address article aside audio b bdi bdo blockquote br button caption cite code col colgroup dd del details dfn div dl dt em figcaption figure footer h1 h2 h3 h4 h5 h6 header hgroup hr i img ins kbd li main mark nav ol p picture pre q rp rt ruby s samp section small source span strong sub summary sup table tbody td th thead time tr track u ul var video wbr".split(" "));
    private static final Set<String> ATTRIBUTES = Set.of("id class title lang dir role href target rel download src poster alt width height controls autoplay loop muted preload loading decoding crossorigin type media sizes datetime cite open colspan rowspan scope headers span start reversed value kind srclang label".split(" "));
    private static final Set<String> DATA_MIMES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp", "image/avif", "audio/mpeg", "audio/wav", "audio/ogg", "audio/webm", "video/mp4", "video/webm", "video/ogg", "font/woff", "font/woff2", "font/ttf", "font/otf");
    private static final Set<String> CSS_FUNCTIONS = Set.of("var calc min max clamp rgb rgba hsl hsla hwb lab lch oklab oklch color color-mix linear-gradient radial-gradient conic-gradient repeating-linear-gradient repeating-radial-gradient repeating-conic-gradient cubic-bezier steps translate translatex translatey translatez translate3d scale scalex scaley scalez scale3d rotate rotatex rotatey rotatez rotate3d skew skewx skewy matrix matrix3d perspective blur brightness contrast drop-shadow grayscale hue-rotate invert opacity saturate sepia fit-content repeat minmax counter counters attr env round mod rem abs sign pow sqrt hypot log exp sin cos tan asin acos atan atan2".split(" "));
    private static final Pattern CSS_FUNCTION = Pattern.compile("[a-zA-Z_-][a-zA-Z0-9_-]*");
    private static final Pattern CSS_URL = Pattern.compile("url\\(\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s()'\"]+))\\s*\\)", Pattern.CASE_INSENSITIVE);
    private final ObjectMapper mapper = new ObjectMapper(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(256).maxStringLength(MAX_DOCUMENT_BYTES).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());

    public void validate(String documentJson) {
        if (documentJson == null || documentJson.getBytes(StandardCharsets.UTF_8).length > MAX_DOCUMENT_BYTES) fail("文档不得超过2MiB");
        final JsonNode document;
        try { document = mapper.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(documentJson); }
        catch (Exception ex) { throw new BizException("站点文档不是有效JSON"); }
        object(document); keys(document, "schemaVersion", "id", "name", "theme", "pages", "assets");
        if (!document.path("schemaVersion").isIntegralNumber() || !document.path("schemaVersion").canConvertToInt() || document.path("schemaVersion").intValue() != 1) fail("文档schemaVersion必须为1");
        Set<String> ids = new HashSet<>();
        identity(document, ids); string(document, "name");
        JsonNode theme = document.get("theme"); object(theme); keys(theme, "tokens", "header", "footer");
        JsonNode tokens = theme.get("tokens"); object(tokens);
        Set<String> tokenNames = new HashSet<>();
        tokens.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            if (!key.matches("(?:--)?[a-zA-Z][a-zA-Z0-9_-]*")) fail("主题变量名不合法");
            String canonical = key.startsWith("--") ? key.substring(2) : key;
            if (!tokenNames.add(canonical)) fail("主题变量名重复");
            var one = mapper.createObjectNode(); one.set("--" + canonical, entry.getValue()); styles(one);
        });
        int[] count = {0};
        nodes(theme.get("header"), ids, count, 1);
        nodes(theme.get("footer"), ids, count, 1);
        Set<String> paths = new HashSet<>();
        JsonNode pages = document.get("pages"); array(pages);
        if (pages.isEmpty() || pages.size() > 200) fail("页面数量必须为1至200");
        for (JsonNode page : pages) {
            object(page); keys(page, "id", "name", "path", "root"); identity(page, ids); string(page, "name");
            String path = text(page, "path");
            if (!path.matches("/(?:[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*)?")) fail("页面路由必须为不含点段、查询串的站内绝对路径");
            if (!paths.add(path.toLowerCase(Locale.ROOT))) fail("页面路由不能重复");
            nodes(page.get("root"), ids, count, 1);
        }
        JsonNode assets = document.get("assets"); array(assets);
        if (assets.size() > 500) fail("资源数量不得超过500");
        for (JsonNode asset : assets) {
            object(asset); keys(asset, "id", "name", "url", "mimeType", "kind"); identity(asset, ids); string(asset, "name");
            String mime = text(asset, "mimeType");
            if (!mime.matches("[a-z0-9.+-]+/[a-z0-9.+-]+") || mime.contains("xml")) fail("资源mimeType不合法");
            String kind = text(asset, "kind");
            if (!Set.of("image", "video", "audio", "font", "file").contains(kind)) fail("资源kind不合法");
            String url = text(asset, "url"); safeUrl(url, !kind.equals("file"));
            if (mime.toLowerCase(Locale.ROOT).contains("svg") || mime.toLowerCase(Locale.ROOT).contains("html") || mime.toLowerCase(Locale.ROOT).contains("javascript")) fail("不支持可执行资源类型");
            if (!kind.equals("file") && !mime.startsWith(kind + "/")) fail("资源mimeType与kind不匹配");
            if (url.startsWith("data:") && !url.startsWith("data:" + mime + ";base64,")) fail("内嵌资源mimeType不匹配");
        }
    }
    private void nodes(JsonNode nodes, Set<String> ids, int[] count, int depth) {
        array(nodes);
        if (depth > 32 && !nodes.isEmpty()) fail("节点深度不得超过32");
        for (JsonNode node : nodes) {
            if (++count[0] > 2000) fail("节点数量不得超过2000");
            object(node); keys(node, "id", "type", "tag", "text", "attributes", "styles", "responsive", "children"); identity(node, ids); text(node, "type");
            if (!TAGS.contains(text(node, "tag"))) fail("节点标签不在安全白名单内");
            if (node.has("text") && !node.get("text").isTextual()) fail("节点text必须是字符串");
            JsonNode attrs = node.get("attributes"); object(attrs);
            attrs.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                if (!key.equals(key.toLowerCase(Locale.ROOT)) || key.startsWith("on") || !(ATTRIBUTES.contains(key) || key.matches("(?:aria|data)-[a-z0-9-]+"))) fail("节点属性不在安全白名单内");
                if (!entry.getValue().isTextual()) fail("属性值必须为字符串");
                if (Set.of("src", "poster", "href", "cite").contains(key)) safeUrl(entry.getValue().asText(), key.equals("src") || key.equals("poster"));
                if (key.equals("crossorigin") && !entry.getValue().asText().equals("anonymous")) fail("资源禁止携带跨域凭据");
                if (Set.of("data-studio-document", "data-studio-region", "data-studio-type").contains(key)) fail("属性与编译器保留标识冲突");
                if (key.equals("data-studio-id") && !entry.getValue().asText().equals(node.get("id").asText())) fail("稳定ID属性与模型不一致");
                if (entry.getValue().asText().indexOf(0) >= 0) fail("属性不能含空字符");
                if (key.equals("target") && !Set.of("_self", "_blank").contains(entry.getValue().asText())) fail("不支持的链接target");
            });
            styles(node.get("styles"));
            if (node.has("responsive")) {
                JsonNode responsive = node.get("responsive"); object(responsive); keys(responsive, "tablet", "mobile");
                if (responsive.has("tablet")) styles(responsive.get("tablet"));
                if (responsive.has("mobile")) styles(responsive.get("mobile"));
            }
            JsonNode children = node.get("children"); array(children);
            if (node.has("text") && !children.isEmpty()) fail("text与children不能同时存在");
            if (Set.of("br", "col", "hr", "img", "source", "track", "wbr").contains(node.get("tag").asText()) && (node.has("text") || !children.isEmpty())) fail("空元素不能含文本或子节点");
            nodes(children, ids, count, depth + 1);
        }
    }
    private void styles(JsonNode styles) {
        object(styles);
        styles.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            if (!key.matches("(?:--[a-zA-Z][a-zA-Z0-9_-]*|[a-z][a-z0-9-]*)") || key.equalsIgnoreCase("behavior") || key.toLowerCase(Locale.ROOT).contains("binding")) fail("CSS属性不合法");
            if (!entry.getValue().isTextual()) fail("CSS值必须为字符串");
            String value = entry.getValue().asText();
            if (value.length() > MAX_DOCUMENT_BYTES || value.chars().anyMatch(c -> c < 32 || c == 127) || value.contains("\\") || value.contains("/*") || value.contains("*/") || value.toLowerCase(Locale.ROOT).matches("(?s).*(expression|javascript|vbscript|behavior|-moz-binding).*")) fail("CSS值包含危险语法");
            var matcher = CSS_URL.matcher(value);
            var remainder = new StringBuffer();
            while (matcher.find()) {
                String url = matcher.group(1) != null ? matcher.group(1) : matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
                safeUrl(url, true);
                matcher.appendReplacement(remainder, "");
            }
            matcher.appendTail(remainder);
            String remaining = remainder.toString();
            if (remaining.matches("(?s).*[;{}@<>].*") || remaining.toLowerCase(Locale.ROOT).contains("url")) fail("CSS资源URL或声明语法不合法");
            var functions = CSS_FUNCTION.matcher(remaining);
            while (functions.find()) {
                int cursor = functions.end();
                while (cursor < remaining.length() && remaining.charAt(cursor) == ' ') cursor++;
                if (cursor < remaining.length() && remaining.charAt(cursor) == '(' && !CSS_FUNCTIONS.contains(functions.group().toLowerCase(Locale.ROOT))) fail("不支持的CSS函数");
            }
            int balance = 0;
            for (char c : remaining.toCharArray()) {
                if (c == '(') balance++;
                if (c == ')' && --balance < 0) fail("CSS括号不匹配");
            }
            if (balance != 0) fail("CSS括号不匹配");
        });
    }
    private void safeUrl(String value, boolean allowData) {
        if (value == null || value.isBlank() || !value.equals(value.strip()) || value.chars().anyMatch(c -> c <= 32 || c == 127) || value.matches("(?s).*[<>\"`].*") || value.contains("\\")) fail("资源URL不合法");
        if (value.startsWith("data:")) {
            int comma = value.indexOf(',');
            if (!allowData || comma < 12 || !value.substring(0, comma).endsWith(";base64") || !DATA_MIMES.contains(value.substring(5, comma - 7))) fail("内嵌资源只支持白名单base64媒体格式");
            try {
                String original = value.substring(comma + 1);
                byte[] decoded = Base64.getDecoder().decode(original);
                if (decoded.length == 0 || !Base64.getEncoder().encodeToString(decoded).equals(original)) fail("内嵌资源必须为规范base64");
            }
            catch (IllegalArgumentException ex) { throw new BizException("内嵌资源base64不合法"); }
            return;
        }
        try {
            URI uri = URI.create(value);
            if (value.startsWith("/") && !value.startsWith("//") && uri.getRawAuthority() == null && uri.getScheme() == null) {
                String decoded = uri.getPath();
                if (decoded == null || decoded.contains("\\") || decoded.startsWith("//") || decoded.chars().anyMatch(c -> c < 32 || c == 127)) fail("站内资源路径不合法");
                return;
            }
            if (value.startsWith("#")) return;
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null) fail("资源仅允许http、https、站内路径或白名单data");
        } catch (IllegalArgumentException ex) { throw new BizException("资源URL不合法"); }
    }
    private void identity(JsonNode node, Set<String> ids) {
        String id = text(node, "id");
        if (!id.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,127}") || !ids.add(id)) fail("文档实体ID必须合法且全局唯一");
    }
    private String string(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || !value.isTextual()) fail(key + "必须为字符串");
        return value.asText();
    }
    private String text(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || !value.isTextual() || value.asText().isBlank()) fail(key + "必须为非空字符串");
        return value.asText();
    }
    private void keys(JsonNode value, String... allowed) {
        Set<String> names = Set.of(allowed);
        value.fieldNames().forEachRemaining(key -> { if (!names.contains(key)) fail("未知字段：" + key); });
    }
    private void object(JsonNode value) { if (value == null || !value.isObject()) fail("文档字段必须为对象"); }
    private void array(JsonNode value) { if (value == null || !value.isArray()) fail("文档字段必须为数组"); }
    private void fail(String message) { throw new BizException(message); }
}
