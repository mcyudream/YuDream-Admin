package online.yudream.base.domain.platform.satori;

import online.yudream.base.domain.platform.satori.message.SatoriElement;
import online.yudream.base.domain.platform.satori.message.SatoriMessage;
import online.yudream.base.domain.platform.satori.message.SatoriMessageBuilder;
import online.yudream.base.domain.platform.satori.service.SatoriMessageEncoder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    @Test
    void shouldRejectMissingRequiredAttributesAndAllowOptionalAttributesToBeOmitted() {
        assertThatThrownBy(() -> SatoriMessageBuilder.create().sharp(null, "General"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SatoriMessageBuilder.create().sharp(" ", "General"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SatoriMessageBuilder.create().a(null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SatoriMessageBuilder.create().a(" ", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SatoriMessageBuilder.create().img(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SatoriMessageBuilder.create().audio(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SatoriMessageBuilder.create().video(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SatoriMessageBuilder.create().file(" ", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SatoriMessageBuilder.create().buttonAction(null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SatoriMessageBuilder.create().buttonLink(" ", null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SatoriMessageBuilder.create().buttonInput(null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SatoriMessageBuilder.create().at(" ", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SatoriMessageBuilder.create().emoji(null, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SatoriMessageBuilder.create().img("https://example.test/image.png", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SatoriMessageBuilder.create().message(" ", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SatoriMessageBuilder.create().author(null, " ", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SatoriMessageBuilder.create().element("a", Map.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SatoriMessageBuilder.create().element("img", Map.of("src", " "), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SatoriMessageBuilder.create().element("sharp", Map.of("name", "General"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SatoriMessageBuilder.create().element(
                "button", Map.of("type", "link"), List.of()
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SatoriElement.element("img", Map.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SatoriElement.element("button", Map.of("type", "link"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);

        SatoriMessage optionalAttributesOmitted = SatoriMessageBuilder.create()
                .at(null, null)
                .emoji(null, null)
                .img("https://example.test/image.png", null)
                .message(null, List.of())
                .quote(null, List.of())
                .author(null, null, List.of())
                .build();

        assertThat(encoder.encode(optionalAttributesOmitted))
                .isEqualTo("<at/><emoji/><img src=\"https://example.test/image.png\"/><message/><quote/><author/>");
    }

    @Test
    void shouldSnapshotElementAndMessageCollections() {
        Map<String, String> sourceAttributes = new LinkedHashMap<>();
        sourceAttributes.put("size", "lg");
        List<SatoriElement> sourceChildren = new ArrayList<>(List.of(SatoriElement.text("original")));
        SatoriElement.Node node = SatoriElement.element("kook:card", sourceAttributes, sourceChildren);

        sourceAttributes.put("size", "sm");
        sourceAttributes.put("theme", "primary");
        sourceChildren.add(SatoriElement.text("later"));

        List<SatoriElement> sourceElements = new ArrayList<>(List.of(node));
        SatoriMessage message = new SatoriMessage(sourceElements);
        sourceElements.add(SatoriElement.text("unexpected"));

        assertThat(node.attributes()).containsExactly(Map.entry("size", "lg"));
        assertThat(node.children()).containsExactly(SatoriElement.text("original"));
        assertThat(message.elements()).containsExactly(node);
    }

    @Test
    void shouldEncodeButtonsWithTheirTypeSpecificRequiredAttributes() {
        SatoriMessage message = SatoriMessageBuilder.create()
                .buttonAction("approve", "success", List.of(SatoriElement.text("Approve")))
                .buttonLink("https://example.test", null, List.of(SatoriElement.text("Open")))
                .buttonInput("/help", "secondary", List.of(SatoriElement.text("Fill")))
                .build();

        assertThat(encoder.encode(message)).isEqualTo(
                "<button type=\"action\" id=\"approve\" theme=\"success\">Approve</button>"
                        + "<button type=\"link\" href=\"https://example.test\">Open</button>"
                        + "<button type=\"input\" text=\"/help\" theme=\"secondary\">Fill</button>"
        );
    }
}
