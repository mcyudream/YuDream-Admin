package online.yudream.base.domain.platform.wiki.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.enumerate.WikiExtractionStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiIngestStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSourceFormat;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSourceKind;
import online.yudream.base.domain.platform.wiki.valobj.WikiSourceImage;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 原始资料（不可变内容，只读来源）。
 * <p>
 * 对应 llm_wiki 的 raw/sources 层：LLM 读取原始资料，但不修改它们。变更以 contentHash
 * 标识，未变更时跳过重复摄入。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WikiSource extends BaseDomain {

    private Long spaceId;
    private String folderPath;
    private String fileName;
    private String title;
    private WikiSourceKind kind;
    private String url;
    private String mimeType;
    private WikiSourceFormat format;
    private Long fileObjectId;
    private String contentHash;
    private String extractedText;
    private WikiExtractionStatus extractionStatus;
    private String extractionError;
    private List<WikiSourceImage> images;
    private WikiIngestStatus ingestStatus;
    private String ingestHash;
    private String ingestError;
    private LocalDateTime ingestedAt;
    private int sort;

    public static WikiSource file(Long spaceId, String folderPath, String fileName, String title,
                                  String mimeType, Long fileObjectId, String contentHash) {
        if (spaceId == null) {
            throw new BizException("知识库不能为空");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new BizException("资料文件名不能为空");
        }
        return WikiSource.builder()
                .spaceId(spaceId)
                .folderPath(normalizeFolder(folderPath))
                .fileName(fileName.trim())
                .title(title == null || title.isBlank() ? fileName.trim() : title.trim())
                .kind(WikiSourceKind.FILE)
                .mimeType(text(mimeType))
                .format(WikiSourceFormat.fromFileName(fileName, mimeType))
                .fileObjectId(fileObjectId)
                .contentHash(contentHash)
                .extractedText("")
                .extractionStatus(WikiExtractionStatus.PENDING)
                .images(new ArrayList<>())
                .ingestStatus(WikiIngestStatus.PENDING)
                .sort(0)
                .build();
    }

    public static WikiSource url(Long spaceId, String folderPath, String title, String url, String mimeType) {
        if (spaceId == null) {
            throw new BizException("知识库不能为空");
        }
        if (url == null || url.isBlank()) {
            throw new BizException("资料 URL 不能为空");
        }
        String safeTitle = title == null || title.isBlank() ? url.trim() : title.trim();
        return WikiSource.builder()
                .spaceId(spaceId)
                .folderPath(normalizeFolder(folderPath))
                .fileName("")
                .title(safeTitle)
                .kind(WikiSourceKind.URL)
                .url(url.trim())
                .mimeType(text(mimeType))
                .format(WikiSourceFormat.URL)
                .contentHash("")
                .extractedText("")
                .extractionStatus(WikiExtractionStatus.PENDING)
                .images(new ArrayList<>())
                .ingestStatus(WikiIngestStatus.PENDING)
                .sort(0)
                .build();
    }

    public static WikiSource text(Long spaceId, String folderPath, String title, String markdown, String contentHash) {
        if (spaceId == null) {
            throw new BizException("知识库不能为空");
        }
        if (title == null || title.isBlank()) {
            throw new BizException("资料标题不能为空");
        }
        return WikiSource.builder()
                .spaceId(spaceId)
                .folderPath(normalizeFolder(folderPath))
                .fileName("")
                .title(title.trim())
                .kind(WikiSourceKind.TEXT)
                .mimeType("text/markdown")
                .format(WikiSourceFormat.MARKDOWN)
                .contentHash(contentHash == null ? "" : contentHash)
                .extractedText(markdown == null ? "" : markdown)
                .extractionStatus(WikiExtractionStatus.EXTRACTED)
                .images(new ArrayList<>())
                .ingestStatus(WikiIngestStatus.PENDING)
                .sort(0)
                .build();
    }

    /** 在线文本资料更新内容；内容哈希随之变化，触发重新摄入。 */
    public void updateText(String title, String markdown, String contentHash) {
        if (kind != WikiSourceKind.TEXT) {
            throw new BizException("仅在线文本资料支持编辑");
        }
        if (title == null || title.isBlank()) {
            throw new BizException("资料标题不能为空");
        }
        this.title = title.trim();
        this.extractedText = markdown == null ? "" : markdown;
        this.contentHash = contentHash == null ? "" : contentHash;
        this.extractionStatus = WikiExtractionStatus.EXTRACTED;
        this.extractionError = null;
        markIngestPending();
    }

    public void markExtracted(String text, List<WikiSourceImage> extractedImages) {
        this.extractedText = text == null ? "" : text;
        this.images = extractedImages == null ? new ArrayList<>() : new ArrayList<>(extractedImages);
        this.extractionStatus = WikiExtractionStatus.EXTRACTED;
        this.extractionError = null;
    }

    public void failExtraction(String error) {
        this.extractionStatus = WikiExtractionStatus.FAILED;
        this.extractionError = error == null || error.isBlank() ? "资料解析失败" : error;
    }

    public void skipExtraction(String reason) {
        this.extractionStatus = WikiExtractionStatus.SKIPPED;
        this.extractionError = reason;
    }

    public void markIngested(String hash) {
        this.ingestHash = hash;
        this.ingestStatus = WikiIngestStatus.INGESTED;
        this.ingestError = null;
        this.ingestedAt = LocalDateTime.now();
    }

    public void markIngestPending() {
        this.ingestStatus = WikiIngestStatus.PENDING;
        this.ingestError = null;
    }

    public void failIngest(String error) {
        this.ingestStatus = WikiIngestStatus.FAILED;
        this.ingestError = error == null || error.isBlank() ? "摄入失败" : error;
    }

    public void skipIngest(String reason) {
        this.ingestStatus = WikiIngestStatus.SKIPPED;
        this.ingestError = reason;
    }

    public boolean isUnchangedSinceIngest(String hash) {
        return ingestStatus == WikiIngestStatus.INGESTED && ingestHash != null && ingestHash.equals(hash);
    }

    public String displayPath() {
        String folder = normalizeFolder(folderPath);
        String name = fileName == null || fileName.isBlank() ? title : fileName;
        return ("/".equals(folder) ? "" : folder) + "/" + name;
    }

    private static String normalizeFolder(String folderPath) {
        String normalized = folderPath == null || folderPath.isBlank() ? "/" : folderPath.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
