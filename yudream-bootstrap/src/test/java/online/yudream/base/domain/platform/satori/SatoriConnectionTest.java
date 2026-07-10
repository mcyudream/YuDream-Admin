package online.yudream.base.domain.platform.satori;

import online.yudream.base.domain.platform.satori.aggregate.SatoriConnection;
import online.yudream.base.domain.platform.satori.aggregate.SatoriEventRecord;
import online.yudream.base.domain.platform.satori.aggregate.SatoriLogin;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.satori.enumerate.SatoriLoginStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SatoriConnectionTest {

    @Test
    void shouldNormalizeUrlSwitchStateAndNeverExposeToken() {
        SatoriConnection connection = SatoriConnection.create("  Bot  ", " https://satori.example.com/v1/ ", "discord", "bot-1", "secret-token");

        assertThat(connection.getName()).isEqualTo("Bot");
        assertThat(connection.getBaseUrl()).isEqualTo("https://satori.example.com/v1");
        assertThat(connection.getPlatform()).isEqualTo("discord");
        assertThat(connection.getUserId()).isEqualTo("bot-1");
        assertThat(connection.enabled()).isTrue();
        assertThat(connection.toString()).contains("SatoriConnection").doesNotContain("secret-token");

        connection.disable();
        assertThat(connection.enabled()).isFalse();
        connection.enable();
        assertThat(connection.enabled()).isTrue();
    }

    @Test
    void shouldRejectCredentialsEmbeddedInSatoriUrl() {
        assertThatThrownBy(() -> SatoriConnection.create("Bot", "https://operator:secret@satori.example.com/v1", "discord", "bot-1", "token"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("HTTP");
    }

    @Test
    void shouldUseConnectionPlatformAndUserAsLoginNaturalKey() {
        SatoriLogin login = SatoriLogin.create(100L, "discord", "bot-1", SatoriLoginStatus.ONLINE,
                "discord", java.util.List.of("message.create"));

        assertThat(login.naturalKey()).isEqualTo("100:discord:bot-1");
    }

    @Test
    void shouldUseConnectionAndSequenceAsEventIdempotencyKey() {
        SatoriEventRecord event = SatoriEventRecord.create(100L, "9007199254740993", "message-created", "{\"id\":\"1\"}", 7);

        assertThat(event.idempotencyKey()).isEqualTo("100:9007199254740993");
        assertThat(event.getExpireAt()).isAfter(event.getReceivedAt());
    }
}
