package online.yudream.base.interfaces.platform.wiki.assembler;

import online.yudream.base.application.platform.wiki.dto.PublicWikiChatRunDTO;
import online.yudream.base.interfaces.platform.ai.assembler.AiRunWebAssembler;
import online.yudream.base.interfaces.platform.wiki.res.PublicWikiChatRunRes;

public final class PublicWikiChatRunWebAssembler {
    private PublicWikiChatRunWebAssembler() { }
    public static PublicWikiChatRunRes run(PublicWikiChatRunDTO dto) {
        return new PublicWikiChatRunRes(AiRunWebAssembler.run(dto.run()), dto.observerToken());
    }
}
