package online.yudream.base.plugin.skin.interfaces.assembler;

import online.yudream.base.plugin.skin.application.cmd.AssignTextureCmd;
import online.yudream.base.plugin.skin.application.cmd.ClosetItemSaveCmd;
import online.yudream.base.plugin.skin.application.cmd.CreatePlayerCmd;
import online.yudream.base.plugin.skin.application.cmd.CreateSkinUserCmd;
import online.yudream.base.plugin.skin.application.cmd.DefaultPlayerSaveCmd;
import online.yudream.base.plugin.skin.application.cmd.MigrationCmd;
import online.yudream.base.plugin.skin.application.cmd.RenameClosetItemCmd;
import online.yudream.base.plugin.skin.application.cmd.RenamePlayerCmd;
import online.yudream.base.plugin.skin.application.cmd.SkinSettingsSaveCmd;
import online.yudream.base.plugin.skin.application.cmd.TextureUploadCmd;
import online.yudream.base.plugin.skin.domain.aggregate.SkinPlayer;
import online.yudream.base.plugin.skin.interfaces.request.AssignTextureRequest;
import online.yudream.base.plugin.skin.interfaces.request.ClosetItemSaveRequest;
import online.yudream.base.plugin.skin.interfaces.request.CreatePlayerRequest;
import online.yudream.base.plugin.skin.interfaces.request.CreateSkinUserRequest;
import online.yudream.base.plugin.skin.interfaces.request.DefaultPlayerSaveRequest;
import online.yudream.base.plugin.skin.interfaces.request.MigrationRequest;
import online.yudream.base.plugin.skin.interfaces.request.RenameClosetItemRequest;
import online.yudream.base.plugin.skin.interfaces.request.RenamePlayerRequest;
import online.yudream.base.plugin.skin.interfaces.request.SkinSettingsSaveRequest;
import online.yudream.base.plugin.skin.interfaces.request.TextureUploadRequest;
import online.yudream.base.plugin.skin.interfaces.res.SkinPlayerRes;
import online.yudream.base.plugin.spi.system.user.PluginUserProfile;

import java.util.function.Function;

public class YuDreamSkinWebAssembler {

    public CreateSkinUserCmd toCmd(CreateSkinUserRequest request) {
        return new CreateSkinUserCmd(request.email(), request.nickname(), request.password());
    }

    public CreatePlayerCmd toCmd(CreatePlayerRequest request) {
        return new CreatePlayerCmd(request.name(), request.ownerId());
    }

    public CreatePlayerCmd toCmd(CreatePlayerRequest request, String ownerId) {
        return new CreatePlayerCmd(request.name(), ownerId);
    }

    public AssignTextureCmd toCmd(AssignTextureRequest request) {
        return new AssignTextureCmd(request.skinHash(), request.capeHash());
    }

    public RenamePlayerCmd toCmd(RenamePlayerRequest request) {
        return new RenamePlayerCmd(request.name());
    }

    public DefaultPlayerSaveCmd toCmd(DefaultPlayerSaveRequest request) {
        return new DefaultPlayerSaveCmd(request.name());
    }

    public TextureUploadCmd toCmd(TextureUploadRequest request) {
        return new TextureUploadCmd(
                request.name(),
                request.type(),
                request.model(),
                request.contentType(),
                request.base64(),
                request.publicAccess()
        );
    }

    public ClosetItemSaveCmd toCmd(ClosetItemSaveRequest request) {
        return new ClosetItemSaveCmd(request.userId(), request.textureHash(), request.itemName());
    }

    public ClosetItemSaveCmd toCmd(ClosetItemSaveRequest request, String userId) {
        return new ClosetItemSaveCmd(userId, request.textureHash(), request.itemName());
    }

    public RenameClosetItemCmd toCmd(RenameClosetItemRequest request) {
        return new RenameClosetItemCmd(request.itemName());
    }

    public SkinSettingsSaveCmd toCmd(SkinSettingsSaveRequest request) {
        return new SkinSettingsSaveCmd(request.maxPlayersPerUser(), request.allowPublicUpload(), request.siteNotice());
    }

    public MigrationCmd toCmd(MigrationRequest request) {
        return new MigrationCmd(
                request.host(),
                request.port(),
                request.database(),
                request.username(),
                request.password(),
                request.textureBaseDir(),
                request.textureArchiveBase64(),
                request.textureArchiveName()
        );
    }

    public SkinPlayerRes toRes(SkinPlayer player, Function<String, PluginUserProfile> userLoader) {
        PluginUserProfile owner = userLoader == null ? null : userLoader.apply(player.ownerId());
        return new SkinPlayerRes(
                player.uuid(),
                player.ownerId(),
                owner == null ? null : owner.nickname(),
                owner == null ? null : owner.username(),
                owner == null ? null : owner.email(),
                player.name(),
                player.skinHash(),
                player.capeHash(),
                player.lastModified()
        );
    }
}
