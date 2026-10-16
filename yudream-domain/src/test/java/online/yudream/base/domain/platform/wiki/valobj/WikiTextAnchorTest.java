package online.yudream.base.domain.platform.wiki.valobj;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.enumerate.WikiTextAnchorState;
import online.yudream.base.domain.platform.wiki.enumerate.WikiTextBlockType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文本锚点值对象：空白归一化与限长、服务端定版唯一性、读取侧 active/reanchorable/orphaned 判定。
 */
class WikiTextAnchorTest {

    private static final String CONTENT = "欢迎来到快速开始页面。\n\n更多说明在这里。";

    @Test
    void draftNormalizesWhitespaceAndEnforcesLimits() {
        WikiTextAnchor draft = WikiTextAnchor.draft("  第一行 \r\n\t第二行  ", null, "  后缀  ", null, 0, 6);

        assertEquals("第一行 第二行", draft.exact());
        assertEquals("", draft.prefix());
        assertEquals("后缀", draft.suffix());
        assertEquals(0, draft.startOffset());
        assertEquals(6, draft.endOffset());
        assertNull(draft.versionId());
        assertEquals(0L, draft.revision());
        assertNull(draft.contentHash());

        assertThrows(BizException.class, () -> WikiTextAnchor.draft("   ", null, null, null, null, null));
        assertThrows(BizException.class, () -> WikiTextAnchor.draft("x".repeat(513), null, null, null, null, null));
        assertThrows(BizException.class, () -> WikiTextAnchor.draft("x", "y".repeat(65), null, null, null, null));
        assertThrows(BizException.class, () -> WikiTextAnchor.draft("x", null, "y".repeat(65), null, null, null));
        assertEquals(512, WikiTextAnchor.draft("x".repeat(512), null, null, null, null, null).exact().length());
        assertThrows(BizException.class, () -> WikiTextAnchor.draft("x", null, null, null, -1, null));
        assertThrows(BizException.class, () -> WikiTextAnchor.draft("x", null, null, null, 9, 3));
    }

    @Test
    void draftCannotCarryVersionStamp() {
        assertThrows(BizException.class, () -> new WikiTextAnchor("引用", "", "", null, null, null, 200L, 0L, "hash"));
        assertThrows(BizException.class, () -> new WikiTextAnchor("引用", "", "", null, null, null, 200L, 1L, " "));
        assertThrows(BizException.class, () -> new WikiTextAnchor("引用", "", "", null, null, null, null, 3L, null));
        assertThrows(BizException.class, () -> new WikiTextAnchor("引用", "", "", null, null, null, null, 0L, "hash"));
    }

    @Test
    void stampRequiresServerVersionAndUniqueResolution() {
        WikiTextAnchor draft = WikiTextAnchor.draft("快速开始", "欢迎来到", "页面。", null, null, null);

        assertThrows(BizException.class, () -> WikiTextAnchor.stamp(draft, null, 0L, null, CONTENT));
        assertThrows(BizException.class, () -> WikiTextAnchor.stamp(draft, 200L, 1L, "hash-1", "完全无关的正文"));
        assertThrows(BizException.class, () -> WikiTextAnchor.stamp(draft, 200L, 1L, "hash-1",
                "欢迎来到快速开始页面。欢迎来到快速开始页面。"));

        WikiTextAnchor stamped = WikiTextAnchor.stamp(draft, 200L, 3L, "hash-1", CONTENT);
        assertEquals(200L, stamped.versionId());
        assertEquals(3L, stamped.revision());
        assertEquals("hash-1", stamped.contentHash());
        assertEquals("快速开始", stamped.exact());
        assertTrue(stamped.uniquelyResolvableIn(CONTENT));
        // 客户端草稿不可直接定位，必须先由服务端定版
        assertFalse(draft.uniquelyResolvableIn(CONTENT));
    }

    @Test
    void ambiguousQuoteWithoutContextIsRejectedButContextDisambiguates() {
        String content = "第一步：安装。第二步：安装完成。";

        assertThrows(BizException.class,
                () -> WikiTextAnchor.stamp(WikiTextAnchor.draft("安装", null, null, null, null, null),
                        200L, 1L, "hash-1", content));

        WikiTextAnchor stamped = WikiTextAnchor.stamp(
                WikiTextAnchor.draft("安装", "第一步：", "。", null, null, null), 200L, 1L, "hash-1", content);
        assertEquals("安装", stamped.exact());
        assertEquals("第一步：", stamped.prefix());
    }

