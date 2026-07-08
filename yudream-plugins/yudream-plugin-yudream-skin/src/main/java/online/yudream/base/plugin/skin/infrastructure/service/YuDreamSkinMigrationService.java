package online.yudream.base.plugin.skin.infrastructure.service;

import online.yudream.base.plugin.skin.domain.aggregate.SkinPlayer;
import online.yudream.base.plugin.skin.domain.aggregate.SkinTexture;
import online.yudream.base.plugin.skin.domain.enumerate.SkinTextureType;
import online.yudream.base.plugin.skin.domain.valobj.MigrationConfig;
import online.yudream.base.plugin.skin.domain.valobj.MigrationLogEntry;
import online.yudream.base.plugin.skin.domain.valobj.MigrationReport;
import online.yudream.base.plugin.skin.domain.valobj.MigrationStatus;
import online.yudream.base.plugin.skin.infrastructure.repository.YuDreamSkinRepository;
import online.yudream.base.plugin.skin.infrastructure.support.HashSupport;
import online.yudream.base.plugin.spi.http.PluginSseStream;
import online.yudream.base.plugin.spi.system.user.PluginUserCreate;
import online.yudream.base.plugin.spi.system.user.PluginUserProfile;
import online.yudream.base.plugin.spi.system.user.PluginUserService;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class YuDreamSkinMigrationService {

    private static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";

    private final YuDreamSkinRepository repository;
    private final PluginUserService users;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "yudream-skin-migration");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicReference<MigrationTask> currentTask = new AtomicReference<>();

    public YuDreamSkinMigrationService(YuDreamSkinRepository repository, PluginUserService users) {
        this.repository = repository;
        this.users = users;
    }

    public MigrationReport migrate(MigrationConfig request) {
        return migrate(request, ignored -> {
        });
    }

    public MigrationStatus start(MigrationConfig request) {
        MigrationTask existing = currentTask.get();
        if (existing != null && existing.running()) {
            throw new IllegalArgumentException("迁移正在执行中，请先查看当前日志");
        }
        MigrationTask task = new MigrationTask();
        currentTask.set(task);
        executor.submit(() -> runTask(task, request));
        return task.status();
    }

    public MigrationStatus status() {
        MigrationTask task = currentTask.get();
        return task == null ? new MigrationStatus("IDLE", false, null, null, null, List.of()) : task.status();
    }

    public PluginSseStream events() {
        MigrationTask task = currentTask.get();
        return task == null ? MigrationTask.empty() : task;
    }

    private void runTask(MigrationTask task, MigrationConfig request) {
        task.start();
        try {
            task.finish(migrate(request, task::info));
        } catch (Exception e) {
            task.fail(e);
        }
    }

    private MigrationReport migrate(MigrationConfig request, Consumer<String> logger) {
        String jdbcUrl = jdbcUrl(request);
        loadDriver();
        List<String> warnings = new ArrayList<>();
        Map<Long, String> userIdByUid = new HashMap<>();
        Map<Long, String> textureHashByTid = new HashMap<>();
        Map<Long, String> playerUuidByPid = new HashMap<>();
        Map<String, String> playerUuidByName = new HashMap<>();
        Map<String, byte[]> textureArchive = textureArchive(request, warnings, logger);
        try (Connection connection = DriverManager.getConnection(jdbcUrl, request.username(), request.password())) {
            loadPlayerUuids(connection, playerUuidByPid, playerUuidByName, warnings, logger);
            logger.accept("已连接 Blessing Skin MySQL 数据库");
            logger.accept("开始迁移用户");
            int users = migrateUsers(connection, userIdByUid, warnings, logger);
            logger.accept("用户迁移完成：" + users);
            logger.accept("开始迁移材质");
            int textures = migrateTextures(connection, request.textureBaseDir(), textureArchive, userIdByUid, textureHashByTid, warnings);
            logger.accept("材质迁移完成：" + textures);
            logger.accept("开始迁移角色");
            int players = migratePlayers(connection, userIdByUid, textureHashByTid, playerUuidByPid, playerUuidByName, warnings);
            logger.accept("角色迁移完成：" + players);
            logger.accept("开始迁移衣柜");
            int closet = migrateCloset(connection, userIdByUid, textureHashByTid, warnings);
            logger.accept("衣柜迁移完成：" + closet);
            logger.accept("开始迁移配置");
            int options = migrateOptions(connection);
            logger.accept("配置迁移完成：" + options);
            warnings.forEach(warning -> logger.accept("警告：" + warning));
            return new MigrationReport(users, players, textures, closet, options, warnings);
        } catch (Exception e) {
            throw new IllegalStateException("Blessing Skin 数据迁移失败：" + e.getMessage(), e);
        }
    }

    private String jdbcUrl(MigrationConfig request) {
        if (request == null || request.host() == null || request.host().isBlank()) {
            throw new IllegalArgumentException("数据库主机不能为空");
        }
        String database = request.database() == null || request.database().isBlank() ? "blessing_skin" : request.database().trim();
        int port = request.port() == null || request.port() <= 0 ? 3306 : request.port();
        return "jdbc:mysql://" + request.host().trim() + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
    }

    private int migrateUsers(Connection connection, Map<Long, String> userIdByUid, List<String> warnings, Consumer<String> logger) throws Exception {
        int count = 0;
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select * from users")) {
            while (rs.next()) {
                long uid = longValue(rs, "uid", count + 1);
                String email = stringValue(rs, "email", "user" + uid + "@legacy.local");
                String nickname = stringValue(rs, "nickname", email);
                String passwordHash = firstString(rs, "password", "password_hash", "passhash", "pwd");
                Optional<PluginUserProfile> hostUser = resolveHostUser(uid, email, nickname);
                if (hostUser.isEmpty()) {
                    hostUser = Optional.of(createHostUser(uid, email, nickname, passwordHash, warnings, logger));
                }
                userIdByUid.put(uid, String.valueOf(hostUser.get().id()));
                count++;
            }
        }
        return count;
    }

    private int migrateTextures(Connection connection, String textureBaseDir, Map<String, byte[]> textureArchive,
                                Map<Long, String> userIdByUid, Map<Long, String> textureHashByTid, List<String> warnings) throws Exception {
        int count = 0;
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select * from textures")) {
            while (rs.next()) {
                long tid = longValue(rs, "tid", count + 1);
                String hash = stringValue(rs, "hash", "");
                if (hash.isBlank()) {
                    warnings.add("跳过缺少 hash 的材质 tid=" + tid);
                    continue;
                }
                String objectKey = importTextureFile(textureBaseDir, textureArchive, hash, warnings);
                SkinTextureType type = SkinTextureType.from(stringValue(rs, "type", "steve"));
                long uploaderUid = longValue(rs, "uploader", 0L);
                String uploaderId = userIdByUid.get(uploaderUid);
                if (uploaderId == null) {
                    uploaderId = "system";
                    warnings.add("材质上传者未匹配系统用户，按 system 导入 tid=" + tid + " uploader=" + uploaderUid);
                }
                repository.saveTexture(new SkinTexture(
                        hash,
                        stringValue(rs, "name", hash),
                        type.yggdrasilType(),
                        type.model(),
                        "image/png",
                        longValue(rs, "size", 0L),
                        uploaderId,
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

    private int migratePlayers(Connection connection, Map<Long, String> userIdByUid, Map<Long, String> textureHashByTid,
                               Map<Long, String> playerUuidByPid, Map<String, String> playerUuidByName, List<String> warnings) throws Exception {
        int count = 0;
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select * from players")) {
            while (rs.next()) {
                String name = firstString(rs, "name", "player_name");
                if (name == null || name.isBlank()) {
                    warnings.add("跳过缺少名称的角色 pid=" + longValue(rs, "pid", 0L));
                    continue;
                }
                long pid = longValue(rs, "pid", count + 1);
                long ownerUid = longValue(rs, "uid", 0L);
                String ownerId = userIdByUid.get(ownerUid);
                if (ownerId == null) {
                    warnings.add("跳过未匹配系统用户的角色 pid=" + pid + " uid=" + ownerUid);
                    continue;
                }
                String skinHash = textureHashByTid.get(longValue(rs, "tid_skin", 0L));
                String capeHash = textureHashByTid.get(longValue(rs, "tid_cape", 0L));
                String uuid = resolvePlayerUuid(rs, pid, name, playerUuidByPid, playerUuidByName, warnings);
                repository.savePlayer(new SkinPlayer(
                        uuid,
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

    private void loadPlayerUuids(Connection connection, Map<Long, String> playerUuidByPid, Map<String, String> playerUuidByName,
                                 List<String> warnings, Consumer<String> logger) {
        try {
            if (!hasTable(connection, "uuid")) {
                logger.accept("未发现 Blessing Skin uuid 表，角色 UUID 将按名称生成");
                return;
            }
            int count = 0;
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("select * from uuid")) {
                while (rs.next()) {
                    String uuid = normalizePlayerUuid(firstString(rs, "uuid", "player_uuid", "profile_uuid"));
                    if (!hasText(uuid)) {
                        warnings.add("跳过缺少有效 UUID 的旧 uuid 表记录 id=" + longValue(rs, "id", 0L));
                        continue;
                    }
                    Long pid = nullableLong(rs, "pid");
                    if (pid != null && pid > 0) {
                        playerUuidByPid.putIfAbsent(pid, uuid);
                    }
                    String name = firstString(rs, "name", "player_name");
                    if (hasText(name)) {
                        playerUuidByName.putIfAbsent(lowerKey(name), uuid);
                    }
                    count++;
                }
            }
            logger.accept("已读取 Blessing Skin 旧角色 UUID：" + count);
        } catch (Exception e) {
            warnings.add("旧角色 UUID 表读取失败，将按角色名生成 UUID：" + e.getMessage());
        }
    }

    private String resolvePlayerUuid(ResultSet rs, long pid, String name, Map<Long, String> playerUuidByPid,
                                     Map<String, String> playerUuidByName, List<String> warnings) throws Exception {
        String uuid = normalizePlayerUuid(firstString(rs, "uuid", "player_uuid", "profile_uuid"));
        if (!hasText(uuid)) {
            uuid = playerUuidByPid.get(pid);
        }
        if (!hasText(uuid)) {
            uuid = playerUuidByName.get(lowerKey(name));
        }
        if (hasText(uuid)) {
            return uuid;
        }
        warnings.add("未找到旧角色 UUID，已按名称生成新 UUID：pid=" + pid + " name=" + name);
        return HashSupport.playerUuid(name);
    }

    private int migrateCloset(Connection connection, Map<Long, String> userIdByUid, Map<Long, String> textureHashByTid, List<String> warnings) {
        int count = 0;
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select * from user_closet")) {
            while (rs.next()) {
                long userUid = longValue(rs, "user_uid", 0L);
                String userId = userIdByUid.get(userUid);
                if (userId == null) {
                    warnings.add("跳过未匹配系统用户的衣柜项 user=" + userUid);
                    continue;
                }
                String textureHash = textureHashByTid.get(longValue(rs, "texture_tid", 0L));
                if (textureHash == null) {
                    warnings.add("跳过缺少材质的衣柜项 user=" + userId);
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

    private Map<String, byte[]> textureArchive(MigrationConfig request, List<String> warnings, Consumer<String> logger) {
        if (request.textureArchiveBase64() == null || request.textureArchiveBase64().isBlank()) {
            return Map.of();
        }
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(request.textureArchiveBase64())))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String fileName = fileName(entry.getName());
                if (!fileName.isBlank()) {
                    files.put(fileName.toLowerCase(Locale.ROOT), zip.readAllBytes());
                }
            }
            logger.accept("已读取材质压缩包：" + archiveName(request) + "，文件数 " + files.size());
        } catch (Exception e) {
            warnings.add("材质压缩包读取失败：" + e.getMessage());
        }
        return files;
    }

    private String importTextureFile(String textureBaseDir, Map<String, byte[]> textureArchive, String hash, List<String> warnings) {
        byte[] archived = textureArchive.get(hash.toLowerCase(Locale.ROOT));
        if (archived == null) {
            archived = textureArchive.get((hash + ".png").toLowerCase(Locale.ROOT));
        }
        if (archived != null) {
            return repository.saveTextureFile(hash, archived, "image/png");
        }
        if (!textureArchive.isEmpty()) {
            warnings.add("材质压缩包中未找到文件：" + hash);
        }
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

    private String archiveName(MigrationConfig request) {
        return request.textureArchiveName() == null || request.textureArchiveName().isBlank()
                ? "textures.zip"
                : request.textureArchiveName();
    }

    private String fileName(String path) {
        String normalized = path == null ? "" : path.replace('\\', '/');
        int index = normalized.lastIndexOf('/');
        return index >= 0 ? normalized.substring(index + 1) : normalized;
    }

    private Optional<PluginUserProfile> resolveHostUser(long legacyUid, String email, String nickname) {
        if (email != null && !email.isBlank()) {
            Optional<PluginUserProfile> byEmail = users.findByEmail(email.trim());
            if (byEmail.isPresent()) {
                return byEmail;
            }
            String localPart = emailLocalPart(email);
            if (!localPart.isBlank()) {
                Optional<PluginUserProfile> byEmailName = users.findByUsername(localPart);
                if (byEmailName.isPresent()) {
                    return byEmailName;
                }
            }
        }
        if (nickname != null && !nickname.isBlank()) {
            Optional<PluginUserProfile> byNickname = users.findByUsername(nickname.trim());
            if (byNickname.isPresent()) {
                return byNickname;
            }
        }
        return Optional.empty();
    }

    private PluginUserProfile createHostUser(long legacyUid, String email, String nickname, String passwordHash, List<String> warnings, Consumer<String> logger) {
        String safeEmail = uniqueEmail(normalizeEmail(email), legacyUid);
        String safeUsername = uniqueUsername(legacyUid, safeEmail, nickname);
        String safeNickname = hasText(nickname) ? nickname.trim() : safeUsername;
        boolean hasBcryptPassword = isBcryptHash(passwordHash);
        PluginUserProfile created = users.create(new PluginUserCreate(
                safeUsername,
                safeNickname,
                safeEmail,
                null,
                null,
                hasBcryptPassword ? null : fallbackRawPassword(legacyUid),
                hasBcryptPassword ? passwordHash.trim() : null,
                true
        ));
        logger.accept("已为 Blessing Skin 用户创建系统用户 uid=" + legacyUid + " email=" + safeEmail + " userId=" + created.id());
        if (!hasBcryptPassword) {
            warnings.add("Blessing Skin 用户缺少 BCrypt 密码哈希，已写入迁移初始密码 uid=" + legacyUid);
        }
        return created;
    }

    private String uniqueUsername(long legacyUid, String email, String nickname) {
        List<String> candidates = new ArrayList<>();
        String localPart = emailLocalPart(email);
        if (hasText(localPart)) {
            candidates.add(sanitizeUsername(localPart));
        }
        if (hasText(nickname)) {
            candidates.add(sanitizeUsername(nickname));
        }
        candidates.add("bs_" + legacyUid);
        for (String candidate : candidates.stream().filter(this::hasText).distinct().toList()) {
            if (users.findByUsername(candidate).isEmpty()) {
                return candidate;
            }
            String withUid = candidate + "_" + legacyUid;
            if (users.findByUsername(withUid).isEmpty()) {
                return withUid;
            }
        }
        int index = 1;
        while (true) {
            String candidate = "bs_" + legacyUid + "_" + index++;
            if (users.findByUsername(candidate).isEmpty()) {
                return candidate;
            }
        }
    }

    private String uniqueEmail(String email, long legacyUid) {
        String base = hasText(email) && isEmail(email) ? email.trim() : "legacy-" + legacyUid + "@migration.local";
        if (users.findByEmail(base).isEmpty()) {
            return base;
        }
        String local = emailLocalPart(base);
        String domain = base.substring(base.indexOf('@') + 1);
        int index = 1;
        while (true) {
            String candidate = local + "+" + legacyUid + "-" + index++ + "@" + domain;
            if (users.findByEmail(candidate).isEmpty()) {
                return candidate;
            }
        }
    }

    private String normalizeEmail(String email) {
        if (!hasText(email)) {
            return null;
        }
        return email.trim();
    }

    private String sanitizeUsername(String value) {
        if (!hasText(value)) {
            return "";
        }
        String sanitized = value.trim().replaceAll("[^A-Za-z0-9_.-]", "_");
        sanitized = sanitized.replaceAll("_+", "_");
        return sanitized.replaceAll("^_+|_+$", "");
    }

    private boolean isEmail(String value) {
        return hasText(value) && value.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }

    private boolean isBcryptHash(String value) {
        return hasText(value) && value.trim().matches("^\\$2[aby]\\$\\d{2}\\$.{53}$");
    }

    private String fallbackRawPassword(long legacyUid) {
        return "YuDream" + legacyUid;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String emailLocalPart(String email) {
        int index = email.indexOf('@');
        return index > 0 ? email.substring(0, index).trim() : "";
    }

    private void loadDriver() {
        try {
            Class.forName(MYSQL_DRIVER);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("MySQL JDBC 驱动不存在，请将 mysql-connector-j 加入运行时 classpath", e);
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

    private Long nullableLong(ResultSet rs, String name) throws Exception {
        if (!hasColumn(rs, name)) {
            return null;
        }
        long value = rs.getLong(name);
        return rs.wasNull() ? null : value;
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

    private boolean hasTable(Connection connection, String tableName) throws Exception {
        try (ResultSet rs = connection.getMetaData().getTables(connection.getCatalog(), null, tableName, null)) {
            if (rs.next()) {
                return true;
            }
        }
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("show tables like '" + tableName.replace("'", "''") + "'")) {
            return rs.next();
        }
    }

    private String normalizePlayerUuid(String uuid) {
        if (!hasText(uuid)) {
            return null;
        }
        String normalized = uuid.trim().replace("-", "").toLowerCase(Locale.ROOT);
        return normalized.matches("[0-9a-f]{32}") ? normalized : null;
    }

    private String lowerKey(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class MigrationTask implements PluginSseStream {

        private final String id = UUID.randomUUID().toString();
        private final List<MigrationLogEntry> logs = new CopyOnWriteArrayList<>();
        private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();
        private volatile String state = "PENDING";
        private volatile Long startedAt;
        private volatile Long finishedAt;
        private volatile MigrationReport report;
        private volatile Throwable error;

        static MigrationTask empty() {
            MigrationTask task = new MigrationTask();
            task.state = "IDLE";
            return task;
        }

        boolean running() {
            return "PENDING".equals(state) || "RUNNING".equals(state);
        }

        void start() {
            startedAt = System.currentTimeMillis();
            state = "RUNNING";
            info("迁移任务已开始：" + id);
            emitStatus();
        }

        void info(String message) {
            add("INFO", message);
        }

        void finish(MigrationReport report) {
            this.report = report;
            finishedAt = System.currentTimeMillis();
            state = "SUCCESS";
            info("迁移任务已完成");
            emitStatus();
            subscribers.forEach(Subscriber::complete);
        }

        void fail(Throwable throwable) {
            error = throwable;
            finishedAt = System.currentTimeMillis();
            state = "FAILED";
            add("ERROR", throwable.getMessage() == null ? "迁移失败" : throwable.getMessage());
            emitStatus();
        }

        MigrationStatus status() {
            return new MigrationStatus(state, running(), startedAt, finishedAt, report, logs);
        }

        private void add(String level, String message) {
            MigrationLogEntry entry = new MigrationLogEntry(System.currentTimeMillis(), level, message);
            logs.add(entry);
            subscribers.forEach(subscriber -> subscriber.send("migration.log", entry));
        }

        private void emitStatus() {
            MigrationStatus status = status();
            subscribers.forEach(subscriber -> subscriber.send("migration.status", status));
        }

        @Override
        public void subscribe(Subscriber subscriber) {
            subscribers.add(subscriber);
            subscriber.send("migration.status", status());
            logs.forEach(log -> subscriber.send("migration.log", log));
            if (!running() && !"IDLE".equals(state) && error == null) {
                subscriber.complete();
            }
        }

        @Override
        public void unsubscribe(Subscriber subscriber) {
            subscribers.remove(subscriber);
        }
    }
}
