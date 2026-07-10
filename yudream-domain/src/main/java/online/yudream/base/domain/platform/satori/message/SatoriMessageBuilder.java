package online.yudream.base.domain.platform.satori.message;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Satori 标准元素的安全构造器。
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
        elements.add(SatoriElement.element(name, attributes, children));
        return this;
    }

    public SatoriMessageBuilder at(String id, String name) {
        return element("at", attributes("id", id, "name", name), List.of());
    }

    public SatoriMessageBuilder sharp(String id, String name) {
        return element("sharp", attributes("id", id, "name", name), List.of());
    }

    public SatoriMessageBuilder emoji(String id, String name) {
        return element("emoji", attributes("id", id, "name", name), List.of());
    }

    public SatoriMessageBuilder a(String href, List<SatoriElement> children) {
        return element("a", attributes("href", href), children);
    }

    public SatoriMessageBuilder img(String src) {
        return img(src, null);
    }

    public SatoriMessageBuilder img(String src, String title) {
        return element("img", attributes("src", src, "title", title), List.of());
    }

    public SatoriMessageBuilder audio(String src) {
        return element("audio", attributes("src", src), List.of());
    }

    public SatoriMessageBuilder video(String src) {
        return element("video", attributes("src", src), List.of());
    }

    public SatoriMessageBuilder file(String src, String title) {
        return element("file", attributes("src", src, "title", title), List.of());
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
        return element("message", attributes("id", id), children);
    }

    public SatoriMessageBuilder quote(String id, List<SatoriElement> children) {
        return element("quote", attributes("id", id), children);
    }

    public SatoriMessageBuilder author(String id, String name, List<SatoriElement> children) {
        return element("author", attributes("id", id, "name", name), children);
    }

    public SatoriMessageBuilder button(String id, String title, List<SatoriElement> children) {
        return element("button", attributes("id", id, "title", title), children);
    }

    public SatoriMessage build() {
        return new SatoriMessage(elements);
    }

    private static Map<String, String> attributes(String... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("Satori 属性必须成对提供");
        }
        Map<String, String> attributes = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            String key = Objects.requireNonNull(pairs[index], "Satori 属性名不能为空");
            String value = pairs[index + 1];
            if (value != null) {
                attributes.put(key, value);
            }
        }
        return attributes;
    }
}
