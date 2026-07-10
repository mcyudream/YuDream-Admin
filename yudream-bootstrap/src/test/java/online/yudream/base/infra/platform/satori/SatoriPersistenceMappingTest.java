package online.yudream.base.infra.platform.satori;

import online.yudream.base.domain.platform.satori.aggregate.SatoriConnection;
import online.yudream.base.domain.platform.satori.aggregate.SatoriEventRecord;
import online.yudream.base.domain.platform.satori.aggregate.SatoriLogin;
import online.yudream.base.domain.platform.satori.enumerate.SatoriLoginStatus;
import online.yudream.base.domain.platform.satori.service.SatoriCredentialCipher;
import online.yudream.base.infra.platform.satori.dataobj.SatoriEventRecordDO;
import online.yudream.base.infra.platform.satori.mapper.SatoriInfraMapper;
import online.yudream.base.infra.platform.satori.service.AesGcmSatoriCredentialCipher;
import online.yudream.base.infra.platform.satori.bootstrap.SatoriIndexInitializer;
import online.yudream.base.infra.platform.satori.dataobj.SatoriLoginDO;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SatoriPersistenceMappingTest {

    private final SatoriCredentialCipher cipher = new SatoriCredentialCipher() {
        @Override public String encrypt(String plaintext) { return "enc:" + plaintext; }
        @Override public String decrypt(String ciphertext) { return ciphertext.substring(4); }
    };

    @Test
    void shouldDeferCredentialKeyValidationUntilSatoriActuallyUsesTheCipher() {
        AesGcmSatoriCredentialCipher cipherWithoutConfiguredKey = new AesGcmSatoriCredentialCipher("");

        assertThat(cipherWithoutConfiguredKey).isNotNull();
        assertThatThrownBy(() -> cipherWithoutConfiguredKey.encrypt("secret-token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("YUDREAM_SATORI_CREDENTIAL_KEY");
    }

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

    @Test
    void shouldEnsureSatoriUniqueAndTtlIndexesAtStartup() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        IndexOperations loginIndexes = mock(IndexOperations.class);
        IndexOperations eventIndexes = mock(IndexOperations.class);
        when(mongoTemplate.indexOps(SatoriLoginDO.class)).thenReturn(loginIndexes);
        when(mongoTemplate.indexOps(SatoriEventRecordDO.class)).thenReturn(eventIndexes);
        when(loginIndexes.ensureIndex(any(IndexDefinition.class))).thenReturn("satori_login_unique");
        when(eventIndexes.ensureIndex(any(IndexDefinition.class))).thenReturn("satori_event_idempotency", "satori_event_expire_at");

        new SatoriIndexInitializer(mongoTemplate).onApplicationEvent(null);

        org.mockito.ArgumentCaptor<IndexDefinition> login = org.mockito.ArgumentCaptor.forClass(IndexDefinition.class);
        org.mockito.ArgumentCaptor<IndexDefinition> events = org.mockito.ArgumentCaptor.forClass(IndexDefinition.class);
        org.mockito.Mockito.verify(loginIndexes).ensureIndex(login.capture());
        org.mockito.Mockito.verify(eventIndexes, org.mockito.Mockito.times(2)).ensureIndex(events.capture());
        assertThat(login.getValue().getIndexKeys()).containsEntry("connectionId", 1).containsEntry("platform", 1).containsEntry("userId", 1);
        assertThat(login.getValue().getIndexOptions()).containsEntry("unique", true);
        assertThat(events.getAllValues().get(0).getIndexKeys()).containsEntry("connectionId", 1).containsEntry("sequence", 1);
        assertThat(events.getAllValues().get(0).getIndexOptions()).containsEntry("unique", true);
        assertThat(events.getAllValues().get(1).getIndexKeys()).containsEntry("expireAt", 1);
        assertThat(events.getAllValues().get(1).getIndexOptions()).containsEntry("expireAfterSeconds", 0L);
    }
}
