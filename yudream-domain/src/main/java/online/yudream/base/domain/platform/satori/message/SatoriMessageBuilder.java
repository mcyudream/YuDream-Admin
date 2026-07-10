package online.yudream.base.domain.platform.satori.message;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Safe builder for standard Satori message elements.
 */
public final class SatoriMessageBuilder {

    private final List<SatoriElement> elements = new ArrayList<>();

    private SatoriMessageBuilder() {
    }

    public static SatoriMessageBuilder create() {
        return new SatoriMessageBuilder();
    }

    public SatoriMessageBuilder text(String value) {
        elements.add(SatoriElement.text(value));
        return this;
    }

    public SatoriMessageBuilder element(String name, Map<String, String> attributes, List<SatoriElement> children) {
        validateStandardElementRequiredAttributes(name, attributes);
        elements.add(SatoriElement.element(name, attributes, children));
        return this;
    }

    public SatoriMessageBuilder at(String id, String name) {
        return element("at", optionalAttributes("id", id, "name", name), List.of());
    }

    public SatoriMessageBuilder sharp(String id, String name) {
        return element("sharp", requiredAndOptionalAttributes("id", id, "name", name), List.of());
    }

    public SatoriMessageBuilder emoji(String id, String name) {
        return element("emoji", optionalAttributes("id", id, "name", name), List.of());
    }

    public SatoriMessageBuilder a(String href, List<SatoriElement> children) {
        return element("a", requiredAttributes("href", href), children);
    }

    public SatoriMessageBuilder img(String src) {
        return img(src, null);
    }

    public SatoriMessageBuilder img(String src, String title) {
        return element("img", requiredAndOptionalAttributes("src", src, "title", title), List.of());
    }

    public SatoriMessageBuilder audio(String src) {
        return element("audio", requiredAttributes("src", src), List.of());
    }

    public SatoriMessageBuilder video(String src) {
        return element("video", requiredAttributes("src", src), List.of());
    }

    public SatoriMessageBuilder file(String src, String title) {
        return element("file", requiredAndOptionalAttributes("src", src, "title", title), List.of());
    }

    public SatoriMessageBuilder strong(List<SatoriElement> children) {
        return element("strong", Map.of(), children);
    }

    public SatoriMessageBuilder em(List<SatoriElement> children) {
        return element("em", Map.of(), children);
    }

    public SatoriMessageBuilder ins(List<SatoriElement> children) {
        return element("ins", Map.of(), children);
    }

    public SatoriMessageBuilder del(List<SatoriElement> children) {
        return element("del", Map.of(), children);
    }

    public SatoriMessageBuilder spl(List<SatoriElement> children) {
        return element("spl", Map.of(), children);
    }

    public SatoriMessageBuilder code(List<SatoriElement> children) {
        return element("code", Map.of(), children);
    }

    public SatoriMessageBuilder sup(List<SatoriElement> children) {
        return element("sup", Map.of(), children);
    }

    public SatoriMessageBuilder sub(List<SatoriElement> children) {
        return element("sub", Map.of(), children);
    }

    public SatoriMessageBuilder br() {
        return element("br", Map.of(), List.of());
    }

    public SatoriMessageBuilder p(List<SatoriElement> children) {
        return element("p", Map.of(), children);
    }

    public SatoriMessageBuilder message(String id, List<SatoriElement> children) {
        return element("message", optionalAttributes("id", id), children);
    }

    public SatoriMessageBuilder quote(String id, List<SatoriElement> children) {
        return element("quote", optionalAttributes("id", id), children);
    }

    public SatoriMessageBuilder author(String id, String name, List<SatoriElement> children) {
        return element("author", optionalAttributes("id", id, "name", name), children);
    }

    public SatoriMessageBuilder button(String id, String title, List<SatoriElement> children) {
        return element("button", optionalAttributes("id", id, "title", title), children);
    }

    public SatoriMessageBuilder buttonAction(String id, String theme, List<SatoriElement> children) {
        return element("button", buttonAttributes("id", id, theme), children);
    }

