package online.yudream.base.application.system.file.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.file.query.FileObjectPageQuery;
import online.yudream.base.application.system.file.dto.FileContentDTO;
import online.yudream.base.application.system.file.dto.FileObjectDTO;
import online.yudream.base.application.system.file.support.ImageThumbnailSupport;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.file.aggregate.FileObject;
import online.yudream.base.domain.system.file.repo.FileObjectRepo;
import online.yudream.base.domain.system.file.service.ObjectStorage;
import online.yudream.base.domain.system.file.valobj.StoredObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileAppService {

    private final FileObjectRepo fileObjectRepo;
    private final ObjectStorage objectStorage;

    @Transactional
    public FileObjectDTO upload(InputStream inputStream, String originalName, String contentType,
                                long size, String module, Long uploaderId, boolean publicAccess) {
        if (inputStream == null || size <= 0) {
            throw new BizException("上传文件不能为空");
        }
        String safeModule = StringUtils.hasText(module) ? module.trim() : "common";
        String objectKey = buildObjectKey(safeModule, originalName);
        objectStorage.put(objectKey, inputStream, size, contentType);
        storeThumbnail(objectKey, originalName, contentType);
        FileObject saved = fileObjectRepo.save(FileObject.builder()
                .bucket(objectStorage.bucket())
                .objectKey(objectKey)
                .originalName(originalName)
                .contentType(contentType)
                .size(size)
                .module(safeModule)
                .uploaderId(uploaderId)
                .publicAccess(publicAccess)
                .deleted(false)
                .build());
        return toDTO(saved);
    }

    @Transactional(readOnly = true)
    public FileObjectDTO get(Long id) {
        return toDTO(getActiveFile(id));
    }

    @Transactional(readOnly = true)
    public FileObjectDTO tryGet(Long id) {
        if (id == null) {
            return null;
        }
        return fileObjectRepo.findById(id)
                .filter(fileObject -> !fileObject.isDeleted())
                .map(this::toDTO)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public PageResult<FileObjectDTO> page(FileObjectPageQuery query) {
        int page = query == null ? 1 : query.getPage();
        int size = query == null ? 20 : query.getSize();
        String keyword = query == null ? null : query.getKeyword();
        String module = query == null ? null : query.getModule();
        Boolean publicAccess = query == null ? null : query.getPublicAccess();
        return new PageResult<>(
                fileObjectRepo.page(keyword, module, publicAccess, page, size).stream().map(this::toDTO).toList(),
                fileObjectRepo.count(keyword, module, publicAccess),
                Math.max(page, 1),
                Math.max(size, 1)
        );
    }

    @Transactional(readOnly = true)
    public FileContentDTO content(Long id) {
        FileObject fileObject = getActiveFile(id);
        return readContent(fileObject);
    }

    @Transactional(readOnly = true)
    public FileContentDTO publicContent(Long id) {
        FileObject fileObject = requirePublicFile(id);
        return readContent(fileObject);
    }

    @Transactional(readOnly = true)
    public FileContentDTO thumbnailContent(Long id) {
        return thumbnailContent(getActiveFile(id));
    }

    @Transactional(readOnly = true)
    public FileContentDTO publicThumbnailContent(Long id) {
        return thumbnailContent(requirePublicFile(id));
    }

    private FileContentDTO readContent(FileObject fileObject) {
        StoredObject storedObject = objectStorage.get(fileObject.getObjectKey());
        return FileContentDTO.builder()
                .originalName(fileObject.getOriginalName())
                .contentType(storedObject.contentType() == null ? fileObject.getContentType() : storedObject.contentType())
                .contentLength(storedObject.contentLength() == null ? fileObject.getSize() : storedObject.contentLength())
                .inputStream(storedObject.inputStream())
                .build();
    }

    private FileContentDTO thumbnailContent(FileObject fileObject) {
        if (!ImageThumbnailSupport.rasterizable(fileObject.getOriginalName(), fileObject.getContentType())) {
            return readContent(fileObject);
        }
        String thumbKey = ImageThumbnailSupport.thumbnailObjectKey(fileObject.getObjectKey());
        try {
            StoredObject stored = objectStorage.get(thumbKey);
            return FileContentDTO.builder()
                    .originalName(ImageThumbnailSupport.THUMB_FILENAME)
                    .contentType(ImageThumbnailSupport.THUMB_CONTENT_TYPE)
                    .contentLength(stored.contentLength())
                    .inputStream(stored.inputStream())
                    .build();
        }
        catch (BizException ignored) {
            FileContentDTO generated = generateAndStoreThumbnail(fileObject, thumbKey);
            return generated == null ? readContent(fileObject) : generated;
        }
    }

    private void storeThumbnail(String objectKey, String originalName, String contentType) {
        if (!ImageThumbnailSupport.rasterizable(originalName, contentType)) {
            return;
        }
        try {
            StoredObject stored = objectStorage.get(objectKey);
            try (InputStream in = stored.inputStream()) {
                byte[] jpeg = ImageThumbnailSupport.thumbnailJpeg(in);
                if (jpeg == null || jpeg.length == 0) {
                    return;
                }
                objectStorage.put(ImageThumbnailSupport.thumbnailObjectKey(objectKey),
                        new ByteArrayInputStream(jpeg), jpeg.length, ImageThumbnailSupport.THUMB_CONTENT_TYPE);
            }
        }
        catch (Exception ignored) {
            // 首次列表请求再补生成
        }
    }

    private FileContentDTO generateAndStoreThumbnail(FileObject fileObject, String thumbKey) {
        StoredObject original = objectStorage.get(fileObject.getObjectKey());
        try (InputStream in = original.inputStream()) {
            byte[] jpeg = ImageThumbnailSupport.thumbnailJpeg(in);
            if (jpeg == null || jpeg.length == 0) {
                return null;
            }
            objectStorage.put(thumbKey, new ByteArrayInputStream(jpeg), jpeg.length, ImageThumbnailSupport.THUMB_CONTENT_TYPE);
            return FileContentDTO.builder()
                    .originalName(ImageThumbnailSupport.THUMB_FILENAME)
                    .contentType(ImageThumbnailSupport.THUMB_CONTENT_TYPE)
                    .contentLength((long) jpeg.length)
                    .inputStream(new ByteArrayInputStream(jpeg))
                    .build();
        }
        catch (Exception ignored) {
            return null;
        }
    }

    private void deleteThumbnail(String objectKey) {
        String thumbKey = ImageThumbnailSupport.thumbnailObjectKey(objectKey);
        if (thumbKey == null) {
            return;
        }
        try {
            objectStorage.delete(thumbKey);
        }
        catch (RuntimeException ignored) {
        }
    }

    private FileObject requirePublicFile(Long id) {
        FileObject fileObject = getActiveFile(id);
        if (!Boolean.TRUE.equals(fileObject.getPublicAccess())) {
            throw new BizException("文件未开放公开访问");
        }
        return fileObject;
    }

    @Transactional
    public void delete(Long id) {
        FileObject fileObject = getActiveFile(id);
        objectStorage.delete(fileObject.getObjectKey());
        deleteThumbnail(fileObject.getObjectKey());
        fileObject.markDeleted();
        fileObjectRepo.save(fileObject);
    }

    public String fileUrl(Long id) {
        return id == null ? null : "/api/files/" + id + "/content";
    }

    private FileObject getActiveFile(Long id) {
        FileObject fileObject = fileObjectRepo.findById(id)
                .orElseThrow(() -> new BizException("文件不存在"));
        if (fileObject.isDeleted()) {
            throw new BizException("文件不存在");
        }
        return fileObject;
    }

    private FileObjectDTO toDTO(FileObject fileObject) {
        return FileObjectDTO.builder()
                .id(fileObject.getId())
                .originalName(fileObject.getOriginalName())
                .contentType(fileObject.getContentType())
                .size(fileObject.getSize())
                .module(fileObject.getModule())
                .url(fileUrl(fileObject.getId()))
                .createTime(fileObject.getCreateTime())
                .build();
    }

    private String buildObjectKey(String module, String originalName) {
        String ext = "";
        if (StringUtils.hasText(originalName)) {
            int index = originalName.lastIndexOf('.');
            if (index >= 0 && index < originalName.length() - 1) {
                ext = originalName.substring(index).toLowerCase();
            }
        }
        LocalDate today = LocalDate.now();
        return module + "/" + today.getYear() + "/" + today.getMonthValue() + "/" + today.getDayOfMonth()
                + "/" + UUID.randomUUID() + ext;
    }
}
