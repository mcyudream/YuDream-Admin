package online.yudream.base.application.platform.ai.service;

import online.yudream.base.domain.common.exception.BizException;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CmsCanvasStyleCoverage {

    private static final Pattern CLASS_ATTRIBUTE = Pattern.compile(
            "\\bclass\\s*=\\s*(['\"])(.*?)\\1",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern CLASS_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]*");
    private static final Pattern LOCKED_CHROME_REGION = Pattern.compile(
            "(?is)<(header|footer)\\b(?=[^>]*\\bdata-yb-chrome\\b)[^>]*>.*?</\\1\\s*>"
    );
    private static final Set<String> SYSTEM_HOME_FRAME_CLASSES = Set.of(
            "site-builder-home", "site-layout-frame", "site-layout-content", "site-admin-sidebar",
            "layout-header-footer", "layout-header-copyright", "layout-admin"
    );

    private CmsCanvasStyleCoverage() {
    }

    static Set<String> htmlClasses(String html) {
        Set<String> classes = new LinkedHashSet<>();
        Matcher attributes = CLASS_ATTRIBUTE.matcher(text(html));
        while (attributes.find()) {
            Matcher names = CLASS_NAME.matcher(attributes.group(2));
            while (names.find()) {
                classes.add(names.group());
            }
        }
        return classes;
    }

    static List<String> uncoveredClasses(String html, String css) {
        String stylesheet = text(css);
        return htmlClasses(html).stream()
                .filter(className -> !containsClassSelector(stylesheet, className))
                .toList();
    }

    static List<String> uncoveredEditableClasses(String html, String css) {
        String editableHtml = LOCKED_CHROME_REGION.matcher(text(html)).replaceAll("");
        String stylesheet = text(css);
        return htmlClasses(editableHtml).stream()
                .filter(className -> !SYSTEM_HOME_FRAME_CLASSES.contains(className))
                .filter(className -> !containsClassSelector(stylesheet, className))
                .toList();
    }

    static void requireComplete(Object html, Object css, String action) {
        String markup = text(html);
        String stylesheet = text(css);
        if (!StringUtils.hasText(markup)) {
            return;
        }
        if (!StringUtils.hasText(stylesheet)) {
            throw new BizException(action + " 必须同时提供 cssContent，禁止生成无样式区块");
        }
        List<String> uncovered = uncoveredClasses(markup, stylesheet);
        if (!uncovered.isEmpty()) {
            throw new BizException(action + " 的 cssContent 未覆盖以下 HTML 类：" + String.join(", ", uncovered));
        }
    }

    private static boolean containsClassSelector(String css, String className) {
        return Pattern.compile("\\." + Pattern.quote(className) + "(?![A-Za-z0-9_-])")
                .matcher(css)
                .find();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
