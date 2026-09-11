package online.yudream.base.domain.platform.milky.aggregate;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.milky.enumerate.MilkyConnectionProtocol;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MilkyConnectionTest {

    @Test
    void milkyCreateStillRequiresAddressAndToken() {
        MilkyConnection connection = MilkyConnection.create("本地", "http://127.0.0.1:3010", "token", "base64", null);
        assertEquals(MilkyConnectionProtocol.MILKY, connection.protocolOrDefault());
        assertEquals("http://127.0.0.1:3010", connection.getBaseUrl());
        assertTrue(connection.credentialConfigured());
        assertFalse(connection.official());
    }

    @Test
    void officialCreateUsesAppCredentialsAndDefaultApiHost() {
        MilkyConnection connection = MilkyConnection.create("官方", "official", null, null,
                "app-id", "app-secret", false, null, "base64", null);
        assertTrue(connection.official());
        assertEquals("app-id", connection.getAppId());
        assertEquals(MilkyConnectionProtocol.OFFICIAL_API, connection.getBaseUrl());
        assertEquals("official", connection.toApiContext().protocol());
        assertTrue(connection.toApiContext().official());
    }

    @Test
    void officialSandboxDefaultsToSandboxHost() {
        MilkyConnection connection = MilkyConnection.create("沙盒", "official", " ", null,
                "app-id", "app-secret", true, 1, "url", "https://admin.example.com");
        assertEquals(MilkyConnectionProtocol.OFFICIAL_SANDBOX_API, connection.getBaseUrl());
        assertTrue(connection.isSandbox());
        assertEquals(1, connection.officialIntents());
    }

    @Test
    void bindMentionOpenIdTrimsAndDeduplicates() {
        MilkyConnection connection = MilkyConnection.create("官方", "official", null, null,
                "app-id", "app-secret", false, null, "base64", null, List.of(" OPENID-A ", "OPENID-B", "OPENID-A"));
        assertEquals(List.of("OPENID-A", "OPENID-B"), connection.officialMentionOpenIds());
        connection.bindMentionOpenId("openid-c");
        connection.bindMentionOpenId("OPENID-A");
        assertEquals(List.of("OPENID-A", "OPENID-B", "openid-c"), connection.officialMentionOpenIds());
        assertThrows(BizException.class, () -> connection.bindMentionOpenId(" "));
    }

    @Test
    void officialUpdateKeepsSecretWhenBlank() {
        MilkyConnection connection = MilkyConnection.create("官方", "official", null, null,
                "app-id", "app-secret", false, null, "base64", null);
        connection.update("官方2", "official", null, null, "app-id-2", "  ", false, 8, "base64", null);
        assertEquals("官方2", connection.getName());
        assertEquals("app-id-2", connection.getAppId());
        assertEquals("app-secret", connection.getAppSecret());
        assertEquals(8, connection.getIntents());
    }

    @Test
    void officialCreateRejectsMissingSecret() {
        assertThrows(BizException.class, () -> MilkyConnection.create("官方", "official", null, null,
                "app-id", " ", false, null, "base64", null));
    }

    @Test
    void unknownProtocolRejected() {
        assertThrows(BizException.class, () -> MilkyConnectionProtocol.from("satori"));
    }
}
