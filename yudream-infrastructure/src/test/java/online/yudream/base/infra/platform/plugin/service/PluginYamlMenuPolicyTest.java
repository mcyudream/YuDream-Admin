package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.plugin.enumerate.PluginMenuMode;
import online.yudream.base.domain.platform.plugin.valobj.PluginMenuPolicy;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginYamlMenuPolicyTest {

    @Test
    void anOldManifestKeepsLegacyModeWithoutConsultingItsBusinessVersion() {
        PluginMenuPolicy policy = read("");
        assertEquals(PluginMenuMode.LEGACY, policy.mode());
        assertFalse(policy.categorized());
    }

    @Test
    void anExplicitEmptyBlockUsesTheSharedOtherCategory() {
        for (String menu : new String[]{"menu: {}\n", "menu:\n"}) {
            PluginMenuPolicy policy = read(menu);
            assertTrue(policy.categorized());
            assertEquals("other", policy.category().code());
            assertEquals("其他", policy.category().name());
        }
    }

    @Test
    void builtInCategoriesProvideStableCodesAndLocalizedNames() {
        for (String[] category : new String[][]{{"mc", "MC"}, {"todo", "待办"}, {"social", "社交"}}) {
            PluginMenuPolicy policy = read("menu:\n  category: " + category[0] + "\n");
            assertTrue(policy.categorized());
            assertEquals("plugin-category:" + category[0], policy.category().menuCode());
            assertEquals(category[1], policy.category().name());
        }
    }

    @Test
    void customCategoriesCanDeclareDisplayMetadata() {
        PluginMenuPolicy policy = read("""
                menu:
                  category:
                    code: knowledge
                    name: 知识
                    icon: i-ri:book-line
                    sort: 75
                """);
        assertEquals("knowledge", policy.category().code());
        assertEquals("知识", policy.category().name());
        assertEquals("i-ri:book-line", policy.category().icon());
        assertEquals(75, policy.category().sort());
        assertEquals("knowledge", read("menu:\n  category: knowledge\n").category().name());
    }

    @Test
    void explicitExceptionsKeepIndependentRoots() {
        for (String mode : new String[]{"LEGACY", "independent"}) {
            assertFalse(read("menu:\n  mode: " + mode + "\n  category: mc\n").categorized());
        }
    }

    @Test
    void malformedDeclarationsFailBeforePluginLoading() {
        for (String menu : new String[]{
                "menu: true\n", "menu: []\n", "menu:\n  mode: UNKNOWN\n",
                "menu:\n  category: 12\n", "menu:\n  category: []\n",
                "menu:\n  category: {name: 知识}\n", "menu:\n  category: Bad_Code\n",
                "menu:\n  category: {code: mc, sort: 2.5}\n"
        }) {
            assertThrows(BizException.class, () -> read(menu), menu);
        }
    }

    private PluginMenuPolicy read(String menu) {
        String yaml = "name: demo\nmain: com.example.Demo\nversion: 999.0.0\n" + menu;
        return new PluginYamlDescriptorReader().readManifest(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))).menuPolicy();
    }
}