    public SatoriMessageBuilder buttonLink(String href, String theme, List<SatoriElement> children) {
        return element("button", buttonAttributes("href", href, theme, "link"), children);
    }

    public SatoriMessageBuilder buttonInput(String text, String theme, List<SatoriElement> children) {
        return element("button", buttonAttributes("text", text, theme, "input"), children);
    }

    public SatoriMessage build() {
        return new SatoriMessage(elements);
    }

    private static Map<String, String> requiredAttributes(String... pairs) {
        Map<String, String> attributes = new LinkedHashMap<>();
        addRequiredAttributes(attributes, pairs);
        return attributes;
    }

    private static Map<String, String> optionalAttributes(String... pairs) {
        Map<String, String> attributes = new LinkedHashMap<>();
        addOptionalAttributes(attributes, pairs);
        return attributes;
    }

    private static Map<String, String> requiredAndOptionalAttributes(
            String requiredName,
            String requiredValue,
            String... optionalPairs
    ) {
        Map<String, String> attributes = new LinkedHashMap<>();
        addRequiredAttributes(attributes, requiredName, requiredValue);
        addOptionalAttributes(attributes, optionalPairs);
        return attributes;
    }

    private static Map<String, String> buttonAttributes(String valueName, String value, String theme) {
        return buttonAttributes(valueName, value, theme, "action");
    }

    private static Map<String, String> buttonAttributes(String valueName, String value, String theme, String type) {
        Map<String, String> attributes = requiredAttributes("type", type, valueName, value);
        addOptionalAttributes(attributes, "theme", theme);
        return attributes;
    }

    private static void addRequiredAttributes(Map<String, String> attributes, String... pairs) {
        validatePairs(pairs);
        for (int index = 0; index < pairs.length; index += 2) {
            String key = requireAttributeName(pairs[index]);
            String value = pairs[index + 1];
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Satori required attribute must not be blank: " + key);
            }
            attributes.put(key, value);
        }
    }

    private static void addOptionalAttributes(Map<String, String> attributes, String... pairs) {
        validatePairs(pairs);
        for (int index = 0; index < pairs.length; index += 2) {
            String key = requireAttributeName(pairs[index]);
            String value = pairs[index + 1];
            if (value == null) {
                continue;
            }
            if (value.isBlank()) {
                throw new IllegalArgumentException("Satori optional attribute must not be blank: " + key);
            }
            attributes.put(key, value);
        }
    }

    private static void validatePairs(String... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("Satori attributes must be supplied in pairs");
        }
    }

    private static void validateStandardElementRequiredAttributes(String name, Map<String, String> attributes) {
        if (attributes == null) {
            attributes = Map.of();
        }
        switch (name) {
            case "sharp" -> requireAttribute(attributes, "id");
            case "a" -> requireAttribute(attributes, "href");
            case "img", "audio", "video", "file" -> requireAttribute(attributes, "src");
            case "button" -> validateButtonAttributes(attributes);
            default -> {
                // The remaining standard elements have no required attributes.
            }
        }
    }

    private static void validateButtonAttributes(Map<String, String> attributes) {
        String type = attributes.get("type");
        if (type == null) {
            return;
        }
        if (type.isBlank()) {
            throw new IllegalArgumentException("Satori button type must not be blank");
        }
        switch (type) {
            case "action" -> requireAttribute(attributes, "id");
            case "link" -> requireAttribute(attributes, "href");
            case "input" -> requireAttribute(attributes, "text");
            default -> throw new IllegalArgumentException("Unsupported Satori button type: " + type);
        }
    }

    private static void requireAttribute(Map<String, String> attributes, String name) {
        String value = attributes.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Satori required attribute must not be blank: " + name);
        }
    }

    private static String requireAttributeName(String value) {
        String name = Objects.requireNonNull(value, "Satori attribute name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Satori attribute name must not be blank");
        }
        return name;
    }
}
