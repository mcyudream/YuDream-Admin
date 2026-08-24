package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.plugin.spi.system.secret.PluginSecretStore;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

final class MongoPluginSecretStore implements PluginSecretStore {
    private static final String COLLECTION = "plugin_secrets";
    private final String pluginCode;
    private final MongoTemplate mongoTemplate;
    private final PluginSecretCipher cipher;

    MongoPluginSecretStore(String pluginCode, MongoTemplate mongoTemplate, String credentialKey, String legacyEncodedKey) {
        this.pluginCode = requireText(pluginCode, "pluginCode");
        this.mongoTemplate = mongoTemplate;
        this.cipher = new PluginSecretCipher(credentialKey, legacyEncodedKey);
    }

    @Override
    public void put(String key, byte[] secret) {
        String safeKey = requireText(key, "key");
        if (secret == null || secret.length == 0) {
            throw new IllegalArgumentException("secret must not be empty");
        }
        PluginSecretCipher.Encrypted encrypted = cipher.encrypt(pluginCode, safeKey, secret);
        mongoTemplate.save(new Document("_id", id(safeKey))
                .append("pluginCode", pluginCode)
                .append("secretKey", safeKey)
                .append("iv", Base64.getEncoder().encodeToString(encrypted.iv()))
                .append("ciphertext", Base64.getEncoder().encodeToString(encrypted.ciphertext()))
                .append("updatedAt", Instant.now().toEpochMilli()), COLLECTION);
    }

    @Override
    public Optional<byte[]> get(String key) {
        String safeKey = requireText(key, "key");
        Document document = mongoTemplate.findById(id(safeKey), Document.class, COLLECTION);
        if (document == null || !pluginCode.equals(document.getString("pluginCode"))) {
            return Optional.empty();
        }
        byte[] iv = Base64.getDecoder().decode(document.getString("iv"));
        byte[] encrypted = Base64.getDecoder().decode(document.getString("ciphertext"));
        return Optional.of(cipher.decrypt(pluginCode, safeKey, iv, encrypted));
    }

    @Override
    public boolean delete(String key) {
        String safeKey = requireText(key, "key");
        Query query = Query.query(Criteria.where("_id").is(id(safeKey)).and("pluginCode").is(pluginCode));
        return mongoTemplate.remove(query, COLLECTION).getDeletedCount() > 0;
    }

    private String id(String key) {
        return pluginCode + ":" + key;
    }

    private static String requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
