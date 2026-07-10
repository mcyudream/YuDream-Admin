package online.yudream.base.application.platform.satori.service;

import online.yudream.base.domain.platform.satori.message.SatoriElement;
import online.yudream.base.domain.platform.satori.message.SatoriMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A deliberately small, safe Markdown subset mapped to Satori standard elements. */
public final class MarkdownToSatoriConverter {
    private static final Pattern IMAGE = Pattern.compile("!\\[([^]]*)]\\(([^ )]+)(?: \\\"[^\\\"]*\\\")?\\)");
    private static final Pattern LINK = Pattern.compile("\\[([^]]+)]\\(([^ )]+)\\)");

    public SatoriMessage convert(String markdown) {
        if (markdown == null || markdown.isEmpty()) return SatoriMessage.empty();
        List<SatoriElement> elements = new ArrayList<>();
        String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (String line : lines) {
            if (line.isBlank()) continue;
            String plain = line.replaceFirst("^#{1,6}\\s+", "");
            elements.add(SatoriElement.element("p", Map.of(), inline(plain)));
        }
        return new SatoriMessage(elements);
    }

    private List<SatoriElement> inline(String value) {
        List<SatoriElement> result = new ArrayList<>();
        Matcher imageMatcher = IMAGE.matcher(value);
        int offset = 0;
        while (imageMatcher.find()) {
            addText(result, value.substring(offset, imageMatcher.start()));
            result.add(SatoriElement.element("img", Map.of("src", imageMatcher.group(2), "title", imageMatcher.group(1)), List.of()));
            offset = imageMatcher.end();
        }
        addLinks(result, value.substring(offset));
        return result;
    }

    private void addLinks(List<SatoriElement> result, String value) {
        Matcher matcher = LINK.matcher(value);
        int offset = 0;
        while (matcher.find()) {
            addText(result, value.substring(offset, matcher.start()));
            result.add(SatoriElement.element("a", Map.of("href", matcher.group(2)), List.of(SatoriElement.text(matcher.group(1)))));
            offset = matcher.end();
        }
        addText(result, value.substring(offset));
    }

    private void addText(List<SatoriElement> result, String value) {
        if (!value.isEmpty()) result.add(SatoriElement.text(value));
    }
}
