package online.yudream.base.domain.platform.satori;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.infra.platform.satori.json.SatoriJsonMapper;
import online.yudream.base.domain.platform.satori.enumerate.SatoriChannelType;
import online.yudream.base.domain.platform.satori.enumerate.SatoriLoginStatus;
import online.yudream.base.domain.platform.satori.enumerate.SatoriOpcode;
import online.yudream.base.domain.platform.satori.model.SatoriBidiPage;
import online.yudream.base.domain.platform.satori.model.SatoriModels;
import online.yudream.base.domain.platform.satori.model.SatoriPage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SatoriProtocolModelTest {

    private final ObjectMapper objectMapper = SatoriJsonMapper.createObjectMapper();

    @Test
    void shouldDeserializeEveryStandardResourceFromEventFixture() throws IOException {
        SatoriModels.SatoriEvent event = fixture("satori/event-message-created.json", SatoriModels.SatoriEvent.class);

        assertThat(event.sn()).isEqualTo("9007199254740993");
        assertThat(event.login().sn()).isEqualTo("9007199254740994");
        assertThat(event.login().user().id()).isEqualTo("9223372036854775806");
        assertThat(event.message().id()).isEqualTo("9223372036854775805");
        assertThat(event.message().content()).contains("<at id=\"42\"/>");
        assertThat(event.channel().id()).isEqualTo("10000000000000000001");
        assertThat(event.emoji().id()).isEqualTo("10000000000000000009");
        assertThat(event.friend().user().id()).isEqualTo("10000000000000000008");
        assertThat(event.guild().id()).isEqualTo("10000000000000000002");
        assertThat(event.member().user().id()).isEqualTo("10000000000000000003");
        assertThat(event.member().roles()).extracting(SatoriModels.SatoriGuildRole::id)
                .containsExactly("10000000000000000004");
        assertThat(event.role().id()).isEqualTo("10000000000000000004");
        assertThat(event.user().id()).isEqualTo("10000000000000000005");
        assertThat(event.operator().id()).isEqualTo("10000000000000000006");
        assertThat(event.argv().options()).containsEntry("silent", true);
        assertThat(event.button().id()).isEqualTo("approve");
        assertThat(event.extensionType()).isEqualTo("discord:interaction");
        assertThat(event.extensionData()).isEqualTo(Map.of("interaction_id", "10000000000000000007"));
        assertThat(event.referrer()).containsEntry("thread", "thread-1");

        SatoriResourceFixture resources = fixture("satori/resources.json", SatoriResourceFixture.class);
        assertThat(resources.friend().user().id()).isEqualTo("10000000000000000008");
        assertThat(resources.emoji().id()).isEqualTo("10000000000000000009");
        assertThat(resources.meta().impl()).isEqualTo("satori");
        assertThat(resources.meta().protocolVersion()).isEqualTo("1.0");
        assertThat(resources.meta().adapter()).isEqualTo("onebot");
        assertThat(resources.meta().features()).contains("message.create");
        assertThat(resources.meta().extraFields()).containsKey("vendor");
        assertThat(resources.meta().extraFields()).containsEntry("vendor_flag", null);
    }

    @Test
    void shouldAcceptMissingAndExplicitNullOptionalFieldsAndKeepPageCursorsAsStrings() throws IOException {
        SatoriModels.SatoriEvent event = fixture("satori/event-optionals.json", SatoriModels.SatoriEvent.class);
        SatoriPage<SatoriModels.SatoriMessage> page = fixturePage("satori/message-page.json", SatoriPage.class, SatoriModels.SatoriMessage.class);
        SatoriBidiPage<SatoriModels.SatoriGuild> bidiPage = fixturePage("satori/guild-bidi-page.json", SatoriBidiPage.class, SatoriModels.SatoriGuild.class);

        assertThat(event.message().updatedAt()).isNull();
        assertThat(event.message().guild()).isNull();
        assertThat(event.login().features()).isEmpty();
        assertThat(event.login().sn()).isNull();
        assertThat(page.data()).hasSize(1);
        assertThat(page.data().getFirst().id()).isEqualTo("10001");
        assertThat(page.next()).isEqualTo("9007199254740993");
        assertThat(bidiPage.prev()).isEqualTo("9223372036854775806");
        assertThat(bidiPage.next()).isNull();
    }

    @Test
    void shouldIgnoreAdapterSpecificLoginFields() throws IOException {
        SatoriModels.SatoriLogin login = objectMapper.readValue("""
                {"sn":1,"platform":"llonebot","self_id":"3816679582","user":{"id":"3816679582"},
                 "status":1,"adapter":"llonebot","features":["login.get"],"proxy_urls":[]}
                """, SatoriModels.SatoriLogin.class);

        assertThat(login.platform()).isEqualTo("llonebot");
        assertThat(login.selfId()).isEqualTo("3816679582");
        assertThat(login.features()).containsExactly("login.get");
    }

    @Test
    void shouldSerializeProtocolNumbersAndKeepBoundaryValuesAsStrings() throws IOException {
        SatoriModels.SatoriEvent event = fixture("satori/event-message-created.json", SatoriModels.SatoriEvent.class);
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(event));

        assertThat(json.path("sn").isTextual()).isTrue();
        assertThat(json.path("sn").asText()).isEqualTo("9007199254740993");
        assertThat(json.path("channel").path("type").intValue()).isEqualTo(0);
        assertThat(json.path("login").path("status").intValue()).isEqualTo(1);
        assertThat(objectMapper.readValue("0", SatoriOpcode.class)).isEqualTo(SatoriOpcode.EVENT);
        assertThat(objectMapper.writeValueAsString(SatoriOpcode.META)).isEqualTo("5");
        assertThat(objectMapper.readValue("3", SatoriChannelType.class)).isEqualTo(SatoriChannelType.VOICE);
        assertThat(objectMapper.readValue("4", SatoriLoginStatus.class)).isEqualTo(SatoriLoginStatus.RECONNECT);
    }

    @Test
    void shouldRoundTripMetaImplementationAndUnknownFields() throws IOException {
        SatoriResourceFixture resources = fixture("satori/resources.json", SatoriResourceFixture.class);
        String serialized = objectMapper.writeValueAsString(resources.meta());

        assertThat(objectMapper.readTree(serialized).path("impl").asText()).isEqualTo("satori");
        assertThat(objectMapper.readTree(serialized).path("vendor").path("revision").intValue()).isEqualTo(2);
    }

    @Test
    void shouldUseProductionWireNamesForSatoriExtensionsAndProtectMetaExtras() throws IOException {
        SatoriModels.SatoriEvent event = fixture("satori/event-message-created.json", SatoriModels.SatoriEvent.class);
        JsonNode serializedEvent = objectMapper.readTree(objectMapper.writeValueAsString(event));
        SatoriResourceFixture resources = fixture("satori/resources.json", SatoriResourceFixture.class);

        assertThat(serializedEvent.path("_type").asText()).isEqualTo("discord:interaction");
        assertThat(serializedEvent.path("_data").path("interaction_id").asText())
                .isEqualTo("10000000000000000007");
        assertThat(resources.meta().extraFields()).isUnmodifiable();
        assertThat(resources.meta().extraFields().get("vendor")).isInstanceOf(Map.class);
        assertThat((Map<?, ?>) resources.meta().extraFields().get("vendor")).isUnmodifiable();
    }

    @Test
    void shouldProtectReservedMetaFieldsAndRejectInvalidEnumWireValues() throws IOException {
        SatoriModels.SatoriMeta meta = new SatoriModels.SatoriMeta(
                "satori", "1.0", "onebot", List.of(),
                Map.of("impl", "forged", "vendor", "trusted")
        );

        JsonNode serialized = objectMapper.readTree(objectMapper.writeValueAsString(meta));
        assertThat(serialized.path("impl").asText()).isEqualTo("satori");
        assertThat(serialized.path("vendor").asText()).isEqualTo("trusted");
        assertThatThrownBy(() -> objectMapper.readValue("\"unexpected\"", SatoriOpcode.class))
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldKeepNativeEventPayloadsForArrayAndScalarExtensions() throws IOException {
        SatoriModels.SatoriEvent arrayEvent = fixture("satori/event-native-data-array.json", SatoriModels.SatoriEvent.class);
        SatoriModels.SatoriEvent scalarEvent = fixture("satori/event-native-data-scalar.json", SatoriModels.SatoriEvent.class);

        assertThat(arrayEvent.extensionData()).isInstanceOf(List.class);
        assertThat(arrayEvent.extensionData()).isEqualTo(Arrays.asList("first", null, Map.of("nested", true)));
        assertThat((List<?>) arrayEvent.extensionData()).isUnmodifiable();
        assertThat(scalarEvent.extensionData()).isEqualTo("opaque-token");
    }

    private <T> T fixture(String path, Class<T> type) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as("fixture %s", path).isNotNull();
            return objectMapper.readValue(input, type);
        }
    }

    private <P, T> P fixturePage(String path, Class<P> pageType, Class<T> itemType) throws IOException {
        JavaType type = objectMapper.getTypeFactory().constructParametricType(pageType, itemType);
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as("fixture %s", path).isNotNull();
            return objectMapper.readValue(input, type);
        }
    }

    private record SatoriResourceFixture(
            SatoriModels.SatoriFriend friend,
            SatoriModels.SatoriEmoji emoji,
            SatoriModels.SatoriMeta meta
    ) {
    }
}
