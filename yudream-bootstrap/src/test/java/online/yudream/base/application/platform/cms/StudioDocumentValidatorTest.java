package online.yudream.base.application.platform.cms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import online.yudream.base.application.platform.cms.service.StudioDocumentValidator;
import online.yudream.base.domain.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class StudioDocumentValidatorTest {
    static final String DOCUMENT = """
            {"schemaVersion":1,"id":"site","name":"测试站点","theme":{"tokens":{"color":"#fff"},"header":[],"footer":[]},"pages":[{"id":"home","name":"首页","path":"/","root":[{"id":"node","type":"text","tag":"p","text":"你好","attributes":{},"styles":{},"children":[]}]}],"assets":[]}
            """;
    final StudioDocumentValidator validator = new StudioDocumentValidator();
    final ObjectMapper mapper = new ObjectMapper();

    @Test void acceptsStructuredDocumentAndRejectsUnknownExecutableSurface() {
        assertThatThrownBy(() -> validator.validate(DOCUMENT.replace("\"name\":\"测试站点\"", "\"name\":\"测试站点\",\"future\":{\"opaque\":true}"))).isInstanceOf(BizException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"script", "iframe", "object", "embed", "svg", "style", "link", "input"})
    void rejectsActiveTags(String tag) {
        assertThatThrownBy(() -> validator.validate(DOCUMENT.replace("\"tag\":\"p\"", "\"tag\":\"" + tag + "\""))).isInstanceOf(BizException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"onclick", "srcdoc", "style", "innerHTML", "srcset", "is", "formaction"})
    void rejectsUnsafeAttributes(String attribute) {
        assertThatThrownBy(() -> validator.validate(DOCUMENT.replace("\"attributes\":{}", "\"attributes\":{\"" + attribute + "\":\"x\"}"))).isInstanceOf(BizException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"javascript:alert(1)", "//evil.test/x", "data:image/svg+xml;base64,PHN2Zz4=", "blob:https://example.com/x", "/%5cevil", "data:x", "data:;base64,AA=="})
    void rejectsUnsafeResourceUrls(String url) throws Exception {
        var doc = (ObjectNode) mapper.readTree(DOCUMENT);
        ((ObjectNode) doc.at("/pages/0/root/0/attributes")).put("src", url);
        assertThatThrownBy(() -> validator.validate(doc.toString())).isInstanceOf(BizException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"url(javascript:alert(1))", "red;position:fixed", "expression(alert(1))", "url(https://good.test/a)/*x*/", "url(https://good.test/a));x", "paint(evil)", "red}@import x", "url(data:image/svg+xml;base64,PHN2Zz4=)"})
    void rejectsUnsafeCss(String css) throws Exception {
        var doc = (ObjectNode) mapper.readTree(DOCUMENT);
        ((ObjectNode) doc.at("/pages/0/root/0/styles")).put("background", css);
        assertThatThrownBy(() -> validator.validate(doc.toString())).isInstanceOf(BizException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"url(https://example.com/a.png)", "url('data:image/png;base64,AA==')", "linear-gradient(red, blue)", "calc(100% - 2px)", "var(--brand)"})
    void acceptsBoundedCssAndWhitelistedEmbeddedResources(String css) throws Exception {
        var doc = (ObjectNode) mapper.readTree(DOCUMENT);
        ((ObjectNode) doc.at("/pages/0/root/0/styles")).put("background", css);
        assertThatCode(() -> validator.validate(doc.toString())).doesNotThrowAnyException();
    }
    @Test void longPlainCssValueIsValidatedWithoutRepeatedFunctionSearch() throws Exception {
        var doc = (ObjectNode) mapper.readTree(DOCUMENT);
        ((ObjectNode) doc.at("/pages/0/root/0/styles")).put("--long-token", "a".repeat(300_000));
        org.junit.jupiter.api.Assertions.assertTimeout(java.time.Duration.ofSeconds(3), () -> validator.validate(doc.toString()));
    }
    @Test void rejectsDuplicateIdsRoutesMalformedJsonAndOversize() {
        assertThatThrownBy(() -> validator.validate(DOCUMENT.replace("\"id\":\"node\"", "\"id\":\"home\""))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> validator.validate(DOCUMENT.replace("\"path\":\"/\"", "\"path\":\"/../admin\""))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> validator.validate(DOCUMENT.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> validator.validate(DOCUMENT + " {}" )).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> validator.validate("中".repeat(StudioDocumentValidator.MAX_DOCUMENT_BYTES / 3 + 1))).isInstanceOf(BizException.class);
    }
    @Test void rejectsExcessNodeCountAndDepth() throws Exception {
        var doc = (ObjectNode) mapper.readTree(DOCUMENT);
        var root = (com.fasterxml.jackson.databind.node.ArrayNode) doc.at("/pages/0/root");
        var node = (ObjectNode) root.get(0).deepCopy();
        root.removeAll();
        for (int i = 0; i < 2001; i++) root.add(node.deepCopy().put("id", "n" + i));
        assertThatThrownBy(() -> validator.validate(doc.toString())).isInstanceOf(BizException.class).hasMessageContaining("2000");
        root.removeAll();
        ObjectNode current = node.deepCopy(); current.remove("text"); root.add(current);
        for (int i = 0; i < 32; i++) {
            ObjectNode child = node.deepCopy().put("id", "depth" + i); child.remove("text");
            ((com.fasterxml.jackson.databind.node.ArrayNode) current.get("children")).add(child); current = child;
        }
        assertThatThrownBy(() -> validator.validate(doc.toString())).isInstanceOf(BizException.class).hasMessageContaining("32");
    }
}
