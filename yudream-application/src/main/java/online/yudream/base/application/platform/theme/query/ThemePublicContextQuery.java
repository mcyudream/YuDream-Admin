package online.yudream.base.application.platform.theme.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 公开主题上下文查询：主题页面（Vue 原生主题页）按需索取数据块与 CMS 最新文章。
 */
@Data
public class ThemePublicContextQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 请求的主题块编码，逗号分隔；空表示不需要块数据。 */
    private String blocks;

    /** 单块数据量上限。 */
    private Integer limit;

    /** CMS 最新文章数量；空或 0 表示不需要。 */
    private Integer cmsLatest;
}
