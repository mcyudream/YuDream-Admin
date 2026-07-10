package online.yudream.base.interfaces.platform.satori.assembler;

import online.yudream.base.application.platform.satori.service.MessageDeliveryAppService;
import online.yudream.base.application.platform.satori.cmd.SatoriInternalInvokeCmd;
import online.yudream.base.interfaces.platform.satori.request.SatoriInternalInvokeRequest;
import online.yudream.base.domain.platform.satori.message.SatoriMessageContent;
import online.yudream.base.interfaces.platform.satori.request.SatoriMessageSendRequest;
import online.yudream.base.interfaces.platform.satori.res.SatoriMessageSendRes;

import java.util.List;

public final class SatoriMessageWebAssembler {
    private SatoriMessageWebAssembler() {
    }

    public static MessageDeliveryAppService.DeliveryRequest toRequest(Long connectionId, SatoriMessageSendRequest request) {
        List<SatoriMessageContent.Attachment> attachments = request.getAttachments() == null ? List.of()
                : request.getAttachments().stream().map(item -> new SatoriMessageContent.Attachment(item.getUrl(), item.getTitle(), item.getContentType())).toList();
        return new MessageDeliveryAppService.DeliveryRequest(connectionId, request.getPlatform(), request.getUserId(), request.getChannelId(),
                new SatoriMessageContent(request.getType(), request.getContent(), attachments, request.getReferrer()));
    }

    public static SatoriMessageSendRes toRes(MessageDeliveryAppService.DeliveryResult result) {
        return SatoriMessageSendRes.builder()
                .messageIds(result.messages().stream().map(item -> item.id()).toList())
                .rendered(result.rendered())
                .degraded(result.degraded())
                .build();
    }

    public static SatoriInternalInvokeCmd toCmd(Long connectionId, SatoriInternalInvokeRequest request) {
        SatoriInternalInvokeCmd cmd = new SatoriInternalInvokeCmd();
        cmd.setConnectionId(connectionId);
        cmd.setPlatform(request.getPlatform());
        cmd.setUserId(request.getUserId());
        cmd.setMethod(request.getMethod());
        cmd.setPayload(request.getPayload());
        return cmd;
    }
}
