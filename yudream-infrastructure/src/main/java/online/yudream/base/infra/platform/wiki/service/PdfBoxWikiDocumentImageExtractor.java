package online.yudream.base.infra.platform.wiki.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.document.valobj.DocumentSource;
import online.yudream.base.domain.platform.wiki.service.WikiDocumentImageExtractor;
import online.yudream.base.domain.platform.wiki.valobj.WikiExtractedImage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * 使用 Apache PDFBox 从 PDF 中抽取内嵌图片（llm_wiki 的“抽图”能力）。
 */
@Service
public class PdfBoxWikiDocumentImageExtractor implements WikiDocumentImageExtractor {

    private static final String DATA_URL_PREFIX = "data:";

    @Override
    public List<WikiExtractedImage> extractImages(DocumentSource source) {
        byte[] content = decode(source);
        if (content == null || content.length == 0) {
            return List.of();
        }
        try (PDDocument document = Loader.loadPDF(content)) {
            List<WikiExtractedImage> images = new ArrayList<>();
            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                PDPage page = document.getPage(pageIndex);
                PDResources resources = page.getResources();
                if (resources == null) {
                    continue;
                }
                int sequence = 0;
                for (COSName name : resources.getXObjectNames()) {
                    PDXObject xObject = resources.getXObject(name);
                    if (xObject instanceof PDImageXObject image) {
                        images.add(render(pageIndex + 1, sequence++, image));
                    }
                }
            }
            return images;
        }
        catch (IOException exception) {
            throw new BizException("PDF 图片抽取失败：" + readableMessage(exception));
        }
    }

    private WikiExtractedImage render(int pageNumber, int sequence, PDImageXObject image) throws IOException {
        BufferedImage rendered = image.getImage();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(rendered, "png", output);
        return new WikiExtractedImage(
                pageNumber,
                "image/png",
                "page-" + pageNumber + "-" + sequence + ".png",
                output.toByteArray(),
                image.getWidth(),
                image.getHeight()
        );
    }

    private byte[] decode(DocumentSource source) {
        if (source == null || source.content() == null || source.content().isBlank()) {
            return null;
        }
        String content = source.content();
        if (content.regionMatches(true, 0, DATA_URL_PREFIX, 0, DATA_URL_PREFIX.length())) {
            int separator = content.indexOf(',');
            if (separator < DATA_URL_PREFIX.length()) {
                throw new BizException("文档输入不是有效的 Data URL 或 Base64 内容");
            }
            String header = content.substring(DATA_URL_PREFIX.length(), separator);
            String payload = content.substring(separator + 1);
            boolean base64 = header.toLowerCase(Locale.ROOT).contains(";base64");
            if (base64) {
                return Base64.getDecoder().decode(payload.replaceAll("\\s", ""));
            }
            try {
                return URLDecoder.decode(payload, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
            }
            catch (IllegalArgumentException exception) {
                throw new BizException("文档输入不是有效的 Data URL 或 Base64 内容");
            }
        }
        return Base64.getDecoder().decode(content.replaceAll("\\s", ""));
    }

    private String readableMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
