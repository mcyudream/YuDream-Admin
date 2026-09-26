package online.yudream.base.interfaces.common.interceptor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 通用敏感字段脱敏（宿主请求日志/审计专用，纯静态、无状态）。
 *
 * <p>JSON 主体（{ 或 [ 开头）走 Jackson parse-tree 递归：按<b>解码后</b>字段名判定
 * （Unicode 逃逸键由 Jackson 解码，与判定输入一致），敏感键<b>整个值</b>替换为
 * {@code "******"}（任意值类型）；非敏感字段原样保留；未命中敏感键原样返回原始
 * 文本，不做重序列化。</p>
 *
 * <p>JSON 解析失败（截断、非法转义、尾随垃圾、嵌套超限）时整段替换为固定占位符
 * {@link #UNPARSEABLE_PLACEHOLDER}，不保留任何猜测原文：解析失败可由请求方故意
 * 制造，失败体不得以任何形式进入日志；审计仍有 path/status/耗时等字段可用。</p>
 *
 * <p>键名判定分两类：</p>
 * <ul>
 *   <li><b>凭据键（fail closed）</b>：键名包含 token、secret、password、passwd、pwd、
 *       authorization、cookie、credential、ticket、api[_-]?key、private[_-]?key 之一
 *       （大小写不敏感、允许任意前后缀与 -/_ 分隔）——enrollToken、nodeSecret、
 *       access_token、refreshToken、ws_ticket、apiKey、private_key、X-API-KEY 等全部命中；</li>
 *   <li><b>内容键（既有行为）</b>：message、content、html、prompt 整键精确匹配，
 *       不随凭据片段扩散，messageId、contentType、context 等不误伤。</li>
 * </ul>
 *
 * <p>非 JSON 文本走有界对切分 fallback：按 {@code &} 切对、首个 {@code =} 或
 * {@code :} 切键值，键先 URL 解码（失败回落原文）再按同名规则匹配，命中只替换值。</p>
 *
 * <p>已知残余（刻意不覆盖）：非敏感字符串值内的内嵌文本（逃逸 JSON、URL query）
 * 不再深挖；无 {@code &} 结构的纯散文不走对切分 fallback。</p>
 */
public final class SensitiveValueMasker {

    /** JSON 解析失败时整段替换的固定占位符（不保留猜测原文）。 */
    private static final String UNPARSEABLE_PLACEHOLDER = "[请求体无法安全解析，已省略]";

    private static final String MASKED = "******";

    /** 独立 mapper：尾随垃圾内容禁止静默丢弃（trailing token 一律视为解析失败）。 */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    /** 凭据键匹配（find 语义，大小写不敏感）。 */
    private static final Pattern CREDENTIAL_KEY_FRAGMENTS = Pattern.compile(
            "token|secret|password|passwd|pwd|authorization|cookie|credential|ticket|api[_-]?key|private[_-]?key",
            Pattern.CASE_INSENSITIVE);

    /** 内容抑制键：既有整键精确匹配（大小写不敏感）。 */
    private static final Set<String> CONTENT_KEYS = Set.of("message", "content", "html", "prompt");

    private SensitiveValueMasker() {
    }

    /**
     * JSON → parse-tree 递归（解析失败返回整段占位符）；非 JSON → 有界对切分 fallback；
     * 未命中敏感键原样返回。
     */
    public static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String trimmed = value.stripLeading();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                JsonNode root = JSON_MAPPER.readTree(value);
                boolean changed = root.isObject()
                        ? maskObject((ObjectNode) root)
                        : root.isArray() && maskArray((ArrayNode) root);
                return changed ? JSON_MAPPER.writeValueAsString(root) : value;
            } catch (JsonProcessingException e) {
                return UNPARSEABLE_PLACEHOLDER;
            }
        }
        return maskQueryOrForm(value);
    }

    /** 递归掩码对象字段：敏感键整值替换，非敏感键下钻。返回是否有改动。 */
    private static boolean maskObject(ObjectNode node) {
        boolean changed = false;
        // fieldNames()+get()+put() 跨 Jackson 2.x 全版本稳定；对既有键 put 只换值，迭代安全
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String key = names.next();
            if (isSensitiveKey(key)) {
                node.put(key, MASKED);
                changed = true;
                continue;
            }
            JsonNode child = node.get(key);
            if (child.isObject()) {
                changed |= maskObject((ObjectNode) child);
            } else if (child.isArray()) {
                changed |= maskArray((ArrayNode) child);
            }
        }
        return changed;
    }

    /** 递归掩码数组元素内的对象/子数组。返回是否有改动。 */
    private static boolean maskArray(ArrayNode array) {
        boolean changed = false;
        for (JsonNode element : array) {
            if (element.isObject()) {
                changed |= maskObject((ObjectNode) element);
            } else if (element.isArray()) {
                changed |= maskArray((ArrayNode) element);
            }
        }
        return changed;
    }

    /** 键名判定：内容键整键精确，或含凭据词片段（大小写不敏感）。 */
    private static boolean isSensitiveKey(String key) {
        return CONTENT_KEYS.contains(key.toLowerCase(Locale.ROOT))
                || CREDENTIAL_KEY_FRAGMENTS.matcher(key).find();
    }

    /** 有界对切分 fallback：按 & 切对，键 URL 解码后匹配，命中只换值。 */
    private static String maskQueryOrForm(String value) {
        if (value.indexOf('=') < 0 && value.indexOf(':') < 0) {
            return value;
        }
        String[] pairs = value.split("&", -1);
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < pairs.length; i++) {
            if (i > 0) {
                out.append('&');
            }
            out.append(maskPair(pairs[i]));
        }
        return out.toString();
    }

    /** 单对掩码：首个 = 或 : 切键值；键解码失败回落原文；非敏感键原样。 */
    private static String maskPair(String pair) {
        int split = pair.indexOf('=');
        int colon = pair.indexOf(':');
        if (split < 0 || (colon >= 0 && colon < split)) {
            split = colon;
        }
        if (split <= 0) {
            return pair;
        }
        if (!isSensitiveKey(urlDecode(pair.substring(0, split)))) {
            return pair;
        }
        return pair.substring(0, split + 1) + MASKED;
    }

    /** URL 解码（UTF-8）；畸形 % 序列回落原文，不抛出。 */
    private static String urlDecode(String raw) {
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return raw;
        }
    }
}
