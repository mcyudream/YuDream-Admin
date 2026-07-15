package online.yudream.base.domain.platform.wiki.aggregate;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class WikiNodeTest {

    @Test
    void rejectsMovingNodeBelowItsOwnDescendant() {
        WikiNode node = WikiNode.directory(10L, null, "指南", "guide", 0);
        node.setId(10L);

        assertThrows(BizException.class, () -> node.moveTo(20L, "10/20/"));
    }

    @Test
    void pageCannotBecomeDirectoryParent() {
        WikiNode page = WikiNode.page(10L, null, "入门", "start", 0);

        assertThrows(BizException.class, () -> page.ensureCanContain(WikiNodeType.PAGE));
    }
}
