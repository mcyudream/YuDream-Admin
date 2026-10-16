package online.yudream.base.interfaces.platform.wiki.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 评论文本锚点请求校验：引用文本限长、块提示成对、表格行列必须随块提示、偏移非负。
 */
class WikiCommentAnchorRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void completeFlatAnchorIsValid() {
        WikiCommentAnchorRequest request = anchor("快速开始", "欢迎来到", "页面。");
        request.setBlockOrdinal(4);
        request.setBlockType("tableCell");
        request.setTableRow(1);
        request.setTableCell(2);
        request.setStartOffset(3);
        request.setEndOffset(8);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void blockOrdinalAndBlockTypeMustBeProvidedAsAPair() {
        WikiCommentAnchorRequest ordinalOnly = anchor("快速开始", null, null);
        ordinalOnly.setBlockOrdinal(4);
        assertThat(validator.validate(ordinalOnly)).isNotEmpty();

        WikiCommentAnchorRequest typeOnly = anchor("快速开始", null, null);
        typeOnly.setBlockType("paragraph");
        assertThat(validator.validate(typeOnly)).isNotEmpty();

        // 两者都不给（只有引用文本与偏移提示）是合法的
        assertThat(validator.validate(anchor("快速开始", null, null))).isEmpty();
    }

    @Test
    void tableHintsRequireBlockHint() {
        WikiCommentAnchorRequest rowOnly = anchor("快速开始", null, null);
        rowOnly.setTableRow(1);
        assertThat(validator.validate(rowOnly)).isNotEmpty();

        WikiCommentAnchorRequest ordinalWithoutType = anchor("快速开始", null, null);
        ordinalWithoutType.setBlockOrdinal(4);
        ordinalWithoutType.setTableCell(2);
        assertThat(validator.validate(ordinalWithoutType)).isNotEmpty();
    }

    @Test
    void exactAndContextLengthsAreBounded() {
        assertThat(validator.validate(anchor("x".repeat(513), null, null))).isNotEmpty();
        assertThat(validator.validate(anchor("  ", null, null))).isNotEmpty();
        WikiCommentAnchorRequest request = anchor("快速开始", "p".repeat(65), "s".repeat(65));
        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void offsetsMustBeNonNegative() {
        WikiCommentAnchorRequest request = anchor("快速开始", null, null);
        request.setStartOffset(-1);
        request.setEndOffset(2);
        assertThat(validator.validate(request)).isNotEmpty();
    }

    private static WikiCommentAnchorRequest anchor(String exact, String prefix, String suffix) {
        WikiCommentAnchorRequest request = new WikiCommentAnchorRequest();
        request.setExact(exact);
        request.setPrefix(prefix);
        request.setSuffix(suffix);
        return request;
    }
}
