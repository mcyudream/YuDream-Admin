package online.yudream.base.domain.platform.satori.message;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 不可变的 Satori 消息元素树。原始 Satori 标记只能由编码器生成。
 */
public sealed interface SatoriElement permits SatoriElement.Text, SatoriElement.Node {

    Set<String> STANDARD_ELEMENT_NAMES = Set.of(
            "at", "sharp", "emoji", "a", "img", "audio", "video", "file",
            "strong", "em", "ins", "del", "spl", "code", "sup", "sub", "br", "p",
            "message", "quote", "author", "button"
    );
    Pattern NAMESPACED_NAME = Pattern.compile("[a-z][a-z0-9-]*(?::[a-z][a-z0-9-]*)+");
    Pattern ATTRIBUTE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_.:-]*");

    static Text text(String value) {
        return new Text(value);
    }

    static Node element(String name, Map<String, String> attributes, List<SatoriElement> children) {
        return new Node(name, attributes, children);
    }

    static boolean isValidElementName(String name) {
        return name != null && (STANDARD_ELEMENT_NAMES.contains(name) || NAMESPACED_NAME.matcher(name).matches());
    }

    static boolean isValidAttributeName(String name) {
        return name != null && ATTRIBUTE_NAME.matcher(name).matches();
    }

    record Text(String value) implements SatoriElement {
        public Text {
            Objects.requireNonNull(value, "文本内容不能为空");
        }
    }

    record Node(String name, Map<String, String> attributes, List<SatoriElement> children) implements SatoriElement {
        public Node {
            if (!isValidElementName(name)) {
                throw new IllegalArgumentException("非法 Satori 元素名称: " + name);
            }
            attributes = immutableAttributes(attributes);
            children = children == null ? List.of() : List.copyOf(children);
        }

        private static Map<String, String> immutableAttributes(Map<String, String> source) {
            if (source == null || source.isEmpty()) {
                return Map.of();
            }
            Map<String, String> copy = new LinkedHashMap<>();
            source.forEach((key, value) -> {
                if (!isValidAttributeName(key)) {
                    throw new IllegalArgumentException("非法 Satori 属性名称: " + key);
                }
                copy.put(key, Objects.requireNonNull(value, "Satori 属性值不能为空"));
            });
            return Collections.unmodifiableMap(copy);
        }
    }
}
