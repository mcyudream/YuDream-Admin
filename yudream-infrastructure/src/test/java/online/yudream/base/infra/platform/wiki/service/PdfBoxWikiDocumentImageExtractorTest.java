package online.yudream.base.infra.platform.wiki.service;

import online.yudream.base.domain.platform.document.valobj.DocumentSource;
import online.yudream.base.domain.platform.wiki.valobj.WikiExtractedImage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfBoxWikiDocumentImageExtractorTest {

    @Test
    void extractsEmbeddedImageFromPdf() throws Exception {
        byte[] png = pngBytes();
        byte[] pdf = pdfWithImage(png);
        DocumentSource source = DocumentSource.base64(Base64.getEncoder().encodeToString(pdf), "application/pdf", "test.pdf");

        List<WikiExtractedImage> images = new PdfBoxWikiDocumentImageExtractor().extractImages(source);

        assertFalse(images.isEmpty());
        WikiExtractedImage image = images.get(0);
        assertEquals("image/png", image.contentType());
        assertEquals(1, image.pageNumber());
        assertTrue(image.content().length > 0);
    }

    private byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(12, 12, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, 12, 12);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private byte[] pdfWithImage(byte[] png) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDImageXObject image = PDImageXObject.createFromByteArray(document, png, "embedded");
            try (PDPageContentStream content = new PDPageContentStream(document, page,
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                content.drawImage(image, 20, 20, 80, 80);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }
}
