package online.yudream.base.domain.platform.workspace.repo;

import online.yudream.base.domain.platform.workspace.aggregate.UserWorkspace;

import java.util.Optional;

public interface UserWorkspaceRepo {

    Optional<UserWorkspace> findByOwner(Long ownerUserId);

    /** 并发首次访问时只创建一个：按归属用户唯一 */
    UserWorkspace getOrCreate(Long ownerUserId);
}
