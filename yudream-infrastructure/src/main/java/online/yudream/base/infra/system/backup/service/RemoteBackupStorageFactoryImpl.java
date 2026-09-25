package online.yudream.base.infra.system.backup.service;

import online.yudream.base.domain.system.backup.aggregate.RemoteTarget;
import online.yudream.base.domain.system.backup.enumerate.RemoteTargetType;
import online.yudream.base.domain.system.backup.service.RemoteBackupStorage;
import org.springframework.stereotype.Service;

/** 按目标协议构建对应存储网关。 */
@Service
public class RemoteBackupStorageFactoryImpl implements RemoteBackupStorage.Factory {

    @Override
    public RemoteBackupStorage create(RemoteTarget target) {
        if (target.getType() == RemoteTargetType.WEBDAV) {
            return new WebDavRemoteBackupStorage(target);
        }
        return new FtpRemoteBackupStorage(target);
    }
}
