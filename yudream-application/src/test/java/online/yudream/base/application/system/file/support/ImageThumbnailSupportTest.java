package online.yudream.base.application.system.file.support;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageThumbnailSupportTest {

    @Test
    void rasterizableAcceptsCommonPhotoFormatsOnly() {
        assertTrue(ImageThumbnailSupport.rasterizable("cover.PNG", "image/png"));
        assertTrue(ImageThumbnailSupport.rasterizable("a.JPG", null));
        assertTrue(ImageThumbnailSupport.rasterizable("shot.webp", "image/webp"));
        assertTrue(ImageThumbnailSupport.rasterizable(null, "image/jpeg"));
        assertFalse(ImageThumbnailSupport.rasterizable("logo.svg", "image/svg+xml"));
        assertFalse(ImageThumbnailSupport.rasterizable("favicon.ico", "image/x-icon"));
        assertFalse(ImageThumbnailSupport.rasterizable("note.pdf", "application/pdf"));
        assertFalse(ImageThumbnailSupport.rasterizable(null, null));
    }

    @Test
    void thumbnailObjectKeyAppendsStableSuffix() {
        assertEquals("timeline/2026/1.jpg.thumb.jpg", ImageThumbnailSupport.thumbnailObjectKey("timeline/2026/1.jpg"));
        assertNull(ImageThumbnailSupport.thumbnailObjectKey(" "));
    }

    @Test
    void thumbnailJpegShrinksLargePng() throws Exception {
        byte[] png = samplePng(1200, 800, Color.RED);
        byte[] jpeg = ImageThumbnailSupport.thumbnailJpeg(new ByteArrayInputStream(png));
        assertNotNull(jpeg);
        assertTrue(jpeg.length < png.length);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertNotNull(decoded);
        assertTrue(decoded.getWidth() <= ImageThumbnailSupport.MAX_EDGE);
        assertTrue(decoded.getHeight() <= ImageThumbnailSupport.MAX_EDGE);
        assertTrue(decoded.getWidth() >= ImageThumbnailSupport.MAX_EDGE - 1
                || decoded.getHeight() >= ImageThumbnailSupport.MAX_EDGE - 1);
    }

    @Test
    void thumbnailJpegRejectsGarbage() {
        assertNull(ImageThumbnailSupport.thumbnailJpeg(new ByteArrayInputStream("not-an-image".getBytes(StandardCharsets.UTF_8))));
        assertNull(ImageThumbnailSupport.thumbnailJpeg(null));
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
}
