package online.yudream.base.application.system.file.service;

import online.yudream.base.application.system.file.dto.FileContentDTO;
import online.yudream.base.application.system.file.dto.FileObjectDTO;
import online.yudream.base.application.system.file.support.ImageThumbnailSupport;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.file.aggregate.FileObject;
import online.yudream.base.domain.system.file.repo.FileObjectRepo;
import online.yudream.base.domain.system.file.service.ObjectStorage;
import online.yudream.base.domain.system.file.valobj.StoredObject;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileAppServiceTest {

    @Test
    void uploadStoresJpegThumbnailBesideOriginal() throws Exception {
        InMemoryFiles files = new InMemoryFiles();
        InMemoryStorage storage = new InMemoryStorage();
        FileAppService service = new FileAppService(files, storage);
        byte[] png = samplePng(800, 600, Color.BLUE);

        FileObjectDTO saved = service.upload(new ByteArrayInputStream(png), "cover.png", "image/png", png.length, "timeline", 1L, true);

        assertNotNull(saved.getId());
        assertEquals(2, storage.objects.size());
        String originalKey = files.rows.getFirst().getObjectKey();
        StoredObject thumb = storage.get(originalKey + ".thumb.jpg");
        assertEquals(ImageThumbnailSupport.THUMB_CONTENT_TYPE, thumb.contentType());
        assertTrue(thumb.contentLength() > 0 && thumb.contentLength() < png.length);
    }

    @Test
    void thumbnailContentGeneratesMissingVariant() throws Exception {
        InMemoryFiles files = new InMemoryFiles();
        InMemoryStorage storage = new InMemoryStorage();
        FileAppService service = new FileAppService(files, storage);
        byte[] png = samplePng(640, 480, Color.GREEN);
        storage.put("legacy/a.png", new ByteArrayInputStream(png), png.length, "image/png");
        FileObject saved = files.save(FileObject.builder()
                .objectKey("legacy/a.png")
                .originalName("a.png")
                .contentType("image/png")
                .size((long) png.length)
                .module("timeline")
                .publicAccess(true)
                .deleted(false)
                .build());

        FileContentDTO thumb = service.publicThumbnailContent(saved.getId());
        assertEquals(ImageThumbnailSupport.THUMB_CONTENT_TYPE, thumb.getContentType());
        assertTrue(storage.objects.containsKey("legacy/a.png.thumb.jpg"));
    }

    @Test
    void publicThumbnailRejectsPrivateFile() {
        InMemoryFiles files = new InMemoryFiles();
        FileAppService service = new FileAppService(files, new InMemoryStorage());
        FileObject saved = files.save(FileObject.builder()
                .objectKey("private/a.png")
                .originalName("a.png")
                .contentType("image/png")
                .size(12L)
                .publicAccess(false)
                .deleted(false)
                .build());
        assertThrows(BizException.class, () -> service.publicThumbnailContent(saved.getId()));
    }

    private static byte[] samplePng(int width, int height, Color color) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(image, "png", buffer);
        return buffer.toByteArray();
    }

    private static final class InMemoryFiles implements FileObjectRepo {
        private final AtomicLong ids = new AtomicLong(1);
        private final List<FileObject> rows = new ArrayList<>();

        @Override
        public FileObject save(FileObject fileObject) {
            if (fileObject.getId() == null) {
                fileObject.setId(ids.getAndIncrement());
            }
            rows.removeIf(item -> item.getId().equals(fileObject.getId()));
            rows.add(fileObject);
            return fileObject;
        }

        @Override
        public Optional<FileObject> findById(Long id) {
            return rows.stream().filter(item -> item.getId().equals(id)).findFirst();
        }

        @Override
        public List<FileObject> page(String keyword, String module, Boolean publicAccess, int page, int size) {
            return List.of();
        }

        @Override
        public long count(String keyword, String module, Boolean publicAccess) {
            return 0;
        }
    }

    private static final class InMemoryStorage implements ObjectStorage {
        private final Map<String, StoredBytes> objects = new LinkedHashMap<>();

        @Override
        public String bucket() {
            return "test";
        }

        @Override
        public String put(String objectKey, InputStream inputStream, long contentLength, String contentType) {
            try {
                objects.put(objectKey, new StoredBytes(inputStream.readAllBytes(), contentType, contentLength));
                return objectKey;
            }
            catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public StoredObject get(String objectKey) {
            StoredBytes stored = objects.get(objectKey);
            if (stored == null) {
                throw new BizException("文件不存在");
            }
            return new StoredObject(objectKey, stored.contentType, stored.contentLength, new ByteArrayInputStream(stored.bytes));
        }

        @Override
        public void delete(String objectKey) {
            objects.remove(objectKey);
        }
    }

    private record StoredBytes(byte[] bytes, String contentType, long contentLength) {
    }
}
