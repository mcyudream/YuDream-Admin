package online.yudream.base.domain.platform.mcp.repo;

import online.yudream.base.domain.platform.mcp.aggregate.McpServer;

import java.util.List;
import java.util.Optional;

public interface McpServerRepo {

    List<McpServer> findAll();

    Optional<McpServer> findById(Long id);

    Optional<McpServer> findByCode(String code);

    McpServer save(McpServer server);

    void deleteById(Long id);
}
