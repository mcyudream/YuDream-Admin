package online.yudream.base.plugin.blessing.interfaces.assembler;

import online.yudream.base.plugin.blessing.application.cmd.AssignTextureCmd;
import online.yudream.base.plugin.blessing.application.cmd.ClosetItemSaveCmd;
import online.yudream.base.plugin.blessing.application.cmd.CreatePlayerCmd;
import online.yudream.base.plugin.blessing.application.cmd.CreateSkinUserCmd;
import online.yudream.base.plugin.blessing.application.cmd.MigrationCmd;
import online.yudream.base.plugin.blessing.application.cmd.SkinSettingsSaveCmd;
import online.yudream.base.plugin.blessing.application.cmd.TextureUploadCmd;
import online.yudream.base.plugin.blessing.interfaces.request.AssignTextureRequest;
import online.yudream.base.plugin.blessing.interfaces.request.ClosetItemSaveRequest;
import online.yudream.base.plugin.blessing.interfaces.request.CreatePlayerRequest;
import online.yudream.base.plugin.blessing.interfaces.request.CreateSkinUserRequest;
import online.yudream.base.plugin.blessing.interfaces.request.MigrationRequest;
import online.yudream.base.plugin.blessing.interfaces.request.SkinSettingsSaveRequest;
import online.yudream.base.plugin.blessing.interfaces.request.TextureUploadRequest;

public class BlessingSkinWebAssembler {

    public CreateSkinUserCmd toCmd(CreateSkinUserRequest request) {
        return new CreateSkinUserCmd(request.email(), request.nickname(), request.password());
    }

    public CreatePlayerCmd toCmd(CreatePlayerRequest request) {
        return new CreatePlayerCmd(request.name(), request.ownerId());
    }

    public AssignTextureCmd toCmd(AssignTextureRequest request) {
        return new AssignTextureCmd(request.skinHash(), request.capeHash());
    }

    public TextureUploadCmd toCmd(TextureUploadRequest request) {
        return new TextureUploadCmd(
                request.name(),
                request.type(),
                request.model(),
                request.contentType(),
                request.base64(),
                request.publicAccess(),
                request.uploaderId()
        );
    }

    public ClosetItemSaveCmd toCmd(ClosetItemSaveRequest request) {
        return new ClosetItemSaveCmd(request.userId(), request.textureHash(), request.itemName());
    }

    public SkinSettingsSaveCmd toCmd(SkinSettingsSaveRequest request) {
        return new SkinSettingsSaveCmd(request.maxPlayersPerUser(), request.allowPublicUpload(), request.siteNotice());
    }

    public MigrationCmd toCmd(MigrationRequest request) {
        return new MigrationCmd(
                request.driverClass(),
                request.jdbcUrl(),
                request.username(),
                request.password(),
                request.textureBaseDir()
        );
    }
}
