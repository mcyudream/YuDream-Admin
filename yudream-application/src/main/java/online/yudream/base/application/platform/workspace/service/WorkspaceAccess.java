package online.yudream.base.application.platform.workspace.service;

import online.yudream.base.application.platform.workspace.assembler.WorkspaceAssembler;
import online.yudream.base.application.platform.workspace.dto.WorkspaceEntryDTO;
import online.yudream.base.application.platform.workspace.dto.WorkspaceFileContentDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.workspace.aggregate.UserWorkspace;
import online.yudream.base.domain.platform.workspace.aggregate.WorkspaceFile;
import online.yudream.base.domain.platform.workspace.enumerate.WorkspaceActor;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * AI 工具看到的工作区：路径相对 root（共享应用为整个工作区，受限应用为 apps/&lt;code&gt;），
 * 只能访问 root 内的条目；写入、移动、删除记为 AI 来源，删除进回收站可恢复。
 */
public final class WorkspaceAccess {

    private final WorkspaceAppService service;
    private final Long userId;
    private final String root;

    WorkspaceAccess(WorkspaceAppService service, Long userId, String root) {
        this.service = service;
        this.userId = userId;
        this.root = root;
    }

    public String root() {
        return root;
    }

    /** directory 内全部条目（递归），路径相对 root */
    public List<WorkspaceEntryDTO> list(String directory) {
        String base = absolute(WorkspacePaths.directory(directory));
        return service.listActive(workspace()).stream()
                .filter(entry -> WorkspacePaths.within(entry.getPath(), base) && !entry.getPath().equals(base))
                .map(entry -> WorkspaceAssembler.entry(entry, relative(entry.getPath())))
                .toList();
    }

    public WorkspaceFileContentDTO read(String path, int maxChars) {
        WorkspaceFileContentDTO content = service.preview(workspace(), absolute(WorkspacePaths.file(path)), maxChars);
        return new WorkspaceFileContentDTO(relative(content.path()), content.encoding(), content.content(), content.truncated(),
                content.size(), content.contentType(), content.revision());
    }

    public boolean exists(String path) {
        return service.find(workspace(), absolute(WorkspacePaths.directory(path))).isPresent();
    }

    public WorkspaceEntryDTO writeText(String path, String content) {
        String target = absolute(WorkspacePaths.file(path));
        WorkspaceFile file = service.write(workspace(), userId, target, content.getBytes(StandardCharsets.UTF_8), null, null, WorkspaceActor.AI);
        return WorkspaceAssembler.entry(file, relative(file.getPath()));
    }

    /** directory 内全部文件的相对路径（递归，不含目录） */
    public List<String> files(String directory) {
        return list(directory).stream().filter(entry -> !"DIRECTORY".equals(entry.kind())).map(WorkspaceEntryDTO::path).toList();
    }

    public byte[] readBytes(String path) {
        return service.readBytes(workspace(), absolute(WorkspacePaths.file(path)));
    }

    public WorkspaceEntryDTO writeBytes(String path, byte[] data) {
        String target = absolute(WorkspacePaths.file(path));
        WorkspaceFile file = service.write(workspace(), userId, target, data, null, null, WorkspaceActor.AI);
        return WorkspaceAssembler.entry(file, relative(file.getPath()));
    }

    public WorkspaceEntryDTO move(String from, String to) {
        WorkspaceFile moved = service.move(workspace(), absolute(WorkspacePaths.directory(from)), absolute(WorkspacePaths.directory(to)), WorkspaceActor.AI);
        return WorkspaceAssembler.entry(moved, relative(moved.getPath()));
    }

    public int delete(String path) {
        String target = absolute(WorkspacePaths.directory(path));
        if (target.equals(root)) {
            throw new BizException("不能删除工作区根目录");
        }
        return service.trash(workspace(), target);
    }

    private UserWorkspace workspace() {
        return service.workspace(userId);
    }

    private String absolute(String relative) {
        return root.isEmpty() ? relative : relative.isEmpty() ? root : root + "/" + relative;
    }

    private String relative(String absolute) {
        return root.isEmpty() ? absolute : absolute.equals(root) ? "" : absolute.substring(root.length() + 1);
    }
}
