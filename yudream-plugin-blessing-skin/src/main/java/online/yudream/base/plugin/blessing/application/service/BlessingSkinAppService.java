package online.yudream.base.plugin.blessing.application.service;

import online.yudream.base.plugin.blessing.domain.aggregate.SkinPlayer;
import online.yudream.base.plugin.blessing.domain.aggregate.SkinTexture;
import online.yudream.base.plugin.blessing.domain.aggregate.SkinUser;
import online.yudream.base.plugin.blessing.domain.enumerate.SkinTextureType;
import online.yudream.base.plugin.blessing.domain.valobj.MigrationReport;
import online.yudream.base.plugin.blessing.infrastructure.repository.BlessingSkinRepository;
import online.yudream.base.plugin.blessing.infrastructure.service.BlessingSkinMigrationService;
import online.yudream.base.plugin.blessing.infrastructure.service.SkinPasswordService;
import online.yudream.base.plugin.blessing.infrastructure.support.HashSupport;
import online.yudream.base.plugin.blessing.interfaces.request.AssignTextureRequest;
import online.yudream.base.plugin.blessing.interfaces.request.CreatePlayerRequest;
import online.yudream.base.plugin.blessing.interfaces.request.CreateSkinUserRequest;
import online.yudream.base.plugin.blessing.interfaces.request.MigrationRequest;
import online.yudream.base.plugin.blessing.interfaces.request.TextureUploadRequest;
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

    public Object summary() {
        return new Summary(repository.userCount(), repository.playerCount(), repository.textureCount(),
                repository.closetCount(), repository.optionCount());
    }

    public List<SkinUser> listUsers(int page, int size) {
        return repository.listUsers(page, size);
    }

    public SkinUser createUser(CreateSkinUserRequest request) {
        requireText(request.email(), "邮箱不能为空");
        requireText(request.password(), "密码不能为空");
        repository.findUserByEmail(request.email()).ifPresent(ignored -> {
            throw new IllegalArgumentException("邮箱已存在");
        });
        String nickname = hasText(request.nickname()) ? request.nickname().trim() : request.email();
        SkinUser user = new SkinUser(
                UUID.randomUUID().toString(),
                request.email().trim(),
                request.email().trim().toLowerCase(Locale.ROOT),
                nickname,
                passwordService.hash(request.password()),
                null,
                System.currentTimeMillis()
        );
        return repository.saveUser(user);
    }

    public List<SkinPlayer> listPlayers(int page, int size) {
        return repository.listPlayers(page, size);
    }

    public Optional<SkinPlayer> findPlayer(String name) {
        return repository.findPlayerByName(name);
    }

    public SkinPlayer createPlayer(CreatePlayerRequest request, Long currentUserId) {
        String name = requireText(request.name(), "角色名不能为空");
        repository.findPlayerByName(name).ifPresent(ignored -> {
            throw new IllegalArgumentException("角色名已存在");
        });
        String ownerId = hasText(request.ownerId())
                ? request.ownerId().trim()
                : currentUserId == null ? "system" : String.valueOf(currentUserId);
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

    public SkinPlayer assignTextures(String playerName, AssignTextureRequest request) {
        SkinPlayer player = repository.findPlayerByName(playerName)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在：" + playerName));
        String skinHash = blankToNull(request.skinHash());
        String capeHash = blankToNull(request.capeHash());
        if (skinHash != null) {
            requireTexture(skinHash);
        }
        if (capeHash != null) {
            requireTexture(capeHash);
        }
        return repository.savePlayer(player.withTextures(skinHash, capeHash));
    }

    public List<SkinTexture> listTextures(int page, int size) {
        return repository.listTextures(page, size);
    }

    public SkinTexture uploadTexture(TextureUploadRequest request, Long currentUserId) {
        byte[] bytes = Base64.getDecoder().decode(requireText(request.base64(), "材质 base64 不能为空"));
        String hash = HashSupport.sha256(bytes);
        SkinTextureType type = SkinTextureType.from(hasText(request.model()) ? request.model() : request.type());
        String contentType = hasText(request.contentType()) ? request.contentType() : "image/png";
        String objectKey = repository.saveTextureFile(hash, bytes, contentType);
        return repository.saveTexture(new SkinTexture(
                hash,
                hasText(request.name()) ? request.name().trim() : hash,
                type.yggdrasilType(),
                type.model(),
                contentType,
                (long) bytes.length,
                hasText(request.uploaderId()) ? request.uploaderId() : currentUserId == null ? "system" : String.valueOf(currentUserId),
                request.publicAccess() == null || request.publicAccess(),
                objectKey,
                null,
                System.currentTimeMillis()
        ));
    }

    public MigrationReport migrate(MigrationRequest request) {
        return migrationService.migrate(request);
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

    public record Summary(long users, long players, long textures, long closetItems, long options) {
    }
}
