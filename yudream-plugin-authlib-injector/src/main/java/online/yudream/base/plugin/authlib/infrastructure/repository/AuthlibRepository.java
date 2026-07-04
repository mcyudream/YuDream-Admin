package online.yudream.base.plugin.authlib.infrastructure.repository;

import online.yudream.base.plugin.authlib.domain.aggregate.AuthSession;
import online.yudream.base.plugin.authlib.domain.aggregate.ServerJoin;
import online.yudream.base.plugin.authlib.domain.valobj.AuthlibKeyPair;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AuthlibRepository {

    private static final String SESSIONS = "sessions";
    private static final String JOINS = "joins";
    private static final String SETTINGS = "settings";

    private final PluginDocumentStore documents;

    public AuthlibRepository(PluginDocumentStore documents) {
        this.documents = documents;
    }

    public AuthSession saveSession(AuthSession session) {
        return toSession(documents.save(SESSIONS, session.accessToken(), sessionDocument(session)));
    }

    public Optional<AuthSession> findSession(String accessToken) {
        return documents.findById(SESSIONS, accessToken).map(this::toSession);
    }

    public List<AuthSession> findSessionsByUser(String userId) {
        return documents.findByField(SESSIONS, "userId", userId, 1, 200).stream().map(this::toSession).toList();
    }

    public void deleteSession(String accessToken) {
        documents.delete(SESSIONS, accessToken);
    }

    public ServerJoin saveJoin(ServerJoin join) {
        return toJoin(documents.save(JOINS, join.id(), joinDocument(join)));
    }

    public List<ServerJoin> findJoinsByServer(String serverId) {
        return documents.findByField(JOINS, "serverId", serverId, 1, 200).stream().map(this::toJoin).toList();
    }

    public Optional<AuthlibKeyPair> keyPair() {
        return documents.findById(SETTINGS, "keypair")
                .map(document -> new AuthlibKeyPair(string(document, "publicKey"), string(document, "privateKey")));
    }

    public AuthlibKeyPair saveKeyPair(AuthlibKeyPair keyPair) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("publicKey", keyPair.publicKey());
        document.put("privateKey", keyPair.privateKey());
        Map<String, Object> saved = documents.save(SETTINGS, "keypair", document);
        return new AuthlibKeyPair(string(saved, "publicKey"), string(saved, "privateKey"));
    }

    private Map<String, Object> sessionDocument(AuthSession session) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("clientToken", session.clientToken());
        document.put("userId", session.userId());
        document.put("username", session.username());
        document.put("selectedProfileId", session.selectedProfileId());
        document.put("issuedAt", session.issuedAt());
        document.put("expiresAt", session.expiresAt());
        return document;
    }

    private Map<String, Object> joinDocument(ServerJoin join) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("serverId", join.serverId());
        document.put("profileId", join.profileId());
        document.put("username", join.username());
        document.put("accessToken", join.accessToken());
        document.put("expiresAt", join.expiresAt());
        return document;
    }

    private AuthSession toSession(Map<String, Object> document) {
        return new AuthSession(string(document, "id"), string(document, "clientToken"), string(document, "userId"),
                string(document, "username"), string(document, "selectedProfileId"), number(document, "issuedAt"),
                number(document, "expiresAt"));
    }

    private ServerJoin toJoin(Map<String, Object> document) {
        return new ServerJoin(string(document, "id"), string(document, "serverId"), string(document, "profileId"),
                string(document, "username"), string(document, "accessToken"), number(document, "expiresAt"));
    }

    private String string(Map<String, Object> document, String key) {
        Object value = document.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Long number(Map<String, Object> document, String key) {
        Object value = document.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null || String.valueOf(value).isBlank() ? null : Long.parseLong(String.valueOf(value));
    }
}
