package online.yudream.base.domain.platform.wiki.enumerate;

import java.util.Locale;

/**
 * 原始资料格式，用于选择解析器与展示方式。
 */
public enum WikiSourceFormat {
    PDF,
    DOCX,
    PPTX,
    XLSX,
    XLS,
    ODS,
    EPUB,
    MOBI,
    ORG,
    MARKDOWN,
    TEXT,
    IMAGE,
    AUDIO,
    VIDEO,
    WEB,
    URL,
    OTHER;

    public static WikiSourceFormat fromFileName(String fileName, String mimeType) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        String mime = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf") || mime.contains("pdf")) {
            return PDF;
        }
        if (name.endsWith(".docx") || mime.contains("docx")) {
            return DOCX;
        }
        if (name.endsWith(".pptx") || mime.contains("pptx")) {
            return PPTX;
        }
        if (name.endsWith(".xlsx") || mime.contains("xlsx")) {
            return XLSX;
        }
        if (name.endsWith(".xls") || mime.contains("vnd.ms-excel")) {
            return XLS;
        }
        if (name.endsWith(".ods") || mime.contains("ods")) {
            return ODS;
        }
        if (name.endsWith(".epub") || mime.contains("epub")) {
            return EPUB;
        }
        if (name.endsWith(".mobi") || mime.contains("mobi")) {
            return MOBI;
        }
        if (name.endsWith(".org")) {
            return ORG;
        }
        if (name.endsWith(".md") || name.endsWith(".markdown") || mime.contains("markdown")) {
            return MARKDOWN;
        }
        if (name.endsWith(".txt") || mime.contains("text/plain")) {
            return TEXT;
        }
        if (mime.startsWith("image/") || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".svg") || name.endsWith(".bmp")) {
            return IMAGE;
        }
        if (mime.startsWith("audio/") || name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".m4a")
                || name.endsWith(".flac") || name.endsWith(".ogg")) {
            return AUDIO;
        }
        if (mime.startsWith("video/") || name.endsWith(".mp4") || name.endsWith(".webm") || name.endsWith(".mov")
                || name.endsWith(".mkv")) {
            return VIDEO;
        }
        if (name.endsWith(".html") || name.endsWith(".htm") || mime.contains("text/html")) {
            return WEB;
        }
        return OTHER;
    }
}
