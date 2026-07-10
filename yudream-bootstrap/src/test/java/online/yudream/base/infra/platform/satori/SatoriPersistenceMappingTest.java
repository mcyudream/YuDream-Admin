package online.yudream.base.infra.platform.satori;

import online.yudream.base.domain.platform.satori.aggregate.SatoriConnection;
import online.yudream.base.domain.platform.satori.aggregate.SatoriEventRecord;
import online.yudream.base.domain.platform.satori.aggregate.SatoriLogin;
import online.yudream.base.domain.platform.satori.enumerate.SatoriLoginStatus;
import online.yudream.base.domain.platform.satori.service.SatoriCredentialCipher;
import online.yudream.base.infra.platform.satori.dataobj.SatoriEventRecordDO;
import online.yudream.base.infra.platform.satori.mapper.SatoriInfraMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.Indexed;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SatoriPersistenceMappingTest {

    private final SatoriCredentialCipher cipher = new SatoriCredentialCipher() {
        @Override public String encrypt(String plaintext) { return "enc:" + plaintext; }
        @Override public String decrypt(String ciphertext) { return ciphertext.substring(4); }
    };

    @Test
    void shouldRoundTripConnectionWithoutPersistingPlaintextToken() {
        SatoriConnection connection = SatoriConnection.create("Bot", "https://satori.example.com/v1/", "secret-token");
        connection.setId(1L);

        var dataObj = SatoriInfraMapper.toDataObj(connection, cipher);
        assertThat(dataObj.getEncryptedToken()).isEqualTo("enc:secret-token");
        assertThat(java.util.Arrays.stream(dataObj.getClass().getDeclaredFields()).map(java.lang.reflect.Field::getName))
                .doesNotContain("token");
        assertThat(SatoriInfraMapper.toDomain(dataObj, cipher)).usingRecursiveComparison().isEqualTo(connection);
    }

    @Test
    void shouldRoundTripLoginAndEventAndDeclareTtlField() throws NoSuchFieldException {
        SatoriLogin login = SatoriLogin.create(1L, "discord", "bot-1", SatoriLoginStatus.ONLINE, "discord", List.of("message.create"));
        SatoriEventRecord event = SatoriEventRecord.create(1L, "2", "message-created", "{}", 3);

        assertThat(SatoriInfraMapper.toDomain(SatoriInfraMapper.toDataObj(login))).usingRecursiveComparison().isEqualTo(login);
        assertThat(SatoriInfraMapper.toDomain(SatoriInfraMapper.toDataObj(event))).usingRecursiveComparison().isEqualTo(event);

        Field expireAt = SatoriEventRecordDO.class.getDeclaredField("expireAt");
        assertThat(expireAt.isAnnotationPresent(Indexed.class)).isTrue();
        assertThat(expireAt.getAnnotation(Indexed.class).expireAfter()).isEqualTo("0s");
    }
}
