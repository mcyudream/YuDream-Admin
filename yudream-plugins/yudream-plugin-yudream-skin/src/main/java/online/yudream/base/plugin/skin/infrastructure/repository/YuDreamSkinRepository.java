package online.yudream.base.plugin.skin.infrastructure.repository;

import online.yudream.base.plugin.skin.domain.aggregate.SkinPlayer;
import online.yudream.base.plugin.skin.domain.aggregate.SkinClosetItem;
import online.yudream.base.plugin.skin.domain.aggregate.SkinTexture;
import online.yudream.base.plugin.skin.domain.aggregate.SkinUser;
import online.yudream.base.plugin.skin.domain.valobj.SkinSiteSettings;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import online.yudream.base.plugin.spi.system.storage.PluginFileStore;
import online.yudream.base.plugin.spi.system.storage.PluginStoredFile;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class YuDreamSkinRepository {

    private static final String USERS = "users";
    private static final String PLAYERS = "players";
    private static final String TEXTURES = "textures";
    private static final String CLOSET = "closet";
    private static final String OPTIONS = "options";
    private static final String OPTION_MAX_PLAYERS = "maxPlayersPerUser";
    private static final String OPTION_PUBLIC_UPLOAD = "allowPublicUpload";
    private static final String OPTION_SITE_NOTICE = "siteNotice";
    private static final String OPTION_DEFAULT_PLAYER_PREFIX = "defaultPlayerUuid:";

    private final PluginDocumentStore documents;
    private final PluginFileStore files;

    public YuDreamSkinRepository(PluginDocumentStore documents, PluginFileStore files) {
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
        return toPlayer(documents.save(PLAYERS, normalizeUuid(player.uuid()), playerDocument(player)));
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

    public void deletePlayer(String uuid) {
        documents.delete(PLAYERS, normalizeUuid(uuid));
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

    public void deleteTexture(SkinTexture texture) {
        if (texture == null) {
            return;
        }
        documents.delete(TEXTURES, texture.hash());
        if (texture.objectKey() != null && !texture.objectKey().isBlank()) {
            files.delete(texture.objectKey());
        }
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

    public SkinClosetItem saveClosetItem(String userId, String textureHash, String itemName) {
        return saveClosetItem(new SkinClosetItem(closetId(userId, textureHash), userId, textureHash, itemName, System.currentTimeMillis()));
    }

    public SkinClosetItem saveClosetItem(SkinClosetItem item) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("userId", item.userId());
        document.put("textureHash", item.textureHash());
        document.put("itemName", item.itemName());
        document.put("createdAt", item.createdAt());
        return toClosetItem(documents.save(CLOSET, item.id(), document));
    }

    public List<SkinClosetItem> listClosetItems(int page, int size) {
        return documents.findAll(CLOSET, page, size).stream().map(this::toClosetItem).toList();
    }

    public List<SkinClosetItem> findClosetByUser(String userId, int page, int size) {
        return documents.findByField(CLOSET, "userId", userId, page, size).stream().map(this::toClosetItem).toList();
    }

    public Optional<SkinClosetItem> findClosetItem(String id) {
        return documents.findById(CLOSET, id).map(this::toClosetItem);
    }

    public Optional<SkinClosetItem> findClosetByUserAndTexture(String userId, String textureHash) {
        return findClosetItem(closetId(userId, textureHash));
    }

    public List<SkinClosetItem> findClosetByTexture(String textureHash) {
        return documents.findByField(CLOSET, "textureHash", textureHash, 1, 10000).stream().map(this::toClosetItem).toList();
    }

    public void deleteClosetItem(String id) {
        documents.delete(CLOSET, id);
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

    public void deleteOption(String key) {
        documents.delete(OPTIONS, key);
    }

    public Optional<String> findDefaultPlayerUuid(String ownerId) {
        return Optional.ofNullable(optionValue(defaultPlayerOptionKey(ownerId), null))
                .filter(value -> !value.isBlank());
    }

    public void saveDefaultPlayerUuid(String ownerId, String uuid) {
        if (uuid == null || uuid.isBlank()) {
            deleteOption(defaultPlayerOptionKey(ownerId));
            return;
        }
        saveOption(defaultPlayerOptionKey(ownerId), normalizeUuid(uuid));
    }

    public SkinSiteSettings settings() {
        SkinSiteSettings defaults = SkinSiteSettings.defaults();
        return new SkinSiteSettings(
                intOption(OPTION_MAX_PLAYERS, defaults.maxPlayersPerUser()),
                boolOption(OPTION_PUBLIC_UPLOAD, defaults.allowPublicUpload()),
                optionValue(OPTION_SITE_NOTICE, defaults.siteNotice())
        );
    }

    public SkinSiteSettings saveSettings(SkinSiteSettings settings) {
        SkinSiteSettings normalized = settings == null ? SkinSiteSettings.defaults() : settings;
        saveOption(OPTION_MAX_PLAYERS, String.valueOf(normalized.safeMaxPlayersPerUser()));
        saveOption(OPTION_PUBLIC_UPLOAD, String.valueOf(normalized.publicUploadEnabled()));
        saveOption(OPTION_SITE_NOTICE, normalized.siteNotice() == null ? "" : normalized.siteNotice());
        return settings();
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

    private SkinClosetItem toClosetItem(Map<String, Object> document) {
        return new SkinClosetItem(
                string(document, "id"),
                string(document, "userId"),
                string(document, "textureHash"),
                string(document, "itemName"),
                number(document, "createdAt")
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

    private String optionValue(String key, String defaultValue) {
        return documents.findById(OPTIONS, key)
                .map(document -> string(document, "value"))
                .filter(value -> !value.isBlank())
                .orElse(defaultValue);
    }

    private Integer intOption(String key, Integer defaultValue) {
        String value = optionValue(key, defaultValue == null ? null : String.valueOf(defaultValue));
        return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value);
    }

    private Boolean boolOption(String key, Boolean defaultValue) {
        String value = optionValue(key, defaultValue == null ? null : String.valueOf(defaultValue));
        return value == null || value.isBlank() ? defaultValue : Boolean.parseBoolean(value);
    }

    private String lower(String value) {
        return value == null ? null : value.trim().toLowerCase();
    }

    private String normalizeUuid(String uuid) {
        return uuid == null ? null : uuid.replace("-", "").toLowerCase(Locale.ROOT);
    }

    private String closetId(String userId, String textureHash) {
        return userId + ":" + textureHash;
    }

    private String defaultPlayerOptionKey(String ownerId) {
        return OPTION_DEFAULT_PLAYER_PREFIX + ownerId;
    }
}
