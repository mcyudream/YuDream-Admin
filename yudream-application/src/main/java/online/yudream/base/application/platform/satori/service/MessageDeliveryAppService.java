package online.yudream.base.application.platform.satori.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.satori.assembler.SatoriMessageAssembler;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.render.model.RenderModels.RenderRequest;
import online.yudream.base.domain.platform.render.model.RenderModels.RenderedImage;
import online.yudream.base.domain.platform.render.model.RenderModels.SourceType;
import online.yudream.base.domain.platform.render.service.MessageRenderGateway;
import online.yudream.base.domain.platform.satori.aggregate.SatoriConnection;
import online.yudream.base.domain.platform.satori.aggregate.SatoriLogin;
import online.yudream.base.domain.platform.satori.message.SatoriMessageContent;
import online.yudream.base.domain.platform.satori.model.SatoriApiModels.MessageCreate;
import online.yudream.base.domain.platform.satori.model.SatoriApiModels.SatoriApiContext;
import online.yudream.base.domain.platform.satori.model.SatoriApiModels.UploadFile;
import online.yudream.base.domain.platform.satori.repo.SatoriConnectionRepo;
import online.yudream.base.domain.platform.satori.repo.SatoriLoginRepo;
import online.yudream.base.domain.platform.satori.service.PlatformCapabilityProfileFactory;
import online.yudream.base.domain.platform.satori.service.SatoriApiGateway;
import online.yudream.base.domain.platform.satori.service.SatoriOperationLogger;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Coordinates one logical message into the concrete Satori messages accepted by an adapter. */
@Service
@RequiredArgsConstructor
public class MessageDeliveryAppService {
    public static final String SATORI_CAPABILITY_CODE = "satori";
    public static final String RENDER_CAPABILITY_CODE = "message-render";

    private final CapabilityAppService capabilityAppService;
    private final SatoriConnectionRepo connectionRepo;
    private final SatoriLoginRepo loginRepo;
    private final SatoriApiGateway apiGateway;
    private final MessageRenderGateway renderGateway;
    private final SatoriOperationLogger operationLogger;
    private final MarkdownToSatoriConverter markdownConverter = new MarkdownToSatoriConverter();

    public DeliveryResult deliver(DeliveryRequest request) {
        capabilityAppService.ensureEnabled(SATORI_CAPABILITY_CODE, "Satori 平台");
        validate(request);
        SatoriConnection connection = connectionRepo.findById(request.connectionId())
                .orElseThrow(() -> new BizException("Satori 连接不存在"));
        if (!connection.enabled()) throw new BizException("Satori 连接未启用");
        SatoriLogin login = loginRepo.findByNaturalKey(request.connectionId(), request.platform(), request.userId()).orElse(null);
        PlatformCapabilityProfileFactory.Profile profile = PlatformCapabilityProfileFactory.from(login);
        SatoriApiContext context = new SatoriApiContext(connection.getBaseUrl(), connection.getToken(), request.platform(), request.userId());
        PreparedContent prepared = prepare(request.content(), profile, context);
        List<online.yudream.base.domain.platform.satori.model.SatoriModels.SatoriMessage> messages = new ArrayList<>();
        try {
            for (String payload : prepared.payloads()) {
                // message.create is intentionally never retried: adapters may already have accepted it.
                messages.addAll(apiGateway.messageCreate(context, new MessageCreate(request.channelId(), payload, request.content().referrer())));
            }
            operationLogger.info(connection.getId(), "MESSAGE", "message.create", "消息投递成功，共 " + messages.size() + " 条");
        } catch (RuntimeException exception) {
            operationLogger.error(connection.getId(), "MESSAGE", "message.create", exception.getMessage());
            throw exception;
        }
        return new DeliveryResult(messages, prepared.rendered(), prepared.degraded());
    }

    private PreparedContent prepare(SatoriMessageContent content, PlatformCapabilityProfileFactory.Profile profile,
                                    SatoriApiContext context) {
        return switch (content.type()) {
            case TEXT -> new PreparedContent(List.of(SatoriMessageAssembler.text(content.content())), false, false);
            case SATORI -> new PreparedContent(List.of(content.content()), false, false);
            case MARKDOWN -> profile.supportsRichText()
                    ? new PreparedContent(List.of(SatoriMessageAssembler.encode(markdownConverter.convert(content.content()))), false, false)
                    : rendered(content.content(), SourceType.MARKDOWN, context);
            case HTML -> profile.supportsRichText()
                    ? new PreparedContent(List.of(content.content()), false, false)
                    : rendered(content.content(), SourceType.HTML, context);
            case IMAGE, AUDIO, VIDEO, FILE -> media(content, profile);
            case COMPOSITE -> composite(content, profile, context);
        };
    }

