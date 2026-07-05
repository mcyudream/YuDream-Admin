package online.yudream.base.plugin.blessing.infrastructure.service;

import online.yudream.base.plugin.blessing.domain.aggregate.SkinPlayer;
import online.yudream.base.plugin.blessing.domain.aggregate.SkinTexture;
import online.yudream.base.plugin.blessing.domain.aggregate.SkinUser;
import online.yudream.base.plugin.blessing.domain.enumerate.SkinTextureType;
import online.yudream.base.plugin.blessing.domain.valobj.MigrationConfig;
import online.yudream.base.plugin.blessing.domain.valobj.MigrationReport;
import online.yudream.base.plugin.blessing.infrastructure.repository.BlessingSkinRepository;
import online.yudream.base.plugin.blessing.infrastructure.support.HashSupport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BlessingSkinMigrationService {

    private final BlessingSkinRepository repository;

    public BlessingSkinMigrationService(BlessingSkinRepository repository) {
        this.repository = repository;
    }

    public MigrationReport migrate(MigrationConfig request) {
        if (request == null || request.jdbcUrl() == null || request.jdbcUrl().isBlank()) {
            throw new IllegalArgumentException("JDBC 地址不能为空");
        }
        if (request.driverClass() != null && !request.driverClass().isBlank()) {
            loadDriver(request.driverClass());
        }
        List<String> warnings = new ArrayList<>();
        Map<Long, String> textureHashByTid = new HashMap<>();
        try (Connection connection = DriverManager.getConnection(request.jdbcUrl(), request.username(), request.password())) {
            int users = migrateUsers(connection);
            int textures = migrateTextures(connection, request.textureBaseDir(), textureHashByTid, warnings);
            int players = migratePlayers(connection, textureHashByTid, warnings);
            int closet = migrateCloset(connection, textureHashByTid, warnings);
            int options = migrateOptions(connection);
            return new MigrationReport(users, players, textures, closet, options, warnings);
        } catch (Exception e) {
            throw new IllegalStateException("Blessing Skin 数据迁移失败：" + e.getMessage(), e);
        }
    }

    private int migrateUsers(Connection connection) throws Exception {
        int count = 0;
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select * from users")) {
            while (rs.next()) {
                long uid = longValue(rs, "uid", count + 1);
                String email = stringValue(rs, "email", "user" + uid + "@legacy.local");
                String nickname = stringValue(rs, "nickname", email);
                repository.saveUser(new SkinUser(
                        String.valueOf(uid),
                        email,
                        email.toLowerCase(Locale.ROOT),
                        nickname,
                        stringValue(rs, "password", ""),
                        uid,
                        millis(rs, "register_at")
                ));
                count++;
            }
        }
        return count;
    }

    private int migrateTextures(Connection connection, String textureBaseDir, Map<Long, String> textureHashByTid,
                                List<String> warnings) throws Exception {
        int count = 0;
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select * from textures")) {
            while (rs.next()) {
                long tid = longValue(rs, "tid", count + 1);
                String hash = stringValue(rs, "hash", "");
                if (hash.isBlank()) {
                    warnings.add("跳过无 hash 材质 tid=" + tid);
                    continue;
                }
                String objectKey = importTextureFile(textureBaseDir, hash, warnings);
                SkinTextureType type = SkinTextureType.from(stringValue(rs, "type", "steve"));
                repository.saveTexture(new SkinTexture(
                        hash,
                        stringValue(rs, "name", hash),
                        type.yggdrasilType(),
                        type.model(),
                        "image/png",
                        longValue(rs, "size", 0L),
                        String.valueOf(longValue(rs, "uploader", 0L)),
                        intValue(rs, "public", 0) != 0,
                        objectKey,
                        tid,
                        millis(rs, "upload_at")
                ));
                textureHashByTid.put(tid, hash);
                count++;
            }
        }
        return count;
    }

    private int migratePlayers(Connection connection, Map<Long, String> textureHashByTid, List<String> warnings) throws Exception {
        int count = 0;
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select * from players")) {
            while (rs.next()) {
                String name = firstString(rs, "name", "player_name");
                if (name == null || name.isBlank()) {
                    warnings.add("跳过无名称角色 pid=" + longValue(rs, "pid", 0L));
                    continue;
                }
                long pid = longValue(rs, "pid", count + 1);
                String ownerId = String.valueOf(longValue(rs, "uid", 0L));
                String skinHash = textureHashByTid.get(longValue(rs, "tid_skin", 0L));
                String capeHash = textureHashByTid.get(longValue(rs, "tid_cape", 0L));
                repository.savePlayer(new SkinPlayer(
                        HashSupport.playerUuid(name),
                        ownerId,
                        name,
                        name.toLowerCase(Locale.ROOT),
                        skinHash,
                        capeHash,
                        pid,
                        millis(rs, "last_modified")
                ));
                count++;
            }
        }
        return count;
    }

    private int migrateCloset(Connection connection, Map<Long, String> textureHashByTid, List<String> warnings) {
        int count = 0;
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select * from user_closet")) {
            while (rs.next()) {
                String userId = String.valueOf(longValue(rs, "user_uid", 0L));
                String textureHash = textureHashByTid.get(longValue(rs, "texture_tid", 0L));
                if (textureHash == null) {
                    warnings.add("跳过无材质衣柜项 user=" + userId);
                    continue;
                }
                repository.saveClosetItem(userId, textureHash, stringValue(rs, "item_name", null));
                count++;
            }
        } catch (Exception ignored) {
            warnings.add("未发现 user_closet 表，已跳过衣柜迁移");
        }
        return count;
    }

    private int migrateOptions(Connection connection) throws Exception {
        int count = 0;
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select * from options")) {
            while (rs.next()) {
                repository.saveOption(stringValue(rs, "option_name", "option_" + count), stringValue(rs, "option_value", ""));
                count++;
            }
        }
        return count;
    }

    private String importTextureFile(String textureBaseDir, String hash, List<String> warnings) {
        if (textureBaseDir == null || textureBaseDir.isBlank()) {
            return null;
        }
        try {
            Path path = Path.of(textureBaseDir, hash);
            if (!Files.exists(path)) {
                path = Path.of(textureBaseDir, hash + ".png");
            }
            if (!Files.exists(path)) {
                warnings.add("材质文件不存在：" + hash);
                return null;
            }
            byte[] bytes = Files.readAllBytes(path);
            return repository.saveTextureFile(hash, bytes, "image/png");
        } catch (Exception e) {
            warnings.add("材质文件导入失败 " + hash + "：" + e.getMessage());
            return null;
        }
    }

    private void loadDriver(String driverClass) {
        try {
            Class.forName(driverClass);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("JDBC 驱动不存在，请将驱动加入运行时 classpath：" + driverClass, e);
        }
    }

    private String firstString(ResultSet rs, String... names) throws Exception {
        for (String name : names) {
            if (hasColumn(rs, name)) {
                return rs.getString(name);
            }
        }
        return null;
    }

    private String stringValue(ResultSet rs, String name, String defaultValue) throws Exception {
        return hasColumn(rs, name) ? rs.getString(name) : defaultValue;
    }

    private long longValue(ResultSet rs, String name, long defaultValue) throws Exception {
        return hasColumn(rs, name) ? rs.getLong(name) : defaultValue;
    }

    private int intValue(ResultSet rs, String name, int defaultValue) throws Exception {
        return hasColumn(rs, name) ? rs.getInt(name) : defaultValue;
    }

    private Long millis(ResultSet rs, String name) throws Exception {
        if (!hasColumn(rs, name) || rs.getTimestamp(name) == null) {
            return Instant.now().toEpochMilli();
        }
        return rs.getTimestamp(name).toInstant().toEpochMilli();
    }

    private boolean hasColumn(ResultSet rs, String name) throws Exception {
        ResultSetMetaData metaData = rs.getMetaData();
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            if (metaData.getColumnLabel(i).equalsIgnoreCase(name) || metaData.getColumnName(i).equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }
}
