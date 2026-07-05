package online.yudream.base.plugin.blessing.application.service;

import online.yudream.base.plugin.blessing.application.cmd.AssignTextureCmd;
import online.yudream.base.plugin.blessing.application.cmd.ClosetItemSaveCmd;
import online.yudream.base.plugin.blessing.application.cmd.CreatePlayerCmd;
import online.yudream.base.plugin.blessing.application.cmd.CreateSkinUserCmd;
import online.yudream.base.plugin.blessing.application.cmd.MigrationCmd;
import online.yudream.base.plugin.blessing.application.cmd.RenameClosetItemCmd;
import online.yudream.base.plugin.blessing.application.cmd.RenamePlayerCmd;
import online.yudream.base.plugin.blessing.application.cmd.SkinSettingsSaveCmd;
import online.yudream.base.plugin.blessing.application.cmd.TextureUploadCmd;
import online.yudream.base.plugin.blessing.application.dto.BlessingSkinSummaryDTO;
import online.yudream.base.plugin.blessing.domain.aggregate.SkinPlayer;
import online.yudream.base.plugin.blessing.domain.aggregate.SkinClosetItem;
import online.yudream.base.plugin.blessing.domain.aggregate.SkinTexture;
import online.yudream.base.plugin.blessing.domain.aggregate.SkinUser;
import online.yudream.base.plugin.blessing.domain.enumerate.SkinTextureType;
import online.yudream.base.plugin.blessing.domain.valobj.MigrationConfig;
import online.yudream.base.plugin.blessing.domain.valobj.MigrationReport;
import online.yudream.base.plugin.blessing.domain.valobj.SkinSiteSettings;
import online.yudream.base.plugin.blessing.infrastructure.repository.BlessingSkinRepository;
import online.yudream.base.plugin.blessing.infrastructure.service.BlessingSkinMigrationService;
import online.yudream.base.plugin.blessing.infrastructure.service.SkinPasswordService;
import online.yudream.base.plugin.blessing.infrastructure.support.HashSupport;
import online.yudream.base.plugin.spi.system.skin.PluginSkinProfile;
import online.yudream.base.plugin.spi.system.skin.PluginSkinService;
import online.yudream.base.plugin.spi.system.skin.PluginSkinTexture;
import online.yudream.base.plugin.spi.system.skin.PluginSkinUser;
import online.yudream.base.plugin.spi.system.storage.PluginStoredFile;

