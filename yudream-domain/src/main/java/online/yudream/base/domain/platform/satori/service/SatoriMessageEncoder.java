package online.yudream.base.domain.platform.satori.service;

import online.yudream.base.domain.platform.satori.message.SatoriElement;
import online.yudream.base.domain.platform.satori.message.SatoriMessage;

import java.util.Map;
import java.util.Objects;

/**
 * 将受校验的消息 AST 编码为 Satori 元素字符串。
 */
public final class SatoriMessageEncoder {

    public String encode(SatoriMessage message) {
        Objects.requireNonNull(message, "Satori 消息不能为空");
        StringBuilder encoded = new StringBuilder();
        message.elements().forEach(element -> append(encoded, element));
        return encoded.toString();
    }

    private void append(StringBuilder target, SatoriElement element) {
        if (element instanceof SatoriElement.Text text) {
            target.append(escapeText(text.value()));
            return;
        }
        SatoriElement.Node node = (SatoriElement.Node) element;
        if (!SatoriElement.isValidElementName(node.name())) {
            throw new IllegalArgumentException("非法 Satori 元素名称: " + node.name());
        }
        target.append('<').append(node.name());
        for (Map.Entry<String, String> attribute : node.attributes().entrySet()) {
            if (!SatoriElement.isValidAttributeName(attribute.getKey())) {
                throw new IllegalArgumentException("非法 Satori 属性名称: " + attribute.getKey());
            }
            target.append(' ').append(attribute.getKey()).append("=\"")
                    .append(escapeAttribute(attribute.getValue())).append('\"');
        }
        if (node.children().isEmpty()) {
            target.append("/>");
            return;
        }
        target.append('>');
        node.children().forEach(child -> append(target, child));
        target.append("</").append(node.name()).append('>');
    }

    private String escapeText(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String escapeAttribute(String value) {
        return escapeText(value).replace("\"", "&quot;").replace("'", "&apos;");
    }
}
