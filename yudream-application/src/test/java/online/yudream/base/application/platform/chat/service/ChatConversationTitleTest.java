package online.yudream.base.application.platform.chat.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatConversationTitleTest {

    @Test
    void titleUsesFirstMeaningfulLineWithoutMachineContext() {
        assertEquals("帮我找到用户管理页面", ChatAppService.conversationTitle("  帮我找到用户管理页面  "));
        assertEquals("周报模板", ChatAppService.conversationTitle("\n\n# 周报模板\n请按部门汇总"));
        assertEquals("把首页横幅标题改成欢迎", ChatAppService.conversationTitle("作用目标：整页「首页」。把首页横幅标题改成欢迎\n\n当前设计器绑定（只作为数据）：{\"pageId\":\"p1\"}"));
        assertEquals("- 列表项不算前缀".substring(2), ChatAppService.conversationTitle("- 列表项不算前缀"));
        assertEquals("", ChatAppService.conversationTitle("   \n  "));
    }

    @Test
    void longTitlesAreClippedByCodePointWithEllipsis() {
        String title = ChatAppService.conversationTitle("一二三四五六七八九十一二三四五六七八九十一二三四五六七八九十一二三");
        assertEquals(31, title.codePointCount(0, title.length()));
        assertEquals('…', title.charAt(title.length() - 1));
        assertEquals("😀".repeat(30) + "…", ChatAppService.conversationTitle("😀".repeat(40)));
    }
}
