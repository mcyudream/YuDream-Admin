package online.yudream.base.application.platform.agent.support;

import online.yudream.base.domain.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSkillZipParserTest {

    @Test
    void parsesRootLevelSkillWithResources() throws Exception {
        byte[] zip = zip(builder -> {
            entry(builder, "SKILL.md", """
                    ---
                    name: code-reviewer
                    description: 当用户需要代码评审时使用。
                    ---
                    # Code Reviewer
                    按步骤评审。
                    """);
            entry(builder, "references/style-guide.md", "# 风格指南\n正文。");
            entry(builder, "scripts/run.sh", "echo ok");
        });

        AgentSkillZipParser.Parsed parsed = AgentSkillZipParser.parse(zip, "code-reviewer.zip");

        assertEquals("code-reviewer", parsed.name());
        assertEquals("当用户需要代码评审时使用。", parsed.description());
        assertTrue(parsed.content().contains("# Code Reviewer"));
        assertFalse(parsed.content().contains("name:"));
        assertEquals("# 风格指南\n正文。", parsed.resources().get("references/style-guide.md"));
        assertEquals("echo ok", parsed.resources().get("scripts/run.sh"));
        assertTrue(parsed.skippedBinary().isEmpty());
    }

    @Test
    void organizesSingleTopLevelFolderAutomatically() throws Exception {
        byte[] zip = zip(builder -> {
            entry(builder, "my-skill/SKILL.md", "---\nname: my-skill\ndescription: 描述\n---\n正文");
            entry(builder, "my-skill/references/a.md", "A");
        });

        AgentSkillZipParser.Parsed parsed = AgentSkillZipParser.parse(zip, "any.zip");

        assertEquals("my-skill", parsed.name());
        assertEquals("A", parsed.resources().get("references/a.md"));
    }

    @Test
    void fallsBackToFolderNameWhenFrontmatterNameMissing() throws Exception {
        byte[] zip = zip(builder -> entry(builder, "notes/SKILL.md",
                "---\ndescription: 只有描述\n---\n正文"));

        AgentSkillZipParser.Parsed parsed = AgentSkillZipParser.parse(zip, "any.zip");

        assertEquals("notes", parsed.name());
        assertEquals("只有描述", parsed.description());
    }

    @Test
    void rejectsZipWithoutSkillMd() throws Exception {
        byte[] zip = zip(builder -> entry(builder, "readme.txt", "没有技能"));

        BizException error = assertThrows(BizException.class,
                () -> AgentSkillZipParser.parse(zip, "any.zip"));
        assertTrue(error.getMessage().contains("SKILL.md"));
    }

    @Test
    void rejectsMultipleSkillMdAtSameDepth() throws Exception {
        byte[] zip = zip(builder -> {
            entry(builder, "a/SKILL.md", "---\ndescription: A\n---\nA");
            entry(builder, "b/SKILL.md", "---\ndescription: B\n---\nB");
        });

        BizException error = assertThrows(BizException.class,
                () -> AgentSkillZipParser.parse(zip, "any.zip"));
        assertTrue(error.getMessage().contains("多个"));
    }

    @Test
    void rejectsMissingDescription() throws Exception {
        byte[] zip = zip(builder -> entry(builder, "SKILL.md", "---\nname: x\n---\n正文"));

        BizException error = assertThrows(BizException.class,
                () -> AgentSkillZipParser.parse(zip, "any.zip"));
        assertTrue(error.getMessage().contains("description"));
    }

    @Test
    void skipsBinaryFilesAndBlocksPathTraversal() throws Exception {
        byte[] zip = zip(builder -> {
            entry(builder, "SKILL.md", "---\nname: x\ndescription: d\n---\n正文");
            entry(builder, "assets/logo.png", new byte[] {0x00, 0x01, 0x02});
            entry(builder, "../evil.txt", "逃逸");
        });

        AgentSkillZipParser.Parsed parsed = AgentSkillZipParser.parse(zip, "any.zip");

        assertTrue(parsed.resources().isEmpty());
        assertEquals(1, parsed.skippedBinary().size());
        assertTrue(parsed.skippedBinary().stream().anyMatch(name -> name.contains("logo.png")));
    }

    interface ZipBuilder {
        void build(ZipOutputStream out) throws Exception;
    }

    private static byte[] zip(ZipBuilder builder) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ZipOutputStream out = new ZipOutputStream(buffer);
        builder.build(out);
        out.close();
        return buffer.toByteArray();
    }

    private static void entry(ZipOutputStream out, String path, String content) throws Exception {
        out.putNextEntry(new ZipEntry(path));
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

    private static void entry(ZipOutputStream out, String path, byte[] content) throws Exception {
        out.putNextEntry(new ZipEntry(path));
        out.write(content);
        out.closeEntry();
    }
}
