package online.yudream.base.plugin.blessing.infrastructure.repository;

import online.yudream.base.plugin.blessing.domain.aggregate.SkinPlayer;
import online.yudream.base.plugin.blessing.domain.aggregate.SkinTexture;
import online.yudream.base.plugin.blessing.domain.aggregate.SkinUser;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import online.yudream.base.plugin.spi.system.storage.PluginFileStore;
import online.yudream.base.plugin.spi.system.storage.PluginStoredFile;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BlessingSkinRepository {

    private static final String USERS = "users";
    private static final String PLAYERS = "players";
    private static final String TEXTURES = "textures";
    private static final String CLOSET = "closet";
    private static final String OPTIONS = "options";

    private final PluginDocumentStore documents;
    private final PluginFileStore files;

    public BlessingSkinRepository(PluginDocumentStore documents, PluginFileStore files) {
        this.documents = documents;
        this.files = files;
    }

    public SkinUser saveUser(SkinUser user) {
        return toUser(documents.save(USERS, user.id(), userDocument(user)));
    }

    public Optional<SkinUser> findUserById(String id) {
        return documents.findById(USERS, id).map(this::toUser);
    }

    public Optional<SkinUser> findUserByEmail(String email) {
        return documents.findByField(USERS, "emailLower", lower(email), 1, 1).stream().findFirst().map(this::toUser);
    }

    public Optional<SkinUser> findUserByNickname(String nickname) {
        return documents.findByField(USERS, "nicknameLower", lower(nickname), 1, 1).stream().findFirst().map(this::toUser);
    }

    public List<SkinUser> listUsers(int page, int size) {
        return documents.findAll(USERS, page, size).stream().map(this::toUser).toList();
    }

    public long userCount() {
        return documents.count(USERS);
    }

    public SkinPlayer savePlayer(SkinPlayer player) {
        return toPlayer(documents.save(PLAYERS, player.uuid(), playerDocument(player)));
    }

    public Optional<SkinPlayer> findPlayerByUuid(String uuid) {
        return documents.findById(PLAYERS, normalizeUuid(uuid)).map(this::toPlayer);
    }

    public Optional<SkinPlayer> findPlayerByName(String name) {
        return documents.findByField(PLAYERS, "nameLower", lower(name), 1, 1).stream().findFirst().map(this::toPlayer);
    }

    public List<SkinPlayer> findPlayersByOwner(String ownerId) {
        return documents.findByField(PLAYERS, "ownerId", ownerId, 1, 200).stream().map(this::toPlayer).toList();
    }

    public List<SkinPlayer> listPlayers(int page, int size) {
        return documents.findAll(PLAYERS, page, size).stream().map(this::toPlayer).toList();
    }

    public long playerCount() {
        return documents.count(PLAYERS);
    }

    public SkinTexture saveTexture(SkinTexture texture) {
        return toTexture(documents.save(TEXTURES, texture.hash(), textureDocument(texture)));
    }

    public Optional<SkinTexture> findTextureByHash(String hash) {
        return documents.findById(TEXTURES, hash).map(this::toTexture);
    }

    public List<SkinTexture> listTextures(int page, int size) {
        return documents.findAll(TEXTURES, page, size).stream().map(this::toTexture).toList();
    }

    public long textureCount() {
        return documents.count(TEXTURES);
    }

    public String saveTextureFile(String hash, byte[] bytes, String contentType) {
        return files.put("textures/" + hash, new ByteArrayInputStream(bytes), bytes.length, contentType);
    }

    public Optional<PluginStoredFile> readTextureFile(SkinTexture texture) {
        if (texture == null || texture.objectKey() == null || texture.objectKey().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(files.get(texture.objectKey()));
    }

    public void saveClosetItem(String userId, String textureHash, String itemName) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("userId", userId);
        document.put("textureHash", textureHash);
        document.put("itemName", itemName);
        documents.save(CLOSET, userId + ":" + textureHash, document);
    }

    public long closetCount() {
        return documents.count(CLOSET);
    }

    public void saveOption(String key, String value) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("key", key);
        document.put("value", value);
        documents.save(OPTIONS, key, document);
    }

    public long optionCount() {
        return documents.count(OPTIONS);
    }

    private Map<String, Object> userDocument(SkinUser user) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("email", user.email());
        document.put("emailLower", user.emailLower());
        document.put("nickname", user.nickname());
        document.put("nicknameLower", lower(user.nickname()));
        document.put("passwordHash", user.passwordHash());
        document.put("migratedUid", user.migratedUid());
        document.put("createdAt", user.createdAt());
        return document;
    }

    private Map<String, Object> playerDocument(SkinPlayer player) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("ownerId", player.ownerId());
        document.put("name", player.name());
        document.put("nameLower", player.nameLower());
        document.put("skinHash", player.skinHash());
        document.put("capeHash", player.capeHash());
        document.put("migratedPid", player.migratedPid());
        document.put("lastModified", player.lastModified());
        return document;
    }

    private Map<String, Object> textureDocument(SkinTexture texture) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("name", texture.name());
        document.put("type", texture.type());
        document.put("model", texture.model());
        document.put("contentType", texture.contentType());
        document.put("size", texture.size());
        document.put("uploaderId", texture.uploaderId());
        document.put("publicAccess", texture.publicAccess());
        document.put("objectKey", texture.objectKey());
        document.put("migratedTid", texture.migratedTid());
        document.put("uploadedAt", texture.uploadedAt());
        return document;
    }

    private SkinUser toUser(Map<String, Object> document) {
        return new SkinUser(
                string(document, "id"),
                string(document, "email"),
                string(document, "emailLower"),
                string(document, "nickname"),
                string(document, "passwordHash"),
                number(document, "migratedUid"),
                number(document, "createdAt")
        );
    }

    private SkinPlayer toPlayer(Map<String, Object> document) {
        return new SkinPlayer(
                string(document, "id"),
                string(document, "ownerId"),
                string(document, "name"),
                string(document, "nameLower"),
                string(document, "skinHash"),
                string(document, "capeHash"),
                number(document, "migratedPid"),
                number(document, "lastModified")
        );
    }

    private SkinTexture toTexture(Map<String, Object> document) {
        return new SkinTexture(
                string(document, "id"),
                string(document, "name"),
                string(document, "type"),
                string(document, "model"),
                string(document, "contentType"),
                number(document, "size"),
                string(document, "uploaderId"),
                bool(document, "publicAccess"),
                string(document, "objectKey"),
                number(document, "migratedTid"),
                number(document, "uploadedAt")
        );
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
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private Boolean bool(Map<String, Object> document, String key) {
        Object value = document.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null ? null : Boolean.parseBoolean(String.valueOf(value));
    }

    private String lower(String value) {
        return value == null ? null : value.trim().toLowerCase();
    }

    private String normalizeUuid(String uuid) {
        return uuid == null ? null : uuid.replace("-", "");
    }
}
