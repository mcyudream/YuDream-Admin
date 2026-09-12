package online.yudream.base.domain.platform.plugin.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.plugin.enumerate.PluginPublicationChannel;
import online.yudream.base.domain.platform.plugin.enumerate.PluginPublicationStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 自托管市场源的插件发布物。{code}@{pluginVersion} 不可覆盖；状态机 PENDING → PUBLISHED/REJECTED，
 * PUBLISHED → REVOKED（下架后 JAR 保留备查但不再对外下发）。pluginVersion 是插件语义版本，
 * 与 BaseDomain 的乐观锁 version 无关。category/tags 是 v3 社区元数据（plugin.yml 不携带），
 * 供 v2 协议检索；compatibilityJson/publisherJson 保存发布时的元数据原文供编辑后重生成 descriptor。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PluginMarketPublication extends BaseDomain {

    private String code;
    private String pluginVersion;
    private String displayName;
    private String description;
    private String mainClass;
    private List<String> dependencies;
    private List<String> softDependencies;
    private String icon;
    private String releaseNotes;
    private String license;
    private String category;
    private List<String> tags;
    private String compatibilityJson;
    private String publisherJson;
    private String descriptorJson;
    /** 相对市场目录的 JAR 存储路径（{code}/{version}/plugin.jar）。 */
    private String jarPath;
    private String sha256;
    private Long sizeBytes;
    private Long downloadCount;
    private Long publisherUserId;
    private PluginPublicationChannel channel;
    private PluginPublicationStatus status;
    private String reviewNote;
    private Long reviewerUserId;
    private LocalDateTime reviewedAt;

    public boolean published() {
        return status == PluginPublicationStatus.PUBLISHED;
    }

    public void accept(Long reviewerId, String note) {
        requireStatus(PluginPublicationStatus.PENDING, "只有待审核的发布物可以通过");
        this.status = PluginPublicationStatus.PUBLISHED;
        applyReview(reviewerId, note);
    }

    public void reject(Long reviewerId, String note) {
        requireStatus(PluginPublicationStatus.PENDING, "只有待审核的发布物可以拒绝");
        this.status = PluginPublicationStatus.REJECTED;
        applyReview(reviewerId, note);
    }

    public void revoke(Long reviewerId, String note) {
        requireStatus(PluginPublicationStatus.PUBLISHED, "只有已发布的版本可以下架");
        this.status = PluginPublicationStatus.REVOKED;
        applyReview(reviewerId, note);
    }

    /** 编辑展示元数据（不改 code/version/JAR，不重置审核状态），返回是否发生了变更。 */
    public boolean updateDisplayInfo(String displayName, String description, String releaseNotes,
                                     String license, String category, List<String> tags,
                                     String compatibilityJson) {
        boolean changed = false;
        if (displayName != null && !displayName.equals(this.displayName)) {
            this.displayName = displayName;
            changed = true;
        }
        if (description != null && !description.equals(this.description)) {
            this.description = description;
            changed = true;
        }
        if (releaseNotes != null && !releaseNotes.equals(this.releaseNotes)) {
            this.releaseNotes = releaseNotes;
            changed = true;
        }
        if (license != null && !license.equals(this.license)) {
            this.license = license;
            changed = true;
        }
        if (category != null && !category.equals(this.category)) {
            this.category = category;
            changed = true;
        }
        if (tags != null && !tags.equals(this.tags)) {
            this.tags = tags;
            changed = true;
        }
        if (compatibilityJson != null && !compatibilityJson.equals(this.compatibilityJson)) {
            this.compatibilityJson = compatibilityJson;
            changed = true;
        }
        return changed;
    }

    private void applyReview(Long reviewerId, String note) {
        this.reviewerUserId = reviewerId;
        this.reviewNote = note;
        this.reviewedAt = LocalDateTime.now();
    }

    private void requireStatus(PluginPublicationStatus expected, String message) {
        if (status != expected) {
            throw new BizException(message);
        }
    }
}
