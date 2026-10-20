package online.yudream.base.domain.platform.cms.aggregate;

import online.yudream.base.domain.common.exception.BizException;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 网站工作台用户区块模板：站点内共享的可复用节点片段（节点树 JSON），插入时由前端重建全部节点 ID。
 * 节点内容的安全校验由应用层按站点文档规则执行；聚合只守护元数据与版本。
 */
public record StudioBlockTemplate(
        String id,
        String name,
        String category,
        String description,
        List<String> keywords,
        String nodesJson,
        long revision,
        Long createdBy,
        Instant createdAt,
        Instant updatedAt) {

    public static final Set<String> CATEGORIES = Set.of("text", "media", "design", "layout", "widgets", "custom");
    public static final int MAX_KEYWORDS = 10;

    public StudioBlockTemplate {
        if (id == null || !id.matches("[a-f0-9-]{36}")) throw new BizException("区块模板 ID 无效");
        name = requireName(name);
        category = requireCategory(category);
        description = description == null ? "" : description.trim();
        if (description.length() > 200) throw new BizException("区块模板说明不能超过 200 字");
        keywords = normalizeKeywords(keywords);
        if (nodesJson == null || nodesJson.isBlank()) throw new BizException("区块模板内容不能为空");
        if (revision < 1) throw new BizException("区块模板版本无效");
    }

    public static StudioBlockTemplate create(String name, String category, String description, List<String> keywords,
                                             String nodesJson, Long createdBy, Instant now) {
        return new StudioBlockTemplate(UUID.randomUUID().toString(), name, category, description, keywords, nodesJson, 1, createdBy, now, now);
    }

    /** 按预期版本更新元数据与可选内容；版本不一致拒绝，不自动合并。 */
    public StudioBlockTemplate update(long expectedRevision, String name, String category, String description,
                                      List<String> keywords, String nodesJson, Instant now) {
        if (expectedRevision != revision) throw new BizException(409, "区块模板已被他人修改，请刷新后重试");
        return new StudioBlockTemplate(id, name, category, description, keywords, nodesJson == null ? this.nodesJson : nodesJson,
                revision + 1, createdBy, createdAt, now);
    }

    private static String requireName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty() || name.length() > 60) throw new BizException("区块模板名称需为 1 至 60 字");
        return name;
    }

    private static String requireCategory(String value) {
        String category = value == null || value.isBlank() ? "custom" : value.trim();
        if (!CATEGORIES.contains(category)) throw new BizException("区块模板分类无效");
        return category;
    }

    private static List<String> normalizeKeywords(List<String> values) {
        if (values == null) return List.of();
        List<String> keywords = values.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).distinct().toList();
        if (keywords.size() > MAX_KEYWORDS || keywords.stream().anyMatch(word -> word.length() > 20)) {
            throw new BizException("关键词最多 10 个，每个不超过 20 字");
        }
        return keywords;
    }
}