    @Test
    void stateUsesCurrentPublishedHashAndUniqueMatchOnly() {
        WikiTextAnchor anchored = WikiTextAnchor.stamp(
                WikiTextAnchor.draft("快速开始", "欢迎来到", "页面。", null, null, null), 200L, 1L, "hash-1", CONTENT);

        assertEquals(WikiTextAnchorState.ACTIVE, anchored.stateAgainst("hash-1", CONTENT));
        assertEquals(WikiTextAnchorState.REANCHORABLE, anchored.stateAgainst("hash-2", CONTENT));
        assertEquals(WikiTextAnchorState.ORPHANED, anchored.stateAgainst("hash-2", "页面已改版，引用文本已不存在。"));

        // 引用文本仍存在但出现两次且无法消歧：宁可 ORPHANED，不高亮
        assertEquals(WikiTextAnchorState.ORPHANED,
                anchored.stateAgainst("hash-2", "欢迎来到快速开始页面。欢迎来到快速开始页面。"));
        // 未定版的草稿永远不可高亮
        assertEquals(WikiTextAnchorState.ORPHANED,
                WikiTextAnchor.draft("快速开始", null, null, null, null, null).stateAgainst("hash-1", CONTENT));
    }

    @Test
    void blockHintValidatesTableShapeAndWireNames() {
        WikiTextBlockHint paragraph = WikiTextBlockHint.of("paragraph", 3, null, null);
        assertEquals(WikiTextBlockType.PARAGRAPH, paragraph.type());
        assertEquals(3, paragraph.ordinal());
        assertNull(paragraph.tableRow());
        assertNull(paragraph.tableCell());
        assertEquals("paragraph", paragraph.type().wireValue());

        // 线协议 lowerCamel、枚举名与 SNAKE_CASE 都能解析到同一类型
        assertEquals(WikiTextBlockType.TABLE_CELL, WikiTextBlockType.of("tableCell"));
        assertEquals(WikiTextBlockType.TABLE_CELL, WikiTextBlockType.of("TABLE_CELL"));
        assertEquals(WikiTextBlockType.LIST_ITEM, WikiTextBlockType.of("listItem"));
        assertEquals(WikiTextBlockType.LIST_ITEM, WikiTextBlockType.of("LIST_ITEM"));
        assertEquals(WikiTextBlockType.QUOTE, WikiTextBlockType.of("blockquote"));
        assertEquals("blockquote", WikiTextBlockType.QUOTE.wireValue());
        assertNull(WikiTextBlockType.of("unknown"));

        WikiTextBlockHint cell = WikiTextBlockHint.of("tableCell", 4, 1, 2);
        assertEquals(WikiTextBlockType.TABLE_CELL, cell.type());
        assertEquals(1, cell.tableRow());
        assertEquals(2, cell.tableCell());

        assertThrows(BizException.class, () -> WikiTextBlockHint.of("UNKNOWN", 0, null, null));
        assertThrows(BizException.class, () -> WikiTextBlockHint.of("paragraph", null, null, null));
        assertThrows(BizException.class, () -> WikiTextBlockHint.of("paragraph", -1, null, null));
        assertThrows(BizException.class, () -> WikiTextBlockHint.of("paragraph", 0, 1, null));
        assertThrows(BizException.class, () -> WikiTextBlockHint.of("tableCell", 0, null, null));
        assertThrows(BizException.class, () -> WikiTextBlockHint.tableCell(0, -1, 2));
    }

    @Test
    void anchorStateWireNamesAreLowercase() {
        assertEquals("active", WikiTextAnchorState.ACTIVE.wireValue());
        assertEquals("reanchorable", WikiTextAnchorState.REANCHORABLE.wireValue());
        assertEquals("orphaned", WikiTextAnchorState.ORPHANED.wireValue());
        assertEquals(WikiTextAnchorState.ACTIVE, WikiTextAnchorState.of("ACTIVE"));
        assertEquals(WikiTextAnchorState.ACTIVE, WikiTextAnchorState.of("active"));
        assertNull(WikiTextAnchorState.of("unknown"));
    }
}
