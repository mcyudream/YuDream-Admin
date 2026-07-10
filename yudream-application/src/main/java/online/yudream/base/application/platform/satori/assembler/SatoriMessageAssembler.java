package online.yudream.base.application.platform.satori.assembler;

import online.yudream.base.domain.platform.satori.message.SatoriMessage;
import online.yudream.base.domain.platform.satori.message.SatoriMessageBuilder;
import online.yudream.base.domain.platform.satori.message.SatoriMessageContent;
import online.yudream.base.domain.platform.satori.service.SatoriMessageEncoder;

import java.util.List;

public final class SatoriMessageAssembler {
    private SatoriMessageAssembler() {
    }

    public static String text(String value) {
        return encode(SatoriMessageBuilder.create().text(value == null ? "" : value).build());
    }

    public static String media(SatoriMessageContent.Type type, SatoriMessageContent.Attachment attachment) {
        SatoriMessageBuilder builder = SatoriMessageBuilder.create();
        switch (type) {
            case IMAGE -> builder.img(attachment.url(), attachment.title());
            case AUDIO -> builder.audio(attachment.url());
            case VIDEO -> builder.video(attachment.url());
            case FILE -> builder.file(attachment.url(), attachment.title());
            default -> throw new IllegalArgumentException("Unsupported media message type: " + type);
        }
        return encode(builder.build());
    }

    public static String composite(String text, List<SatoriMessageContent.Attachment> attachments) {
        SatoriMessageBuilder builder = SatoriMessageBuilder.create().text(text == null ? "" : text);
        for (SatoriMessageContent.Attachment attachment : attachments) {
            String contentType = attachment.contentType() == null ? "" : attachment.contentType().toLowerCase();
            if (contentType.startsWith("audio/")) builder.audio(attachment.url());
            else if (contentType.startsWith("video/")) builder.video(attachment.url());
            else if (contentType.startsWith("image/")) builder.img(attachment.url(), attachment.title());
            else builder.file(attachment.url(), attachment.title());
        }
        return encode(builder.build());
    }

    public static String encode(SatoriMessage message) {
        return new SatoriMessageEncoder().encode(message);
    }
}
