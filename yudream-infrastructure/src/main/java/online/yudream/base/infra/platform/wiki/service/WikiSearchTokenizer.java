package online.yudream.base.infra.platform.wiki.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 检索分词：按空白/标点切分，CJK 单字作为词元，供关键词检索做“任一命中”匹配。
 */
public final class WikiSearchTokenizer {

    private WikiSearchTokenizer() {
    }

    public static List<String> tokenize(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        Set<String> terms = new LinkedHashSet<>();
        StringBuilder current = new StringBuilder();
        for (char c : query.toCharArray()) {
            if (Character.isWhitespace(c) || isPunctuation(c)) {
                flush(current, terms);
            }
            else if (isCjk(c)) {
                flush(current, terms);
                terms.add(String.valueOf(c));
            }
            else {
                current.append(c);
            }
        }
        flush(current, terms);
        return terms.stream().filter(term -> !term.isBlank()).toList();
    }

    private static void flush(StringBuilder current, Set<String> terms) {
        if (current.length() > 0) {
            terms.add(current.toString());
            current.setLength(0);
        }
    }

    private static boolean isCjk(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A;
    }

    private static boolean isPunctuation(char c) {
        return !Character.isLetterOrDigit(c) && !isCjk(c);
    }
}
