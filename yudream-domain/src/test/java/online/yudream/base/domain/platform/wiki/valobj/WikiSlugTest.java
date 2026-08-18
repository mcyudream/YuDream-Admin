package online.yudream.base.domain.platform.wiki.valobj;

import online.yudream.base.domain.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WikiSlugTest {

    @Test
    void derivesAsciiSlug() {
        assertEquals("hello-world", WikiSlug.derive("Hello World"));
    }

    @Test
    void derivesChineseTitleWithStableHashSuffix() {
        assertTrue(WikiSlug.derive("实体A").startsWith("a-"));
        assertTrue(WikiSlug.derive("实体").startsWith("page-"));
    }

    @Test
    void rejectsInvalidSpaceSlug() {
        assertThrows(BizException.class, () -> WikiSlug.of("My Wiki"));
    }
}
