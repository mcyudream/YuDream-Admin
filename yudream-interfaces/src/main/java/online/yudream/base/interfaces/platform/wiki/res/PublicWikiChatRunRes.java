package online.yudream.base.interfaces.platform.wiki.res;

import online.yudream.base.interfaces.platform.ai.res.AiRunRes;

public record PublicWikiChatRunRes(AiRunRes run, String observerToken) { }
