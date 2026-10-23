package online.yudream.base.application.platform.workspace.service;

import online.yudream.base.application.platform.workspace.dto.WorkspaceEntryDTO;
import online.yudream.base.application.platform.workspace.dto.WorkspaceTrashDTO;
import online.yudream.base.application.system.file.dto.FileContentDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.workspace.aggregate.WorkspaceFile;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class WorkspaceAppServiceTest {

    private static final Long USER = 7L;

    private static List<String> paths(WorkspaceTestFixture f) {
        return f.service.overview(USER).entries().stream().map(e -> e.path() + ("DIRECTORY".equals(e.kind()) ? "/" : "")).toList();
    }

    @Test
    void writesCreateParentsVersionContentInObjectStorageAndGuardRevisions() {
        WorkspaceTestFixture f = new WorkspaceTestFixture();
        WorkspaceEntryDTO first = f.service.writeText(USER, "/notes/2026/plan.md", "# 计划", 0);
        assertEquals(List.of("notes/", "notes/2026/", "notes/2026/plan.md"), paths(f));
        assertEquals(1, first.revision());
        assertEquals("text/markdown", first.contentType());
        assertEquals("# 计划", f.service.read(USER, "notes/2026/plan.md").content());
        // 元数据不存正文：内容只在对象存储，键为 workspaces/<ws>/objects/<versionId>
        assertTrue(f.objects.keySet().stream().allMatch(key -> key.matches("workspaces/\\d+/objects/\\d+")));

        WorkspaceEntryDTO second = f.service.writeText(USER, "notes/2026/plan.md", "v2", 1);
        assertEquals(2, second.revision());
        assertEquals(2, f.service.versions(USER, "notes/2026/plan.md").size());
        // 相同内容不产生新版本；过期修订号拒绝保存（AI 或其他窗口已改过）
        assertEquals(2, f.service.writeText(USER, "notes/2026/plan.md", "v2", null).revision());
        assertThrows(BizException.class, () -> f.service.writeText(USER, "notes/2026/plan.md", "v3", 1));

        f.service.restoreVersion(USER, "notes/2026/plan.md", f.service.versions(USER, "notes/2026/plan.md").get(1).id());
        assertEquals("# 计划", f.service.read(USER, "notes/2026/plan.md").content());
        assertEquals(3, f.service.versions(USER, "notes/2026/plan.md").size());
        assertThrows(BizException.class, () -> f.service.writeText(USER, "notes", "x", null));
        assertThrows(BizException.class, () -> f.service.writeText(USER, "notes/2026/plan.md/inner.txt", "x", null));
    }

    @Test
    void historyKeepsTenVersionsAndDeletesOlderObjects() {
        WorkspaceTestFixture f = new WorkspaceTestFixture();
        for (int i = 0; i < 14; i++) {
            f.service.writeText(USER, "log.txt", "v" + i, null);
        }
        var history = f.service.versions(USER, "log.txt");
        assertEquals(10, history.size());
        assertEquals(14, history.getFirst().revision());
        assertTrue(history.getFirst().current());
        assertEquals(10, f.objects.size());
    }

    @Test
    void movingDirectoriesRewritesChildPathsAndRejectsCyclesOrCollisions() {
        WorkspaceTestFixture f = new WorkspaceTestFixture();
        f.service.writeText(USER, "src/a.py", "print(1)", null);
        f.service.writeText(USER, "src/lib/b.py", "x", null);
        f.service.writeText(USER, "docs/readme.md", "r", null);
        f.service.move(USER, "src", "projects/demo");
        assertEquals(List.of("docs/", "docs/readme.md", "projects/", "projects/demo/", "projects/demo/a.py",
                "projects/demo/lib/", "projects/demo/lib/b.py"), paths(f));
        assertEquals("print(1)", f.service.read(USER, "projects/demo/a.py").content());
        assertThrows(BizException.class, () -> f.service.move(USER, "projects", "projects/demo/inner"));
        assertThrows(BizException.class, () -> f.service.move(USER, "docs/readme.md", "projects/demo/a.py"));
        f.service.move(USER, "docs/readme.md", "docs/README.md");
        assertTrue(paths(f).contains("docs/README.md"));
    }

    @Test
    void deleteGoesToTrashAndRestoresWholeDirectoriesWithoutOverwriting() {
        WorkspaceTestFixture f = new WorkspaceTestFixture();
        f.service.writeText(USER, "data/a.csv", "1", null);
        f.service.writeText(USER, "data/b.csv", "2", null);
        assertEquals(3, f.service.delete(USER, "data"));
        assertEquals(List.of(), paths(f));
        List<WorkspaceTrashDTO> trash = f.service.trash(USER);
        assertEquals(1, trash.size());
        assertEquals(2, trash.getFirst().childCount());
        assertEquals("data", trash.getFirst().path());

        f.service.writeText(USER, "data/new.csv", "3", null);
        f.service.restore(USER, trash.getFirst().id());
        assertEquals(List.of("data/", "data (2)/", "data (2)/a.csv", "data (2)/b.csv", "data/new.csv"), paths(f));

        f.service.delete(USER, "data/new.csv");
        int objectsBefore = f.objects.size();
        f.service.purgeTrash(USER, f.service.trash(USER).getFirst().id());
        assertEquals(objectsBefore - 1, f.objects.size());
        assertTrue(f.service.trash(USER).isEmpty());
    }

    @Test
    void quotaCountsAllVersionsAndAdministratorsAreUnlimited() {
        WorkspaceTestFixture f = new WorkspaceTestFixture();
        f.config.put("workspaceQuotaMb", "1");
        String half = "x".repeat(600 * 1024);
        f.service.writeText(USER, "a.txt", half, null);
        BizException error = assertThrows(BizException.class, () -> f.service.writeText(USER, "a.txt", half + "y", null));
        assertTrue(error.getMessage().contains("空间不足"));
        assertEquals(1024L * 1024, f.service.overview(USER).quotaBytes());

        when(f.quotas.unlimited(USER)).thenReturn(true);
        f.service.writeText(USER, "a.txt", half + "y", null);
        assertNull(f.service.overview(USER).quotaBytes());
    }

    @Test
    void expiredTmpFilesAndOldTrashArePurged() {
        WorkspaceTestFixture f = new WorkspaceTestFixture();
        f.service.writeText(USER, "tmp/run-1/out.txt", "old", null);
        f.service.writeText(USER, "tmp/fresh.txt", "new", null);
        f.service.writeText(USER, "keep/a.txt", "keep", null);
        f.service.writeText(USER, "gone.txt", "bye", null);
        f.service.delete(USER, "gone.txt");
        for (WorkspaceFile file : f.files.values()) {
            if (file.getPath().equals("tmp/run-1/out.txt")) {
                file.setContentUpdatedAt(LocalDateTime.now().minusDays(8));
            }
            if (file.trashed()) {
                file.setDeletedAt(LocalDateTime.now().minusDays(31));
            }
        }
        f.service.purgeExpired();
        assertFalse(paths(f).contains("tmp/run-1/out.txt"));
        assertTrue(paths(f).containsAll(List.of("tmp/fresh.txt", "keep/a.txt")));
        assertTrue(f.service.trash(USER).isEmpty());
        assertEquals(2, f.objects.size());
    }

    @Test
    void attachmentsAreCopiedIntoTheChosenDirectoryWithOwnershipCheckedByFileService() {
        WorkspaceTestFixture f = new WorkspaceTestFixture();
        when(f.fileAppService.content(eq(55L), eq(USER), eq(false))).thenAnswer(call -> FileContentDTO.builder()
                .originalName("报告.pdf").contentType("application/pdf").contentLength(4L)
                .inputStream(new ByteArrayInputStream("%PDF".getBytes(StandardCharsets.UTF_8))).build());
        WorkspaceEntryDTO saved = f.service.saveAttachment(USER, "55", "uploads/2026");
        assertEquals("uploads/2026/报告.pdf", saved.path());
        assertEquals("application/pdf", saved.contentType());
        assertEquals("uploads/2026/报告 (2).pdf", f.service.saveAttachment(USER, "55", "uploads/2026").path());
        when(f.fileAppService.content(eq(56L), anyLong(), eq(false))).thenThrow(new BizException("无权访问该文件"));
        assertThrows(BizException.class, () -> f.service.saveAttachment(USER, "56", ""));
    }

    @Test
    void attachmentStreamsCloseWhenDirectoryOrFileNameValidationRejectsBeforeReading() {
        for (String name : List.of("报告.pdf", "..")) {
            WorkspaceTestFixture f = new WorkspaceTestFixture();
            TrackedInputStream input = new TrackedInputStream();
            when(f.fileAppService.content(55L, USER, false)).thenReturn(FileContentDTO.builder()
                    .originalName(name).contentType("application/pdf").contentLength(1L)
                    .inputStream(input).build());

            assertThrows(BizException.class,
                    () -> f.service.saveAttachment(USER, "55", name.equals("..") ? "" : "../outside"));

            assertTrue(input.closed);
            assertEquals(0, input.reads);
            assertTrue(f.objects.isEmpty());
        }
    }

    @Test
    void attachmentStreamsCloseOnSuccessfulCopyAndReadFailure() {
        WorkspaceTestFixture f = new WorkspaceTestFixture();
        TrackedInputStream success = new TrackedInputStream();
        when(f.fileAppService.content(55L, USER, false)).thenReturn(FileContentDTO.builder()
                .originalName("报告.pdf").inputStream(success).build());
        f.service.saveAttachment(USER, "55", "uploads");
        assertTrue(success.closed);
        assertTrue(success.reads > 0);

        TrackedInputStream failure = new TrackedInputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("读取失败");
            }
        };
        when(f.fileAppService.content(56L, USER, false)).thenReturn(FileContentDTO.builder()
                .originalName("失败.pdf").inputStream(failure).build());
        assertThrows(BizException.class, () -> f.service.saveAttachment(USER, "56", "uploads"));
        assertTrue(failure.closed);
    }

    private static class TrackedInputStream extends InputStream {
        boolean closed;
        int reads;

        @Override
        public int read() throws IOException {
            return reads++ == 0 ? 1 : -1;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @Test
    void scopedAccessCannotSeeOrEscapeOutsideItsRoot() {
        WorkspaceTestFixture f = new WorkspaceTestFixture();
        f.service.writeText(USER, "private/diary.md", "secret", null);
        WorkspaceAccess app = f.service.access(USER, "apps/report-bot");
        app.writeText("drafts/weekly.md", "# 周报");
        assertEquals(List.of("drafts", "drafts/weekly.md"), app.list("").stream().map(WorkspaceEntryDTO::path).toList());
        assertTrue(paths(f).contains("apps/report-bot/drafts/weekly.md"));
        assertThrows(BizException.class, () -> app.read("../../private/diary.md", 100));
        assertThrows(BizException.class, () -> app.delete(""));
        assertEquals("AI", f.service.overview(USER).entries().stream().filter(e -> e.path().endsWith("weekly.md")).findFirst().orElseThrow().updatedBy());
    }

    @Test
    void pathRulesRejectTraversalAndControlCharacters() {
        for (String path : List.of("", "../secret", "a/../../b", "dir/", "a\\b", "x".repeat(241), "bad\u0001name", "a//b", "./a")) {
            assertThrows(BizException.class, () -> WorkspacePaths.file(path), path);
        }
        assertEquals("ok/file.txt", WorkspacePaths.file("  /ok/file.txt "));
        assertEquals("", WorkspacePaths.directory("/"));
        assertEquals("a/b", WorkspacePaths.directory("a/b/"));
    }
}
