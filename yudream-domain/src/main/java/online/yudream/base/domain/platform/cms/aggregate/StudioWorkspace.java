package online.yudream.base.domain.platform.cms.aggregate;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.cms.valobj.StudioRelease;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 每主题独立聚合；草稿、不可变快照和线上指针同次CAS持久化。 */
public record StudioWorkspace(String themeCode, long revision, String documentJson,
        String publishedReleaseId, Instant updatedAt, List<StudioRelease> releases) {
    public static final int MAX_RELEASES = 20;
    public static final int MAX_STORAGE_BYTES = 10 * 1024 * 1024;

    public StudioWorkspace {
        releases = List.copyOf(releases);
    }
    public static StudioWorkspace empty(String themeCode) {
        if (themeCode == null || !themeCode.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}")) {
            throw new BizException("主题编码不合法");
        }
        return new StudioWorkspace(themeCode, 0, null, null, null, List.of());
    }
    public void checkRevision(String expectedRevision) {
        if (!Long.toString(revision).equals(expectedRevision)) {
            throw new BizException(409, "站点草稿版本冲突，请刷新后重新确认修改");
        }
    }
    public StudioWorkspace saveDraft(String expectedRevision, String document, Instant now) {
        checkRevision(expectedRevision);
        return new StudioWorkspace(themeCode, nextRevision(), document, publishedReleaseId, now,
                trim(releases, document, publishedReleaseId));
    }
    public StudioWorkspace publish(String expectedRevision, String releaseId, Instant now) {
        checkRevision(expectedRevision);
        if (documentJson == null) throw new BizException("请先保存站点草稿");
        var snapshots = new ArrayList<StudioRelease>();
        snapshots.add(new StudioRelease(releaseId, nextRevision(), documentJson, now));
        snapshots.addAll(releases);
        return new StudioWorkspace(themeCode, nextRevision(), documentJson, releaseId, now,
                trim(snapshots, documentJson, releaseId));
    }
    public StudioWorkspace unpublish(String expectedRevision, Instant now) {
        checkRevision(expectedRevision);
        return new StudioWorkspace(themeCode, nextRevision(), documentJson, null, now, releases);
    }
    public StudioWorkspace restore(String expectedRevision, String releaseId, Instant now) {
        checkRevision(expectedRevision);
        var release = releases.stream().filter(item -> item.id().equals(releaseId)).findFirst()
                .orElseThrow(() -> new BizException("发布版本不存在或已超出保留范围"));
        return saveDraft(expectedRevision, release.documentJson(), now);
    }
    public StudioRelease published() {
        return releases.stream().filter(item -> item.id().equals(publishedReleaseId)).findFirst().orElse(null);
    }
    private long nextRevision() {
        if (revision == Long.MAX_VALUE) throw new BizException("站点版本已达到上限");
        return revision + 1;
    }
    private static List<StudioRelease> trim(List<StudioRelease> snapshots, String draft, String publishedId) {
        var retained = new ArrayList<>(snapshots);
        while (retained.size() > MAX_RELEASES || bytes(draft) + retained.stream()
                .mapToLong(item -> bytes(item.documentJson())).sum() > MAX_STORAGE_BYTES) {
            int remove = retained.size() - 1;
            while (remove >= 0 && retained.get(remove).id().equals(publishedId)) remove--;
            if (remove < 0) throw new BizException("站点快照存储预算不足");
            retained.remove(remove);
        }
        return retained;
    }
    private static int bytes(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }
}