import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class BlessingSkinAppService implements PluginSkinService {

    private final BlessingSkinRepository repository;
    private final SkinPasswordService passwordService;
    private final BlessingSkinMigrationService migrationService;

    public BlessingSkinAppService(BlessingSkinRepository repository, SkinPasswordService passwordService,
                                  BlessingSkinMigrationService migrationService) {
        this.repository = repository;
        this.passwordService = passwordService;
        this.migrationService = migrationService;
    }

    public BlessingSkinSummaryDTO summary() {
        return new BlessingSkinSummaryDTO(repository.userCount(), repository.playerCount(), repository.textureCount(),
                repository.closetCount(), repository.optionCount(), settings());
    }

    public List<SkinUser> listUsers(int page, int size) {
        return repository.listUsers(page, size);
    }

    public Optional<SkinUser> findUser(String userId) {
        return repository.findUserById(userId);
    }

    public SkinUser createUser(CreateSkinUserCmd cmd) {
        requireText(cmd.email(), "邮箱不能为空");
        requireText(cmd.password(), "密码不能为空");
        repository.findUserByEmail(cmd.email()).ifPresent(ignored -> {
            throw new IllegalArgumentException("邮箱已存在");
        });
        String nickname = hasText(cmd.nickname()) ? cmd.nickname().trim() : cmd.email();
        SkinUser user = new SkinUser(
                UUID.randomUUID().toString(),
                cmd.email().trim(),
                cmd.email().trim().toLowerCase(Locale.ROOT),
                nickname,
                passwordService.hash(cmd.password()),
                null,
                System.currentTimeMillis()
        );
        return repository.saveUser(user);
    }

    public List<SkinPlayer> listPlayers(int page, int size) {
        return repository.listPlayers(page, size);
    }

    public List<SkinPlayer> listPlayersByOwner(String ownerId) {
        return repository.findPlayersByOwner(requireText(ownerId, "用户不能为空"));
    }

    public Optional<SkinPlayer> findPlayer(String name) {
        return repository.findPlayerByName(name);
    }

    public SkinPlayer createPlayer(CreatePlayerCmd cmd, Long currentUserId) {
        String name = requireText(cmd.name(), "角色名不能为空");
        repository.findPlayerByName(name).ifPresent(ignored -> {
            throw new IllegalArgumentException("角色名已存在");
        });
        String ownerId = hasText(cmd.ownerId())
                ? cmd.ownerId().trim()
                : currentUserId == null ? "system" : String.valueOf(currentUserId);
        int maxPlayers = settings().safeMaxPlayersPerUser();
        if (maxPlayers > 0 && repository.findPlayersByOwner(ownerId).size() >= maxPlayers) {
            throw new IllegalArgumentException("当前用户最多只能拥有 " + maxPlayers + " 个角色");
        }
        return repository.savePlayer(new SkinPlayer(
                HashSupport.playerUuid(name),
                ownerId,
                name,
                name.toLowerCase(Locale.ROOT),
                null,
                null,
                null,
                System.currentTimeMillis()
        ));
    }

    public SkinPlayer assignTextures(String playerName, AssignTextureCmd cmd) {
        SkinPlayer player = repository.findPlayerByName(playerName)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在：" + playerName));
        String skinHash = blankToNull(cmd.skinHash());
        String capeHash = blankToNull(cmd.capeHash());
        if (skinHash != null) {
            requireSkinTexture(skinHash);
        }
        if (capeHash != null) {
            requireCapeTexture(capeHash);
        }
        return repository.savePlayer(player.withTextures(skinHash, capeHash));
    }

    public SkinPlayer assignOwnTextures(String playerName, String ownerId, AssignTextureCmd cmd) {
        SkinPlayer player = repository.findPlayerByName(playerName)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在：" + playerName));
        String userId = requireText(ownerId, "用户不能为空");
        if (!player.ownerId().equals(userId)) {
            throw new IllegalArgumentException("只能操作自己的角色");
        }
        String skinHash = blankToNull(cmd.skinHash());
        String capeHash = blankToNull(cmd.capeHash());
        if (skinHash != null) {
            requireOwnTexture(userId, skinHash, "skin");
        }
        if (capeHash != null) {
            requireOwnTexture(userId, capeHash, "cape");
        }
        return repository.savePlayer(player.withTextures(skinHash, capeHash));
    }

    public SkinPlayer renamePlayer(String playerName, RenamePlayerCmd cmd) {
        SkinPlayer player = repository.findPlayerByName(playerName)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在：" + playerName));
        String newName = requireText(cmd.name(), "角色名不能为空");
        repository.findPlayerByName(newName)
                .filter(existing -> !existing.uuid().equals(player.uuid()))
                .ifPresent(ignored -> {
                    throw new IllegalArgumentException("角色名已存在");
                });
        return repository.savePlayer(player.withName(newName));
    }

    public SkinPlayer renameOwnPlayer(String playerName, String ownerId, RenamePlayerCmd cmd) {
        SkinPlayer player = repository.findPlayerByName(playerName)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在：" + playerName));
        if (!player.ownerId().equals(requireText(ownerId, "用户不能为空"))) {
            throw new IllegalArgumentException("只能操作自己的角色");
        }
        return renamePlayer(playerName, cmd);
    }

    public void deletePlayer(String playerName) {
        SkinPlayer player = repository.findPlayerByName(playerName)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在：" + playerName));
        repository.deletePlayer(player.uuid());
    }

    public void deleteOwnPlayer(String playerName, String ownerId) {
        SkinPlayer player = repository.findPlayerByName(playerName)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在：" + playerName));
        if (!player.ownerId().equals(requireText(ownerId, "用户不能为空"))) {
            throw new IllegalArgumentException("只能操作自己的角色");
        }
        repository.deletePlayer(player.uuid());
    }

    public List<SkinTexture> listTextures(int page, int size) {
        return repository.listTextures(page, size);
    }

    public List<SkinTexture> listVisibleTextures(String userId, boolean manage, int page, int size) {
        if (manage) {
            return listTextures(page, size);
        }
        return repository.listTextures(page, size).stream()
                .filter(texture -> Boolean.TRUE.equals(texture.publicAccess()) || requireText(userId, "用户不能为空").equals(texture.uploaderId()))
                .toList();
    }

    public SkinTexture uploadTexture(TextureUploadCmd cmd, Long currentUserId) {
        String uploaderId = currentUserId == null ? "system" : String.valueOf(currentUserId);
        return saveUploadedTexture(cmd, uploaderId);
    }

    public SkinTexture uploadOwnTexture(TextureUploadCmd cmd, String ownerId, Long currentUserId) {
        String userId = requireText(ownerId, "用户不能为空");
        SkinTexture texture = saveUploadedTexture(cmd, userId);
        repository.saveClosetItem(userId, texture.hash(), texture.name());
        return texture;
    }

    private SkinTexture saveUploadedTexture(TextureUploadCmd cmd, String uploaderId) {
        byte[] bytes = Base64.getDecoder().decode(requireText(cmd.base64(), "材质 base64 不能为空"));
        String hash = HashSupport.sha256(bytes);
        SkinTextureType type = SkinTextureType.from(hasText(cmd.type()) ? cmd.type() : cmd.model());
        String contentType = hasText(cmd.contentType()) ? cmd.contentType() : "image/png";
        String objectKey = repository.saveTextureFile(hash, bytes, contentType);
        boolean publicAccess = settings().publicUploadEnabled() && (cmd.publicAccess() == null || cmd.publicAccess());
        return repository.saveTexture(new SkinTexture(
                hash,
                hasText(cmd.name()) ? cmd.name().trim() : hash,
                type.yggdrasilType(),
                type.model(),
                contentType,
                (long) bytes.length,
                requireText(uploaderId, "用户不能为空"),
                publicAccess,
                objectKey,
                null,
                System.currentTimeMillis()
        ));
    }

    public List<SkinClosetItem> listCloset(String userId, int page, int size) {
        return hasText(userId) ? repository.findClosetByUser(userId.trim(), page, size) : repository.listClosetItems(page, size);
    }

    public SkinClosetItem saveClosetItem(ClosetItemSaveCmd cmd, Long currentUserId) {
        String userId = hasText(cmd.userId()) ? cmd.userId().trim() : currentUserId == null ? "system" : String.valueOf(currentUserId);
        String textureHash = requireText(cmd.textureHash(), "材质不能为空");
        SkinTexture texture = requireTexture(textureHash);
        return repository.saveClosetItem(userId, texture.hash(), hasText(cmd.itemName()) ? cmd.itemName().trim() : texture.name());
    }

    public SkinClosetItem saveOwnClosetItem(ClosetItemSaveCmd cmd, String ownerId, Long currentUserId) {
        String userId = requireText(ownerId, "用户不能为空");
        String textureHash = requireText(cmd.textureHash(), "材质不能为空");
        SkinTexture texture = requireTexture(textureHash);
        if (!Boolean.TRUE.equals(texture.publicAccess()) && !userId.equals(texture.uploaderId())) {
            throw new IllegalArgumentException("只能收藏公开材质或自己上传的私有材质");
        }
        return saveClosetItem(new ClosetItemSaveCmd(userId, texture.hash(), cmd.itemName()), currentUserId);
    }

    public SkinClosetItem renameClosetItem(String id, RenameClosetItemCmd cmd) {
        SkinClosetItem item = repository.findClosetItem(requireText(id, "衣柜项不能为空"))
                .orElseThrow(() -> new IllegalArgumentException("衣柜项不存在"));
        return repository.saveClosetItem(item.withItemName(requireText(cmd.itemName(), "显示名称不能为空")));
    }

    public SkinClosetItem renameOwnClosetItem(String id, String userId, RenameClosetItemCmd cmd) {
        SkinClosetItem item = repository.findClosetItem(requireText(id, "衣柜项不能为空"))
                .orElseThrow(() -> new IllegalArgumentException("衣柜项不存在"));
        if (!item.userId().equals(requireText(userId, "用户不能为空"))) {
            throw new IllegalArgumentException("只能操作自己的衣柜");
        }
        return repository.saveClosetItem(item.withItemName(requireText(cmd.itemName(), "显示名称不能为空")));
    }

    public void deleteClosetItem(String id) {
        repository.deleteClosetItem(requireText(id, "衣柜项不能为空"));
    }

    public void deleteOwnClosetItem(String id, String userId) {
        SkinClosetItem item = repository.findClosetItem(requireText(id, "衣柜项不能为空"))
                .orElseThrow(() -> new IllegalArgumentException("衣柜项不存在"));
        if (!item.userId().equals(requireText(userId, "用户不能为空"))) {
            throw new IllegalArgumentException("只能操作自己的衣柜");
        }
        repository.deleteClosetItem(item.id());
    }

    public SkinSiteSettings settings() {
        return repository.settings();
    }

    public SkinSiteSettings saveSettings(SkinSettingsSaveCmd cmd) {
        int maxPlayers = cmd.maxPlayersPerUser() == null ? SkinSiteSettings.defaults().maxPlayersPerUser() : cmd.maxPlayersPerUser();
        if (maxPlayers < 0) {
            throw new IllegalArgumentException("最多角色数不能小于 0");
        }
        return repository.saveSettings(new SkinSiteSettings(maxPlayers, cmd.allowPublicUpload(), cmd.siteNotice()));
    }

    public MigrationReport migrate(MigrationCmd cmd) {
        return migrationService.migrate(new MigrationConfig(
                cmd.driverClass(),
                cmd.jdbcUrl(),
                cmd.username(),
                cmd.password(),
                cmd.textureBaseDir()
        ));
    }

    @Override
    public Optional<PluginSkinUser> authenticate(String usernameOrEmail, String password) {
        Optional<SkinUser> user = usernameOrEmail != null && usernameOrEmail.contains("@")
                ? repository.findUserByEmail(usernameOrEmail)
                : repository.findUserByNickname(usernameOrEmail);
        if (user.isEmpty() || !passwordService.matches(password, user.get().passwordHash())) {
            return Optional.empty();
        }
        SkinUser skinUser = user.get();
        return Optional.of(new PluginSkinUser(
                skinUser.id(),
                skinUser.email(),
                skinUser.nickname(),
                repository.findPlayersByOwner(skinUser.id()).stream().map(this::toProfile).toList()
        ));
    }

    @Override
    public Optional<PluginSkinProfile> findProfileByName(String name) {
        return repository.findPlayerByName(name).map(this::toProfile);
    }

    @Override
    public Optional<PluginSkinProfile> findProfileByUuid(String uuid) {
        return repository.findPlayerByUuid(uuid).map(this::toProfile);
    }

    @Override
    public List<PluginSkinProfile> findProfilesByNames(List<String> names) {
        if (names == null) {
            return List.of();
        }
        return names.stream()
                .map(this::findProfileByName)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public Optional<PluginSkinTexture> findTextureByHash(String hash) {
        return repository.findTextureByHash(hash).map(this::toTexture);
    }

    @Override
    public Optional<PluginStoredFile> readTexture(String hash) {
        return repository.findTextureByHash(hash).flatMap(repository::readTextureFile);
    }

    @Override
    public void setProfileTexture(String uuid, String textureType, String textureHash) {
        SkinPlayer player = repository.findPlayerByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在：" + uuid));
        SkinTexture texture = requireTexture(textureHash);
        if ("cape".equalsIgnoreCase(textureType)) {
            repository.savePlayer(player.withTextures(player.skinHash(), texture.hash()));
            return;
        }
        repository.savePlayer(player.withTextures(texture.hash(), player.capeHash()));
    }

    @Override
    public void clearProfileTexture(String uuid, String textureType) {
        SkinPlayer player = repository.findPlayerByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在：" + uuid));
        if ("cape".equalsIgnoreCase(textureType)) {
            repository.savePlayer(player.withTextures(player.skinHash(), null));
            return;
        }
        repository.savePlayer(player.withTextures(null, player.capeHash()));
    }

    public PluginSkinProfile toProfile(SkinPlayer player) {
        PluginSkinTexture skin = player.skinHash() == null ? null : repository.findTextureByHash(player.skinHash()).map(this::toTexture).orElse(null);
        PluginSkinTexture cape = player.capeHash() == null ? null : repository.findTextureByHash(player.capeHash()).map(this::toTexture).orElse(null);
        return new PluginSkinProfile(player.uuid(), player.name(), player.ownerId(), skin, cape, player.lastModified());
    }

    private PluginSkinTexture toTexture(SkinTexture texture) {
        return new PluginSkinTexture(texture.hash(), texture.name(), texture.type(), texture.model(), texture.contentType(),
                texture.size(), texture.objectKey(), texture.publicAccess());
    }

    private SkinTexture requireTexture(String hash) {
        return repository.findTextureByHash(hash)
                .orElseThrow(() -> new IllegalArgumentException("材质不存在：" + hash));
    }

    private SkinTexture requireSkinTexture(String hash) {
        SkinTexture texture = requireTexture(hash);
        if (!"skin".equalsIgnoreCase(texture.type())) {
            throw new IllegalArgumentException("请选择皮肤材质");
        }
        return texture;
    }

    private SkinTexture requireCapeTexture(String hash) {
        SkinTexture texture = requireTexture(hash);
        if (!"cape".equalsIgnoreCase(texture.type())) {
            throw new IllegalArgumentException("请选择披风材质");
        }
        return texture;
    }

    private SkinTexture requireOwnTexture(String userId, String hash, String expectedType) {
        SkinTexture texture = "cape".equals(expectedType) ? requireCapeTexture(hash) : requireSkinTexture(hash);
        repository.findClosetByUserAndTexture(userId, hash)
                .orElseThrow(() -> new IllegalArgumentException("材质不在你的衣柜中"));
        return texture;
    }

    private String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