    private PreparedContent media(SatoriMessageContent content, PlatformCapabilityProfileFactory.Profile profile) {
        List<SatoriMessageContent.Attachment> attachments = attachmentsOrContent(content);
        List<String> payloads = new ArrayList<>();
        for (SatoriMessageContent.Attachment attachment : attachments) {
            if (profile.supportsMedia()) payloads.add(SatoriMessageAssembler.media(content.type(), attachment));
            else payloads.add(SatoriMessageAssembler.text(attachment.url()));
        }
        return new PreparedContent(payloads, false, !profile.supportsMedia());
    }

    private PreparedContent composite(SatoriMessageContent content, PlatformCapabilityProfileFactory.Profile profile,
                                      SatoriApiContext context) {
        if (profile.supportsRichText() && profile.supportsMedia()) {
            return new PreparedContent(List.of(SatoriMessageAssembler.composite(content.content(), content.attachments())), false, false);
        }
        List<String> payloads = new ArrayList<>();
        if (!content.content().isBlank()) {
            if (profile.supportsRichText()) payloads.add(SatoriMessageAssembler.text(content.content()));
            else payloads.addAll(rendered(content.content(), SourceType.MARKDOWN, context).payloads());
        }
        for (SatoriMessageContent.Attachment attachment : content.attachments()) {
            SatoriMessageContent.Type type = mediaType(attachment);
            payloads.addAll(media(new SatoriMessageContent(type, "", List.of(attachment), Map.of()), profile).payloads());
        }
        if (payloads.isEmpty()) payloads.add(SatoriMessageAssembler.text(""));
        return new PreparedContent(payloads, !profile.supportsRichText(), true);
    }

    private PreparedContent rendered(String content, SourceType sourceType, SatoriApiContext context) {
        capabilityAppService.ensureEnabled(RENDER_CAPABILITY_CODE, "消息渲染");
        RenderedImage image = renderGateway.render(new RenderRequest(sourceType, content, null, null, null, "png", Map.of()));
        String url = uploadRenderedImage(context, image);
        return new PreparedContent(List.of(SatoriMessageAssembler.media(SatoriMessageContent.Type.IMAGE,
                new SatoriMessageContent.Attachment(url, "message.png", image.contentType()))), true, true);
    }

    private String uploadRenderedImage(SatoriApiContext context, RenderedImage image) {
        Map<String, String> result = apiGateway.uploadCreate(context, List.of(new UploadFile("file", "message.png", image.contentType(), image.content())));
        return result.values().stream().filter(value -> value != null && !value.isBlank()).findFirst()
                .orElseThrow(() -> new BizException("Satori 上传未返回可访问的图片地址"));
    }

    private List<SatoriMessageContent.Attachment> attachmentsOrContent(SatoriMessageContent content) {
        if (!content.attachments().isEmpty()) return content.attachments();
        if (content.content().isBlank()) throw new BizException("媒体消息缺少资源地址");
        return List.of(new SatoriMessageContent.Attachment(content.content(), null, null));
    }

    private SatoriMessageContent.Type mediaType(SatoriMessageContent.Attachment attachment) {
        String contentType = attachment.contentType() == null ? "" : attachment.contentType().toLowerCase();
        if (contentType.startsWith("audio/")) return SatoriMessageContent.Type.AUDIO;
        if (contentType.startsWith("video/")) return SatoriMessageContent.Type.VIDEO;
        if (contentType.startsWith("image/")) return SatoriMessageContent.Type.IMAGE;
        return SatoriMessageContent.Type.FILE;
    }

    private void validate(DeliveryRequest request) {
        if (request == null || request.connectionId() == null || blank(request.platform()) || blank(request.userId())
                || blank(request.channelId()) || request.content() == null) {
            throw new BizException("Satori 消息投递参数不完整");
        }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    public record DeliveryRequest(Long connectionId, String platform, String userId, String channelId,
                                  SatoriMessageContent content) {
    }

    public record DeliveryResult(List<online.yudream.base.domain.platform.satori.model.SatoriModels.SatoriMessage> messages,
                                 boolean rendered, boolean degraded) {
        public DeliveryResult { messages = messages == null ? List.of() : List.copyOf(messages); }
    }

    private record PreparedContent(List<String> payloads, boolean rendered, boolean degraded) {
        private PreparedContent { payloads = List.copyOf(payloads); }
    }
}
