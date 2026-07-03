package online.yudream.base.infra.system.file.mapper;

import online.yudream.base.domain.system.file.aggregate.FileObject;
import online.yudream.base.infra.system.file.dataobj.FileObjectDO;

public class FileObjectInfraMapper {

    public static FileObjectDO toDataObj(FileObject fileObject) {
        if (fileObject == null) {
            return null;
        }
        FileObjectDO fileObjectDO = new FileObjectDO();
        fileObjectDO.setId(fileObject.getId());
        fileObjectDO.setBucket(fileObject.getBucket());
        fileObjectDO.setObjectKey(fileObject.getObjectKey());
        fileObjectDO.setOriginalName(fileObject.getOriginalName());
        fileObjectDO.setContentType(fileObject.getContentType());
        fileObjectDO.setSize(fileObject.getSize());
        fileObjectDO.setModule(fileObject.getModule());
        fileObjectDO.setUploaderId(fileObject.getUploaderId());
        fileObjectDO.setPublicAccess(fileObject.getPublicAccess());
        fileObjectDO.setDeleted(fileObject.getDeleted());
        fileObjectDO.setVersion(fileObject.getVersion());
        fileObjectDO.setCreateTime(fileObject.getCreateTime());
        fileObjectDO.setUpdateTime(fileObject.getUpdateTime());
        return fileObjectDO;
    }

    public static FileObject toDomain(FileObjectDO fileObjectDO) {
        if (fileObjectDO == null) {
            return null;
        }
        return FileObject.builder()
                .id(fileObjectDO.getId())
                .bucket(fileObjectDO.getBucket())
                .objectKey(fileObjectDO.getObjectKey())
                .originalName(fileObjectDO.getOriginalName())
                .contentType(fileObjectDO.getContentType())
                .size(fileObjectDO.getSize())
                .module(fileObjectDO.getModule())
                .uploaderId(fileObjectDO.getUploaderId())
                .publicAccess(fileObjectDO.getPublicAccess())
                .deleted(fileObjectDO.getDeleted())
                .version(fileObjectDO.getVersion())
                .createTime(fileObjectDO.getCreateTime())
                .updateTime(fileObjectDO.getUpdateTime())
                .build();
    }
}
