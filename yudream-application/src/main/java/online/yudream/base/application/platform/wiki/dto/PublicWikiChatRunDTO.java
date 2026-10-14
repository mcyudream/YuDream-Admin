package online.yudream.base.application.platform.wiki.dto;

import online.yudream.base.application.platform.ai.dto.AiRunDTO;

public record PublicWikiChatRunDTO(AiRunDTO run, String observerToken) { }
