package online.yudream.base.interfaces.platform.render.assembler;

import online.yudream.base.application.platform.render.cmd.MessageRenderCmd;
import online.yudream.base.interfaces.platform.render.request.MessageRenderRequest;

public final class MessageRenderWebAssembler {
    private MessageRenderWebAssembler() {
    }

    public static MessageRenderCmd toCmd(MessageRenderRequest request) {
        MessageRenderCmd cmd = new MessageRenderCmd();
        cmd.setSourceType(request.getSourceType());
        cmd.setContent(request.getContent());
        cmd.setWidth(request.getWidth());
        cmd.setMaxHeight(request.getMaxHeight());
        cmd.setTransparent(request.getTransparent());
        cmd.setFormat(request.getFormat());
        cmd.setOptions(request.getOptions());
        return cmd;
    }
}
