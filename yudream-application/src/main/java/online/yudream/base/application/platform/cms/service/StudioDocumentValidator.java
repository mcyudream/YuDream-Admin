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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 结构化文档信任边界；精确schema拒绝未知字段，不解释HTML、脚本或全局CSS。 */
@Component
public class StudioDocumentValidator {
    public static final int MAX_DOCUMENT_BYTES = 2 * 1024 * 1024;
    public static final int MAX_NODES = 6000;
    private static final List<String> STYLE_STATES = List.of("hover", "focus", "active", "current", "pressed", "parent-hover", "parent-focus", "parent-expanded");
    private static final Set<String> PARTICLE_PRESETS = Set.of("none", "snow", "petals", "embers", "spores", "stars", "dust");
    private static final Pattern CLASS_NAME = Pattern.compile("[a-z][a-z0-9-]{0,63}");
    private static final Pattern FONT_FAMILY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_ -]{0,63}");
    private static final Pattern KEYFRAME_KEY = Pattern.compile("from|to|(?:100|[1-9]?\\d)(?:\\.\\d{1,2})?%");
    private static final Set<String> TAGS = Set.of("a abbr address article aside audio b bdi bdo blockquote br button caption cite code col colgroup dd del details dfn div dl dt em figcaption figure footer h1 h2 h3 h4 h5 h6 header hgroup hr i img ins kbd li main mark nav ol p picture pre q rp rt ruby s samp section small source span strong sub summary sup table tbody td th thead time tr track u ul var video wbr".split(" "));
    private static final Set<String> ATTRIBUTES = Set.of("id class title lang dir role href target rel download src poster alt width height controls autoplay loop muted playsinline preload loading decoding crossorigin type media sizes datetime cite open colspan rowspan scope headers span start reversed value kind srclang label".split(" "));
    private static final Set<String> DATA_MIMES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp", "image/avif", "audio/mpeg", "audio/wav", "audio/ogg", "audio/webm", "video/mp4", "video/webm", "video/ogg", "font/woff", "font/woff2", "font/ttf", "font/otf");
    private static final Set<String> CSS_FUNCTIONS = Set.of("var calc min max clamp rgb rgba hsl hsla hwb lab lch oklab oklch color color-mix linear-gradient radial-gradient conic-gradient repeating-linear-gradient repeating-radial-gradient repeating-conic-gradient cubic-bezier steps translate translatex translatey translatez translate3d scale scalex scaley scalez scale3d rotate rotatex rotatey rotatez rotate3d skew skewx skewy matrix matrix3d perspective blur brightness contrast drop-shadow grayscale hue-rotate invert opacity saturate sepia fit-content repeat minmax counter counters attr env round mod rem abs sign pow sqrt hypot log exp sin cos tan asin acos atan atan2".split(" "));
    private static final Pattern CSS_FUNCTION = Pattern.compile("[a-zA-Z_-][a-zA-Z0-9_-]*");
    private static final Pattern CSS_URL = Pattern.compile("url\\(\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s()'\"]+))\\s*\\)", Pattern.CASE_INSENSITIVE);
    private final ObjectMapper mapper = new ObjectMapper(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(256).maxStringLength(MAX_DOCUMENT_BYTES).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());

    public static final int MAX_TEMPLATE_BYTES = 256 * 1024;

    /**
     * 校验区块模板节点数组：包装为仅含该节点集合的最小站点文档后，按与站点文档完全相同的规则校验
     * （标签/属性/样式/URL/ID 唯一/层级与数量上限），与前端 validateStudioNodes 语义一致。
     */
    public void validateNodes(String nodesJson) {
        if (nodesJson == null || nodesJson.getBytes(StandardCharsets.UTF_8).length > MAX_TEMPLATE_BYTES) fail("区块模板内容不得超过256KiB");
        final JsonNode nodes;
        try { nodes = mapper.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(nodesJson); }
        catch (Exception ex) { throw new BizException("区块模板内容不是有效JSON"); }
        if (nodes == null || !nodes.isArray() || nodes.isEmpty()) fail("区块模板至少包含一个节点");
        var document = mapper.createObjectNode();
        document.put("schemaVersion", 1);
        document.put("id", "template-validation-document");
        document.put("name", "");
        var theme = document.putObject("theme");
        theme.putObject("tokens");
        theme.putArray("header");
        theme.putArray("footer");
        var page = document.putArray("pages").addObject();
        page.put("id", "template-validation-page");
        page.put("name", "");
        page.put("path", "/");
        page.set("root", nodes);
        document.putArray("assets");
        validate(document.toString());
    }

    public void validate(String documentJson) {
        if (documentJson == null || documentJson.getBytes(StandardCharsets.UTF_8).length > MAX_DOCUMENT_BYTES) fail("文档不得超过2MiB");
        final JsonNode document;
        try { document = mapper.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(documentJson); }
        catch (Exception ex) { throw new BizException("站点文档不是有效JSON"); }
        object(document); keys(document, "schemaVersion", "id", "name", "theme", "pages", "assets", "features");
        if (!document.path("schemaVersion").isIntegralNumber() || !document.path("schemaVersion").canConvertToInt() || document.path("schemaVersion").intValue() != 1) fail("文档schemaVersion必须为1");
        Set<String> ids = new HashSet<>();
        identity(document, ids); string(document, "name");
        JsonNode theme = document.get("theme"); object(theme); keys(theme, "tokens", "header", "footer", "classes", "fonts", "keyframes");
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
            object(page); keys(page, "id", "name", "path", "root", "settings"); identity(page, ids); string(page, "name");
            String path = text(page, "path");
            if (!path.matches("/(?:(?:[A-Za-z0-9_-]+|:[a-zA-Z][A-Za-z0-9_]{0,31})(?:/(?:[A-Za-z0-9_-]+|:[a-zA-Z][A-Za-z0-9_]{0,31}))*)?")) fail("页面路由必须为不含点段、查询串的站内绝对路径（动态段写作:参数）");
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
        Map<String, String> assetKinds = new HashMap<>();
        for (JsonNode asset : assets) assetKinds.put(asset.get("id").asText(), asset.get("kind").asText());
        themeExtras(theme, assetKinds);
        for (JsonNode page : pages) if (page.has("settings")) pageSettings(page.get("settings"), assetKinds);
        Set<String> fontFamilies = new HashSet<>();
        if (theme.has("fonts")) theme.get("fonts").forEach(font -> fontFamilies.add(font.path("family").asText()));
        Set<String> classNames = new HashSet<>();
        if (theme.has("classes")) theme.get("classes").forEach(item -> classNames.add(item.path("name").asText()));
        if (document.has("features")) features(document.get("features"), assetKinds, fontFamilies, classNames);
    }

    /** 主题扩展：共享样式类（含状态与断点）、字体（引用 font 资产）、关键帧；与前端 validateStudioDocument 同规则。 */
    private void themeExtras(JsonNode theme, Map<String, String> assetKinds) {
        if (theme.has("classes")) {
            JsonNode classes = theme.get("classes"); array(classes);
            if (classes.size() > 500) fail("样式类不得超过500个");
            Set<String> names = new HashSet<>();
            for (JsonNode item : classes) {
                object(item); keys(item, "name", "styles", "responsive", "states");
                String name = text(item, "name");
                if (!CLASS_NAME.matcher(name).matches() || !names.add(name)) fail("样式类名须为小写字母开头的唯一名称");
                styles(item.get("styles"));
                if (item.has("responsive")) {
                    JsonNode responsive = item.get("responsive"); object(responsive); keys(responsive, "tablet", "mobile");
                    responsive.elements().forEachRemaining(this::styles);
                }
                if (item.has("states")) {
                    JsonNode states = item.get("states"); object(states); keys(states, STYLE_STATES.toArray(String[]::new));
                    states.elements().forEachRemaining(this::styles);
                }
            }
        }
        if (theme.has("fonts")) {
            JsonNode fonts = theme.get("fonts"); array(fonts);
            if (fonts.size() > 32) fail("字体不得超过32个");
            for (JsonNode item : fonts) {
                object(item); keys(item, "family", "assetId", "weight", "style");
                if (!FONT_FAMILY.matcher(text(item, "family")).matches()) fail("字体族名只能含字母、数字、空格、下划线与连字符");
                assetRef(item, "assetId", "font", assetKinds);
                if (item.has("weight") && !text(item, "weight").matches("normal|bold|[1-9]00")) fail("字重须为normal、bold或100至900");
                if (item.has("style") && !Set.of("normal", "italic").contains(text(item, "style"))) fail("字形须为normal或italic");
            }
        }
        if (theme.has("keyframes")) {
            JsonNode keyframes = theme.get("keyframes"); array(keyframes);
            if (keyframes.size() > 64) fail("关键帧动画不得超过64个");
            Set<String> names = new HashSet<>();
            for (JsonNode item : keyframes) {
                object(item); keys(item, "name", "frames");
                String name = text(item, "name");
                if (!CLASS_NAME.matcher(name).matches() || !names.add(name)) fail("动画名须为小写字母开头的唯一名称");
                JsonNode frames = item.get("frames"); object(frames);
                if (frames.isEmpty() || frames.size() > 32) fail("关键帧数量须为1至32");
                frames.fields().forEachRemaining(entry -> {
                    if (!KEYFRAME_KEY.matcher(entry.getKey()).matches()) fail("关键帧须为from、to或0%至100%");
                    styles(entry.getValue());
                });
            }
        }
    }

    private void pageSettings(JsonNode settings, Map<String, String> assetKinds) {
        object(settings); keys(settings, "particles", "toast", "iconAssetId", "hostRoutes", "requires", "shell");
        if (settings.has("shell")) {
            if (!settings.get("shell").isBoolean()) fail("shell须为布尔值");
            if (settings.get("shell").booleanValue() && (!settings.has("hostRoutes") || !settings.get("hostRoutes").isArray() || settings.get("hostRoutes").isEmpty())) fail("承载外壳页须声明至少一个承载路由");
        }
        if (settings.has("requires")) requires(settings.get("requires"));
        if (settings.has("hostRoutes")) {
            JsonNode routes = settings.get("hostRoutes"); array(routes);
            if (routes.size() > 10) fail("承载路由最多10个");
            for (JsonNode route : routes) {
                if (!route.isTextual() || !route.asText().matches("/[A-Za-z0-9][A-Za-z0-9_/-]{0,63}") || route.asText().matches("(?i)/site(/.*)?") || route.asText().contains("//")) fail("承载路由须为宿主公开页路径且不能是/site");
            }
        }
        if (settings.has("particles")) particles(settings.get("particles"));
        if (settings.has("iconAssetId")) assetRef(settings, "iconAssetId", "image", assetKinds);
        if (settings.has("toast")) {
            JsonNode toast = settings.get("toast"); object(toast); keys(toast, "title", "body", "iconAssetId");
            boundedText(toast, "title", 80, false);
            boundedText(toast, "body", 200, true);
            if (toast.has("iconAssetId")) assetRef(toast, "iconAssetId", "image", assetKinds);
        }
    }

    private void features(JsonNode features, Map<String, String> assetKinds, Set<String> fontFamilies, Set<String> classNames) {
        object(features); keys(features, "audio", "particles", "navigation", "dataSources", "publicSkin", "content");
        if (features.has("content")) content(features.get("content"));
        if (features.has("publicSkin")) publicSkin(features.get("publicSkin"), assetKinds, fontFamilies, classNames);
        if (features.has("dataSources")) dataSources(features.get("dataSources"));
        if (features.has("particles")) particles(features.get("particles"));
        if (features.has("navigation")) navigation(features.get("navigation"), assetKinds);
        if (!features.has("audio")) return;
        JsonNode audio = features.get("audio"); object(audio);
        keys(audio, "tracks", "musicVolume", "effectVolume", "effectsDefaultOn", "clickSoundAssetId", "buttonSoundAssetId", "toastSoundAssetId");
        JsonNode tracks = audio.get("tracks"); array(tracks);
        if (tracks.size() > 20) fail("背景音乐不得超过20首");
        for (JsonNode track : tracks) {
            object(track); keys(track, "title", "assetId");
            boundedText(track, "title", 80, false);
            assetRef(track, "assetId", "audio", assetKinds);
        }
        volume(audio.get("musicVolume")); volume(audio.get("effectVolume"));
        if (audio.get("effectsDefaultOn") == null || !audio.get("effectsDefaultOn").isBoolean()) fail("effectsDefaultOn须为布尔值");
        for (String key : List.of("clickSoundAssetId", "buttonSoundAssetId", "toastSoundAssetId")) {
            if (audio.has(key)) assetRef(audio, key, "audio", assetKinds);
        }
    }

    private static final Pattern ADAPTER_ENDPOINT = Pattern.compile("/api/plugins/[a-z0-9][a-z0-9-]{0,63}/public(?:/(?:[A-Za-z0-9_.-]+|\\{[a-zA-Z][A-Za-z0-9_]{0,31}\\}))+");
    private static final Pattern FIELD_PATH = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,31}(?:\\.[A-Za-z_][A-Za-z0-9_]{0,31}){0,4}");

    /** 插件数据适配器：只允许读取插件匿名公开接口 /api/plugins/{插件}/public/**，与前端 validateDataSources 同规则 */
    private void dataSources(JsonNode sources) {
        array(sources);
        if (sources.size() > 20) fail("插件数据适配器最多20个");
        Set<String> ids = new HashSet<>();
        for (JsonNode item : sources) {
            object(item); keys(item, "id", "label", "endpoint", "recordsPath", "single", "fields");
            String id = text(item, "id");
            if (!id.matches("[a-z][a-z0-9-]{0,31}") || !ids.add(id)) fail("适配器编码须为小写字母开头的唯一名称");
            boundedText(item, "label", 40, false);
            String endpoint = text(item, "endpoint");
            if (!ADAPTER_ENDPOINT.matcher(endpoint).matches() || endpoint.contains("..")) fail("接口路径须为插件公开接口/api/plugins/{插件}/public/...");
            if (item.has("recordsPath")) {
                JsonNode path = item.get("recordsPath");
                if (!path.isTextual() || (!path.asText().isEmpty() && !FIELD_PATH.matcher(path.asText()).matches())) fail("列表路径须为点分字段名");
            }
            if (item.has("single") && !item.get("single").isBoolean()) fail("single须为布尔值");
            if (item.has("fields")) {
                JsonNode fields = item.get("fields"); object(fields);
                if (fields.size() > 30) fail("字段映射最多30项");
                fields.fields().forEachRemaining(entry -> {
                    if (!entry.getKey().matches("[A-Za-z_][A-Za-z0-9_]{0,31}")) fail("映射目标字段名无效");
                    if (!entry.getValue().isTextual() || !FIELD_PATH.matcher(entry.getValue().asText()).matches()) fail("映射源字段须为点分字段名");
                });
            }
        }
    }

    private static final List<String> SKIN_COLORS = List.of("background", "surface", "text", "muted", "border", "primary", "primaryText");

    /** 公开页适配：颜色限 #rrggbb，字体须为主题字体族，背景图引用图片资源；与前端 validatePublicSkin 同规则 */
    private void publicSkin(JsonNode skin, Map<String, String> assetKinds, Set<String> fontFamilies, Set<String> classNames) {
        object(skin);
        java.util.List<String> allowed = new java.util.ArrayList<>(List.of("chrome", "dark", "fontFamily", "backgroundImageAssetId", "bottomOffset", "pixel", "classes"));
        allowed.addAll(SKIN_COLORS);
        keys(skin, allowed.toArray(String[]::new));
        if (skin.get("chrome") == null || !skin.get("chrome").isBoolean() || skin.get("dark") == null || !skin.get("dark").isBoolean()) fail("chrome/dark须为布尔值");
        for (String key : SKIN_COLORS) {
            if (!text(skin, key).matches("#[0-9a-fA-F]{6}")) fail("公开页颜色须为#rrggbb");
        }
        if (skin.has("fontFamily") && !fontFamilies.contains(text(skin, "fontFamily"))) fail("公开页字体须为主题字体族");
        if (skin.has("backgroundImageAssetId")) assetRef(skin, "backgroundImageAssetId", "image", assetKinds);
        if (skin.has("pixel") && !skin.get("pixel").isBoolean()) fail("pixel须为布尔值");
        if (skin.has("classes")) {
            JsonNode classes = skin.get("classes"); object(classes); keys(classes, "panel", "button", "control", "heading");
            classes.fields().forEachRemaining(entry -> {
                if (!entry.getValue().isTextual() || !classNames.contains(entry.getValue().asText())) fail("公开页沿用的样式类须为已有主题样式类");
            });
        }
        if (skin.has("bottomOffset") && !text(skin, "bottomOffset").matches("0|\\d{1,3}(?:px|rem)")) fail("底部留白须为0至999px或rem");
    }

    /** 站点导航：最多两级、每级 30 项，ID 唯一，地址为站内路径或 http(s)，图标引用图片资源 */
    private void navigation(JsonNode navigation, Map<String, String> assetKinds) {
        object(navigation); keys(navigation, "items", "appendAuto");
        if (navigation.get("appendAuto") == null || !navigation.get("appendAuto").isBoolean()) fail("appendAuto须为布尔值");
        navItems(navigation.get("items"), 1, new HashSet<>(), assetKinds);
    }

    private void navItems(JsonNode items, int depth, Set<String> ids, Map<String, String> assetKinds) {
        array(items);
        if (items.size() > 30) fail("每级导航最多30项");
        for (JsonNode item : items) {
            object(item); keys(item, "id", "label", "url", "iconAssetId", "requires", "children");
            if (item.has("requires")) requires(item.get("requires"));
            String id = text(item, "id");
            if (!id.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}") || !ids.add(id)) fail("导航ID须唯一且安全");
            boundedText(item, "label", 40, false);
            String url = text(item, "url");
            if (url.startsWith("#") || url.startsWith("data:")) fail("导航地址须为站内路径或http(s)链接");
            safeUrl(url, false);
            if (item.has("iconAssetId")) assetRef(item, "iconAssetId", "image", assetKinds);
            if (item.has("children")) {
                if (depth != 1) fail("导航最多两级");
                navItems(item.get("children"), depth + 1, ids, assetKinds);
            }
        }
    }

    private static final Set<String> CONTENT_TYPES = Set.of("text", "textarea", "number", "switch", "select", "color", "image", "link", "list", "particles", "audio", "video");
    private static final Map<String, String> CONTENT_EFFECT_TYPES = Map.of(
            "particles", "particles", "music", "list", "musicVolume", "number", "effectsDefaultOn", "switch",
            "effectVolume", "number", "clickSound", "audio", "buttonSound", "audio", "toastSound", "audio", "pageEffects", "list");
    private static final Pattern CONTENT_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,47}");
    private static final Pattern PAGE_EFFECT_PATH = Pattern.compile("/(?:[A-Za-z0-9_-]+|:[A-Za-z][A-Za-z0-9_]*)(?:/(?:[A-Za-z0-9_-]+|:[A-Za-z][A-Za-z0-9_]*))*|/");

    /** 站点内容：内容模型（分区 → 字段，list 带条目字段且不嵌套）与按类型校验的值；与前端 validateContent 同规则 */
    private void content(JsonNode content) {
        object(content); keys(content, "sections", "values");
        JsonNode sections = content.get("sections"); array(sections);
        if (sections.size() > 20) fail("内容分区最多20个");
        Set<String> codes = new HashSet<>();
        Set<String> effects = new HashSet<>();
        Map<String, JsonNode> fields = new HashMap<>();
        for (JsonNode section : sections) {
            object(section); keys(section, "code", "title", "description", "fields");
            String code = text(section, "code");
            if (!code.matches("[a-z][a-z0-9-]{0,31}") || !codes.add(code)) fail("分区编码须为小写字母开头的唯一名称");
            boundedText(section, "title", 40, false);
            if (section.has("description")) boundedText(section, "description", 200, true);
            JsonNode list = section.get("fields"); array(list);
            if (list.size() > 40) fail("每个分区最多40个字段");
            for (JsonNode field : list) contentField(field, fields, false, effects);
        }
        JsonNode values = content.get("values"); object(values);
        values.fields().forEachRemaining(entry -> {
            JsonNode field = fields.get(entry.getKey());
            if (field == null) fail("内容值含未声明的字段：" + entry.getKey());
            contentValue(field, entry.getValue());
        });
    }

    private void contentField(JsonNode field, Map<String, JsonNode> fields, boolean nested, Set<String> effects) {
        object(field); keys(field, "key", "label", "type", "description", "placeholder", "defaultValue", "options", "itemFields", "effect");
        String key = text(field, "key");
        if (!CONTENT_KEY.matcher(key).matches() || fields.containsKey(key)) fail("字段键须为字母开头的唯一名称：" + key);
        fields.put(key, field);
        boundedText(field, "label", 40, false);
        String type = text(field, "type");
        if (!CONTENT_TYPES.contains(type)) fail("字段类型无效：" + type);
        if (field.has("description")) boundedText(field, "description", 200, true);
        if (field.has("placeholder")) boundedText(field, "placeholder", 100, true);
        if ("select".equals(type)) {
            JsonNode options = field.get("options"); array(options);
            if (options.isEmpty() || options.size() > 30) fail("下拉字段须有1至30个选项");
            for (JsonNode option : options) {
                object(option); keys(option, "label", "value");
                boundedText(option, "label", 40, false);
                boundedText(option, "value", 64, false);
            }
        } else if (field.has("options")) fail("只有下拉字段可声明选项");
        if ("list".equals(type)) {
            if (nested) fail("列表条目内不能再嵌套列表");
            JsonNode items = field.get("itemFields"); array(items);
            if (items.isEmpty() || items.size() > 12) fail("列表须声明1至12个条目字段");
            Map<String, JsonNode> itemFields = new HashMap<>();
            for (JsonNode item : items) contentField(item, itemFields, true, effects);
        } else if (field.has("itemFields")) fail("只有列表字段可声明条目字段");
        if (field.has("effect")) {
            if (nested) fail("只有顶层字段可驱动站点功能");
            String effect = text(field, "effect");
            if (!CONTENT_EFFECT_TYPES.containsKey(effect) || !effects.add(effect)) fail("站点功能字段无效或重复：" + effect);
            if (!type.equals(CONTENT_EFFECT_TYPES.get(effect))) fail("站点功能" + effect + "字段类型不符");
            if ("music".equals(effect)) effectItemFields(field, Map.of("src", Set.of("audio"), "title", Set.of("text")), "src");
            if ("pageEffects".equals(effect)) effectItemFields(field, Map.of(
                    "path", Set.of("text"), "particles", Set.of("particles"), "toastTitle", Set.of("text"),
                    "toastBody", Set.of("text", "textarea"), "toastIcon", Set.of("image")), "path");
        }
        if (field.has("defaultValue")) contentValue(field, field.get("defaultValue"));
    }

    private void effectItemFields(JsonNode field, Map<String, Set<String>> allowed, String required) {
        boolean found = false;
        for (JsonNode item : field.get("itemFields")) {
            String key = item.get("key").asText();
            found |= required.equals(key);
            if (!allowed.getOrDefault(key, Set.of()).contains(item.get("type").asText())) fail("站点功能条目字段不受支持或类型不符：" + key);
        }
        if (!found) fail("站点功能条目须包含" + required + "字段");
    }

    /** 按字段类型校验内容值；空串表示未填写 */
    private void contentValue(JsonNode field, JsonNode value) {
        String type = field.get("type").asText();
        String name = field.get("label").asText();
        switch (type) {
            case "number" -> {
                if (value == null || !value.isNumber() || !Double.isFinite(value.doubleValue())) fail(name + "须为数字");
                String effect = field.path("effect").asText();
                if (("musicVolume".equals(effect) || "effectVolume".equals(effect))
                        && (!value.isIntegralNumber() || value.longValue() < 0 || value.longValue() > 100)) fail(name + "须为0至100的整数");
            }
            case "switch" -> { if (value == null || !value.isBoolean()) fail(name + "须为开关值"); }
            case "list" -> {
                array(value);
                if (value.size() > 50) fail(name + "最多50条");
                String effect = field.path("effect").asText();
                if ("music".equals(effect) && value.size() > 20) fail("背景音乐最多20首");
                Set<String> paths = new HashSet<>();
                for (JsonNode item : value) {
                    object(item);
                    item.fields().forEachRemaining(entry -> {
                        JsonNode itemField = null;
                        for (JsonNode candidate : field.get("itemFields")) {
                            if (entry.getKey().equals(candidate.get("key").asText())) itemField = candidate;
                        }
                        if (itemField == null) fail(name + "含未声明的字段：" + entry.getKey());
                        contentValue(itemField, entry.getValue());
                    });
                    if ("music".equals(effect) && !item.path("src").isTextual()) fail("背景音乐条目须填写音频");
                    if ("music".equals(effect) && item.path("src").asText().isEmpty()) fail("背景音乐条目须填写音频");
                    if ("pageEffects".equals(effect)) {
                        String path = text(item, "path");
                        if (!PAGE_EFFECT_PATH.matcher(path).matches()) fail("页面氛围须填写不含查询参数的站内页面地址");
                        if (!paths.add(path.toLowerCase(Locale.ROOT))) fail("页面氛围地址重复：" + path);
                    }
                }
            }
            default -> {
                if (value == null || !value.isTextual()) fail(name + "必须为字符串");
                String text = value.asText();
                int max = "textarea".equals(type) ? 5000 : 2000;
                if (text.length() > max || text.chars().anyMatch(c -> (c < 32 && c != '\n' && c != '\t') || c == 127)) fail(name + "须为" + max + "字以内的文本");
                if (text.isEmpty()) return;
                switch (type) {
                    case "color" -> { if (!text.matches("#[0-9a-fA-F]{6}")) fail(name + "须为#rrggbb"); }
                    case "image" -> { if (text.startsWith("#") || text.startsWith("data:")) fail(name + "须为图片地址"); safeUrl(text, false); }
                    case "audio" -> { if (text.startsWith("#") || text.startsWith("data:")) fail(name + "须为音频地址"); safeUrl(text, false); }
                    case "video" -> { if (text.startsWith("#") || text.startsWith("data:")) fail(name + "须为视频地址"); safeUrl(text, false); }
                    case "link" -> { if (text.startsWith("data:")) fail(name + "须为链接地址"); safeUrl(text, false); }
                    case "particles" -> { if (!PARTICLE_PRESETS.contains(text)) fail(name + "不是可用的粒子效果"); }
                    case "select" -> {
                        boolean found = false;
                        for (JsonNode option : field.get("options")) found |= text.equals(option.get("value").asText());
                        if (!found) fail(name + "不是可选值");
                    }
                    default -> { }
                }
            }
        }
    }

    /** 依赖声明：plugin:插件编码 或 capability:wiki / capability:plugin-market，最多10项 */
    private void requires(JsonNode value) {
        array(value);
        if (value.size() > 10) fail("依赖最多10项");
        for (JsonNode item : value) {
            if (!item.isTextual() || !item.asText().matches("plugin:[a-z0-9][a-z0-9-]{0,63}|capability:(?:wiki|plugin-market)")) fail("依赖须为plugin:插件编码或capability:wiki/plugin-market");
        }
    }

    private void particles(JsonNode value) {
        if (value == null || !value.isTextual() || !PARTICLE_PRESETS.contains(value.asText())) fail("无效的粒子效果");
    }

    private void volume(JsonNode value) {
        if (value == null || !value.isIntegralNumber() || value.intValue() < 0 || value.intValue() > 100) fail("音量须为0至100的整数");
    }

    private void boundedText(JsonNode node, String key, int max, boolean allowEmpty) {
        JsonNode value = node.get(key);
        if (value == null || !value.isTextual()) fail(key + "必须为字符串");
        String text = value.asText();
        if ((!allowEmpty && text.isBlank()) || text.length() > max || text.chars().anyMatch(c -> c < 32 || c == 127)) fail(key + "须为" + max + "字以内的文本");
    }

    private void assetRef(JsonNode node, String key, String kind, Map<String, String> assetKinds) {
        if (!kind.equals(assetKinds.get(text(node, key)))) fail(key + "必须引用已有的" + kind + "资源");
    }
    private void nodes(JsonNode nodes, Set<String> ids, int[] count, int depth) {
        array(nodes);
        if (depth > 32 && !nodes.isEmpty()) fail("节点深度不得超过32");
        for (JsonNode node : nodes) {
            if (++count[0] > MAX_NODES) fail("节点数量不得超过" + MAX_NODES);
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
