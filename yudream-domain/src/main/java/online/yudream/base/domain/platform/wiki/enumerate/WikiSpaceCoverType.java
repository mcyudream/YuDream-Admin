package online.yudream.base.domain.platform.wiki.enumerate;

/**
 * 知识库封面来源。缺省（含旧数据）视为 {@link #NONE}，保持无封面行为。
 */
public enum WikiSpaceCoverType {
    /** 无封面。 */
    NONE,
    /** 由稳定 seed 确定性呈现的随机封面（同一 seed 每次渲染结果一致）。 */
    SEEDED,
    /** 上传到系统文件对象的封面图片（必须归属当前用户且公开可读）。 */
    UPLOAD;

    /** 空值一律回落 NONE。 */
    public static WikiSpaceCoverType of(WikiSpaceCoverType type) {
        return type == null ? NONE : type;
    }
}
