package online.yudream.base.domain.platform.wiki.valobj;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.enumerate.WikiTextAnchorState;

import java.util.Objects;

/**
 * Wiki 页面评论的文本锚点值对象。
 * <p>
 * 锚点描述「评论引用已发布页面正文的哪一段文本」，采用 W3C TextQuoteSelector 的
 * exact/prefix/suffix 模型：引用文本按空白折叠归一化后，必须能在当前已发布正文中
 * 唯一定位（命中多处时用上下文前缀/后缀消歧；仍不唯一则拒绝落库）。
 * <p>
 * 版本信息（{@code versionId}/{@code revision}/{@code contentHash}）只能由服务端在
 * 「当前已发布版本」上解析后盖章：客户端只能提交引用文本与定位提示（块提示、起止偏移），
 * 任何时候都不接受客户端传入的版本、哈希或空间/作者身份。
 * <p>
 * 锚点只存评论集合，不写入 {@code WikiFrontmatter}；读取侧用
 * {@link #stateAgainst(String, String)} 计算 {@link WikiTextAnchorState}。
 */
public record WikiTextAnchor(
        String exact,
        String prefix,
        String suffix,
        WikiTextBlockHint blockHint,
        Integer startOffset,
        Integer endOffset,
        Long versionId,
        long revision,
        String contentHash
) {

    /** 引用文本长度上限（归一化空白后）。 */
    public static final int MAX_EXACT_LENGTH = 512;
    /** 上下文前缀/后缀长度上限（各自，归一化空白后）。 */
    public static final int MAX_CONTEXT_LENGTH = 64;

    public WikiTextAnchor {
        exact = normalizeWhitespace(exact);
        prefix = normalizeWhitespace(prefix);
        suffix = normalizeWhitespace(suffix);
        if (exact.isEmpty()) {
            throw new BizException("引用文本不能为空");
        }
        if (exact.length() > MAX_EXACT_LENGTH) {
            throw new BizException("引用文本不能超过 " + MAX_EXACT_LENGTH + " 个字符");
        }
        if (prefix.length() > MAX_CONTEXT_LENGTH) {
            throw new BizException("引用前缀不能超过 " + MAX_CONTEXT_LENGTH + " 个字符");
        }
        if (suffix.length() > MAX_CONTEXT_LENGTH) {
            throw new BizException("引用后缀不能超过 " + MAX_CONTEXT_LENGTH + " 个字符");
        }
        if (startOffset != null && startOffset < 0 || endOffset != null && endOffset < 0) {
            throw new BizException("锚点偏移提示不合法");
        }
        if (startOffset != null && endOffset != null && startOffset > endOffset) {
            throw new BizException("锚点起止偏移不合法");
        }
        if (versionId == null) {
            if (revision != 0L || (contentHash != null && !contentHash.isBlank())) {
                throw new BizException("锚点未定版时不能携带版本信息");
            }
        }
        else if (revision <= 0L || contentHash == null || contentHash.isBlank()) {
            throw new BizException("锚点缺少已发布版本信息");
        }
    }

    /** 客户端草稿：只有引用文本与定位提示，版本信息由服务端盖章。 */
    public static WikiTextAnchor draft(String exact, String prefix, String suffix, WikiTextBlockHint blockHint,
                                       Integer startOffset, Integer endOffset) {
        return new WikiTextAnchor(exact, prefix, suffix, blockHint, startOffset, endOffset, null, 0L, null);
    }

    /**
     * 服务端定锚：按当前已发布正文解析引用并盖章。
     * <p>
     * 引用文本无法唯一解析（缺失或多处命中且上下文无法消歧）时拒绝；
     * 未盖章草稿不允许直接落库。
     */
    public static WikiTextAnchor stamp(WikiTextAnchor draft, Long versionId, long revision, String contentHash,
                                       String publishedContent) {
        if (draft == null) {
            throw new BizException("锚点不能为空");
        }
        if (versionId == null || revision <= 0L || contentHash == null || contentHash.isBlank()) {
            throw new BizException("缺少已发布版本信息，无法定锚");
        }
        if (!draft.resolvableIn(publishedContent)) {
            throw new BizException("引用文本无法在当前已发布版本中唯一定位，请重新选择或补充上下文");
        }
        return new WikiTextAnchor(draft.exact(), draft.prefix(), draft.suffix(), draft.blockHint(),
                draft.startOffset(), draft.endOffset(), versionId, revision, contentHash);
    }

    /** 已盖章锚点能否在给定正文中按上下文精确唯一命中；未盖章草稿一律不可定位。 */
    public boolean uniquelyResolvableIn(String content) {
        return versionId != null && matchCount(normalizeWhitespace(content)) == 1;
    }

    /**
     * 公开读取时的锚点状态：只有「唯一命中」才会被判定为可展示状态。
     * <p>
     * 唯一命中且内容哈希与盖章一致为 {@link WikiTextAnchorState#ACTIVE}；命中但页面已改版为
     * {@link WikiTextAnchorState#REANCHORABLE}；无法唯一命中（含歧义、已删除）为
     * {@link WikiTextAnchorState#ORPHANED}。锚点失配不隐藏评论，只是不高亮。
     */
    public WikiTextAnchorState stateAgainst(String currentContentHash, String currentContent) {
        if (!uniquelyResolvableIn(currentContent)) {
            return WikiTextAnchorState.ORPHANED;
        }
        return Objects.equals(contentHash, currentContentHash)
                ? WikiTextAnchorState.ACTIVE : WikiTextAnchorState.REANCHORABLE;
    }

    /**
     * 空白折叠归一化：统一换行、折叠连续空白为单个空格、去掉首尾空白与不可见控制字符。
     * 引用文本与页面正文使用同一套归一化，客户端可以照渲染文本提交引用。
     */
    public static String normalizeWhitespace(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        boolean pendingSpace = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isWhitespace(current) || Character.isSpaceChar(current)) {
                pendingSpace = builder.length() > 0;
                continue;
            }
            if (Character.isISOControl(current)) {
                continue;
            }
            if (pendingSpace) {
                builder.append(' ');
                pendingSpace = false;
            }
            builder.append(current);
        }
        return builder.toString();
    }

    /** 可定位 = 命中次数为 1；前缀/后缀各按「紧邻且完整匹配」参与消歧。 */
    private boolean resolvableIn(String content) {
        return matchCount(normalizeWhitespace(content)) == 1;
    }

    private int matchCount(String content) {
        if (content.isEmpty() || exact.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = content.indexOf(exact);
        while (index >= 0) {
            if (contextMatches(content, index)) {
                count++;
                if (count > 1) {
                    return count;
                }
            }
            index = content.indexOf(exact, index + 1);
        }
        return count;
    }

    private boolean contextMatches(String content, int index) {
        if (!prefix.isEmpty() && !content.substring(0, index).endsWith(prefix)) {
            return false;
        }
        return suffix.isEmpty() || content.startsWith(suffix, index + exact.length());
    }
}
