package online.yudream.base.domain.platform.satori;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.annotation.JsonProperty;
import online.yudream.base.domain.platform.satori.model.SatoriBidiPage;
import online.yudream.base.domain.platform.satori.model.SatoriModels;
import online.yudream.base.domain.platform.satori.model.SatoriPage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SatoriProtocolModelTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .addMixIn(SatoriModels.SatoriEvent.class, SatoriEventJsonMixin.class);

    @Test
    void shouldDeserializeEveryStandardResourceFromEventFixture() throws IOException {
        SatoriModels.SatoriEvent event = fixture("satori/event-message-created.json", SatoriModels.SatoriEvent.class);

        assertThat(event.sn()).isEqualTo("9007199254740993");
        assertThat(event.login().user().id()).isEqualTo("9223372036854775806");
        assertThat(event.message().id()).isEqualTo("9223372036854775805");
        assertThat(event.message().content()).contains("<at id=\"42\"/>");
        assertThat(event.channel().id()).isEqualTo("10000000000000000001");
        assertThat(event.guild().id()).isEqualTo("10000000000000000002");
        assertThat(event.member().user().id()).isEqualTo("10000000000000000003");
        assertThat(event.role().id()).isEqualTo("10000000000000000004");
        assertThat(event.user().id()).isEqualTo("10000000000000000005");
        assertThat(event.operator().id()).isEqualTo("10000000000000000006");
        assertThat(event.argv().options()).containsEntry("silent", true);
        assertThat(event.button().id()).isEqualTo("approve");
        assertThat(event.extensionType()).isEqualTo("discord:interaction");
        assertThat(event.extensionData()).containsEntry("interaction_id", "10000000000000000007");

        SatoriModels.SatoriResourceBundle resources = fixture("satori/resources.json", SatoriModels.SatoriResourceBundle.class);
        assertThat(resources.friend().user().id()).isEqualTo("10000000000000000008");
        assertThat(resources.emoji().id()).isEqualTo("10000000000000000009");
        assertThat(resources.meta().protocolVersion()).isEqualTo("1.0");
        assertThat(resources.meta().adapter()).isEqualTo("onebot");
        assertThat(resources.meta().features()).contains("message.create");
    }

    @Test
    void shouldAcceptMissingAndExplicitNullOptionalFieldsAndKeepPageCursorsAsStrings() throws IOException {
        SatoriModels.SatoriEvent event = fixture("satori/event-optionals.json", SatoriModels.SatoriEvent.class);
        SatoriPage<SatoriModels.SatoriMessage> page = fixturePage("satori/message-page.json", SatoriPage.class, SatoriModels.SatoriMessage.class);
        SatoriBidiPage<SatoriModels.SatoriGuild> bidiPage = fixturePage("satori/guild-bidi-page.json", SatoriBidiPage.class, SatoriModels.SatoriGuild.class);

        assertThat(event.message().updatedAt()).isNull();
        assertThat(event.message().guild()).isNull();
        assertThat(event.login().features()).isEmpty();
        assertThat(page.data()).hasSize(1);
        assertThat(page.data().getFirst().id()).isEqualTo("10001");
        assertThat(page.next()).isEqualTo("9007199254740993");
        assertThat(bidiPage.prev()).isEqualTo("9223372036854775806");
        assertThat(bidiPage.next()).isNull();
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

    abstract static class SatoriEventJsonMixin {
        @JsonProperty("_type")
        abstract String extensionType();

        @JsonProperty("_data")
        abstract java.util.Map<String, Object> extensionData();
    }
}
