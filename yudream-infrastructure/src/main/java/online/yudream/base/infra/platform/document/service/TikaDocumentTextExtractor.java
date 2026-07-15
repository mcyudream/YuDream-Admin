package online.yudream.base.infra.platform.document.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.document.service.DocumentTextExtractor;
import online.yudream.base.domain.platform.document.valobj.DocumentSource;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class TikaDocumentTextExtractor implements DocumentTextExtractor {

    private static final String DATA_URL_PREFIX = "data:";
    private static final String OCTET_STREAM = "application/octet-stream";
    private static final long DEFAULT_PARSE_TIMEOUT_MILLIS = 30_000L;
    private static final ExecutorService PARSER_EXECUTOR = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("agent-document-parser-", 0).factory()
    );
    private final long maxBytes;
    private final long parseTimeoutMillis;

    public TikaDocumentTextExtractor(long maxBytes) {
        this(maxBytes, DEFAULT_PARSE_TIMEOUT_MILLIS);
    }

    @Autowired
    public TikaDocumentTextExtractor(
            @Value("${yudream.platform.agent.document.max-bytes:10485760}") long maxBytes,
            @Value("${yudream.platform.agent.document.parse-timeout-millis:30000}") long parseTimeoutMillis) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("文档大小限制必须大于 0");
        }
        if (parseTimeoutMillis <= 0) {
            throw new IllegalArgumentException("文档解析超时时间必须大于 0");
        }
        this.maxBytes = maxBytes;
        this.parseTimeoutMillis = parseTimeoutMillis;
    }

    @Override
    public String extract(DocumentSource source) {
        DecodedDocument document = decode(source);
        Future<String> parsing = PARSER_EXECUTOR.submit(() -> parse(document));
        try {
            return parsing.get(parseTimeoutMillis, TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException exception) {
            parsing.cancel(true);
            throw new BizException("文档解析超时");
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BizException("文档解析被中断");
        }
        catch (ExecutionException exception) {
            if (exception.getCause() instanceof BizException bizException) {
                throw bizException;
            }
            throw new BizException("文档文本提取失败：" + readableMessage(exception));
        }
    }

    private String parse(DecodedDocument document) {
        AutoDetectParser parser = new AutoDetectParser();
        ParseContext context = new ParseContext();
        Metadata metadata = metadata(document);
        validateDeclaredType(document.contentType(), parser.getSupportedTypes(context));

        int characterLimit = (int) Math.min(Integer.MAX_VALUE, maxBytes);
        BodyContentHandler handler = new BodyContentHandler(characterLimit);
        try (ByteArrayInputStream input = new ByteArrayInputStream(document.content())) {
            parser.parse(input, handler, metadata, context);
            validateDetectedType(metadata.get(Metadata.CONTENT_TYPE), parser.getSupportedTypes(context));
            return handler.toString();
        }
        catch (IOException | TikaException | SAXException e) {
            throw new BizException("文档文本提取失败：" + readableMessage(e));
        }
    }

    private DecodedDocument decode(DocumentSource source) {
        if (source == null || source.content() == null || source.content().isBlank()) {
            throw new BizException("文档内容不能为空");
        }
        if (source.content().regionMatches(true, 0, DATA_URL_PREFIX, 0, DATA_URL_PREFIX.length())) {
            return decodeDataUrl(source);
        }
        return new DecodedDocument(
                decodeBase64(source.content()),
                normalizeContentType(source.contentType()),
                source.fileName()
        );
    }

    private DecodedDocument decodeDataUrl(DocumentSource source) {
        int separator = source.content().indexOf(',');
        if (separator < DATA_URL_PREFIX.length()) {
            throw invalidInput();
        }
        String header = source.content().substring(DATA_URL_PREFIX.length(), separator);
        String payload = source.content().substring(separator + 1);
        String[] attributes = header.split(";");
        String dataUrlType = attributes.length > 0 && attributes[0].contains("/")
                ? normalizeContentType(attributes[0])
                : "text/plain";
        boolean base64 = false;
        for (String attribute : attributes) {
            if ("base64".equalsIgnoreCase(attribute.trim())) {
                base64 = true;
                break;
            }
        }
        byte[] content;
        if (base64) {
            content = decodeBase64(payload);
        }
        else {
            if (payload.length() > maxDataUrlPayloadLength()) {
                throw tooLarge();
            }
            try {
                content = URLDecoder.decode(payload, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
                validateSize(content.length);
            }
            catch (IllegalArgumentException e) {
                throw invalidInput();
            }
        }
        String contentType = dataUrlType == null ? normalizeContentType(source.contentType()) : dataUrlType;
        return new DecodedDocument(content, contentType, source.fileName());
    }

    private byte[] decodeBase64(String encoded) {
        long maximumEncodedLength = ((maxBytes + 2) / 3) * 4;
        if (encoded.length() > maximumEncodedLength + maxBytes) {
            throw tooLarge();
        }
        String compact = encoded.replaceAll("\\s", "");
        if (compact.isEmpty()) {
            throw new BizException("文档内容不能为空");
        }
        if (compact.length() > maximumEncodedLength) {
            throw tooLarge();
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(compact);
            if (decoded.length == 0) {
                throw new BizException("文档内容不能为空");
            }
            validateSize(decoded.length);
            return decoded;
        }
        catch (IllegalArgumentException e) {
            throw invalidInput();
        }
    }

    private Metadata metadata(DecodedDocument document) {
        Metadata metadata = new Metadata();
        if (document.contentType() != null) {
            metadata.set(Metadata.CONTENT_TYPE, document.contentType());
        }
        if (document.fileName() != null && !document.fileName().isBlank()) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, document.fileName());
        }
        return metadata;
    }

    private void validateDeclaredType(String contentType, Set<MediaType> supportedTypes) {
        if (contentType == null || OCTET_STREAM.equals(contentType)) {
            return;
        }
        MediaType mediaType = MediaType.parse(contentType);
        if (mediaType == null || !supportedTypes.contains(mediaType.getBaseType())) {
            throw unsupported(contentType);
        }
    }

    private void validateDetectedType(String contentType, Set<MediaType> supportedTypes) {
        String normalized = normalizeContentType(contentType);
        if (normalized == null || OCTET_STREAM.equals(normalized)) {
            throw unsupported(normalized == null ? OCTET_STREAM : normalized);
        }
        MediaType mediaType = MediaType.parse(normalized);
        if (mediaType == null || !supportedTypes.contains(mediaType.getBaseType())) {
            throw unsupported(normalized);
        }
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        int parameter = contentType.indexOf(';');
        String normalized = parameter >= 0 ? contentType.substring(0, parameter) : contentType;
        return normalized.trim().toLowerCase(Locale.ROOT);
    }

    private void validateSize(long size) {
        if (size > maxBytes) {
            throw tooLarge();
        }
    }

    private long maxDataUrlPayloadLength() {
        return maxBytes > Long.MAX_VALUE / 3 ? Long.MAX_VALUE : maxBytes * 3;
    }

    private BizException invalidInput() {
        return new BizException("文档输入不是有效的 Data URL 或 Base64 内容");
    }

    private BizException tooLarge() {
        return new BizException("文档大小不能超过 " + maxBytes + " 字节");
    }

    private BizException unsupported(String contentType) {
        return new BizException("不支持的文档类型：" + contentType);
    }

    private String readableMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private record DecodedDocument(byte[] content, String contentType, String fileName) {
    }
}
