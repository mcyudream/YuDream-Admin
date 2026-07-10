package online.yudream.base.domain.platform.satori;

import online.yudream.base.domain.platform.satori.message.SatoriElement;
import online.yudream.base.domain.platform.satori.message.SatoriMessage;
import online.yudream.base.domain.platform.satori.message.SatoriMessageBuilder;
import online.yudream.base.domain.platform.satori.service.SatoriMessageEncoder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SatoriMessageEncoderTest {

    private final SatoriMessageEncoder encoder = new SatoriMessageEncoder();

    @Test
    void shouldEncodeEscapedTextAttributesAndNamespacedElements() {
        SatoriMessage message = SatoriMessageBuilder.create()
                .text("a < b & c")
                .at("42", "Alice & Bob")
                .element("kook:card", Map.of("size", "lg"), List.of())
                .build();

        assertThat(encoder.encode(message))
                .isEqualTo("a &lt; b &amp; c<at id=\"42\" name=\"Alice &amp; Bob\"/><kook:card size=\"lg\"/>");
    }

    @Test
    void shouldEncodeEveryStandardElementIncludingNestedMessageStructure() {
        SatoriMessage message = SatoriMessageBuilder.create()
                .sharp("general", "General")
                .emoji("wave", "Wave")
                .a("https://example.test/?q=\"x\"", List.of(SatoriElement.text("link")))
                .img("https://example.test/image.png", "image")
                .audio("https://example.test/audio.mp3")
                .video("https://example.test/video.mp4")
                .file("https://example.test/file.zip", "archive")
                .strong(List.of(SatoriElement.text("bold")))
                .em(List.of(SatoriElement.text("emphasis")))
                .ins(List.of(SatoriElement.text("inserted")))
                .del(List.of(SatoriElement.text("deleted")))
                .spl(List.of(SatoriElement.text("spoiler")))
                .code(List.of(SatoriElement.text("a < b")))
                .sup(List.of(SatoriElement.text("top")))
                .sub(List.of(SatoriElement.text("bottom")))
                .br()
                .p(List.of(SatoriElement.text("paragraph")))
                .message("nested-1", List.of(SatoriElement.text("reply")))
                .quote("quote-1", List.of(SatoriElement.text("quoted")))
                .author("user-1", "Alice", List.of(SatoriElement.text("author text")))
                .button("approve", "Approve", List.of(SatoriElement.text("click")))
                .build();

        assertThat(encoder.encode(message)).isEqualTo(
                "<sharp id=\"general\" name=\"General\"/><emoji id=\"wave\" name=\"Wave\"/>"
                        + "<a href=\"https://example.test/?q=&quot;x&quot;\">link</a>"
                        + "<img src=\"https://example.test/image.png\" title=\"image\"/>"
                        + "<audio src=\"https://example.test/audio.mp3\"/>"
                        + "<video src=\"https://example.test/video.mp4\"/>"
                        + "<file src=\"https://example.test/file.zip\" title=\"archive\"/>"
                        + "<strong>bold</strong><em>emphasis</em><ins>inserted</ins><del>deleted</del><spl>spoiler</spl>"
                        + "<code>a &lt; b</code><sup>top</sup><sub>bottom</sub><br/><p>paragraph</p>"
                        + "<message id=\"nested-1\">reply</message><quote id=\"quote-1\">quoted</quote>"
                        + "<author id=\"user-1\" name=\"Alice\">author text</author>"
                        + "<button id=\"approve\" title=\"Approve\">click</button>"
        );
    }

    @Test
    void shouldRejectIllegalElementAndAttributeNamesAndProtectMessageImmutability() {
        assertThatThrownBy(() -> SatoriElement.element("script", Map.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SatoriElement.element("card", Map.of("on click", "run"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SatoriElement.element("kook:", Map.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);

        SatoriElement.Node node = SatoriElement.element("kook:card", Map.of("size", "lg"), List.of());
        SatoriMessage message = new SatoriMessage(List.of(node));

        assertThatThrownBy(() -> message.elements().add(SatoriElement.text("unexpected")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> node.attributes().put("size", "sm"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
