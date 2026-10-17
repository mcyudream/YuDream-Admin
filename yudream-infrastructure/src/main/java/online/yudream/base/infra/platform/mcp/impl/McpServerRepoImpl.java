package online.yudream.base.infra.platform.mcp.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.mcp.aggregate.McpServer;
import online.yudream.base.domain.platform.mcp.repo.McpServerRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.mcp.dataobj.McpServerDO;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class McpServerRepoImpl implements McpServerRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public List<McpServer> findAll() {
        Query query = new Query().with(Sort.by(Sort.Direction.ASC, "name"));
        return mongoTemplate.find(query, McpServerDO.class).stream().map(McpInfraMapper::toDomain).toList();
    }

    @Override
    public Optional<McpServer> findById(Long id) {
        return Optional.ofNullable(McpInfraMapper.toDomain(mongoTemplate.findById(id, McpServerDO.class)));
    }

    @Override
    public Optional<McpServer> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(McpInfraMapper.toDomain(
                mongoTemplate.findOne(Query.query(Criteria.where("code").is(code.trim().toLowerCase())), McpServerDO.class)));
    }

    @Override
    public McpServer save(McpServer server) {
        McpServerDO data = McpInfraMapper.toDataObj(server);
        if (data.getId() == null) {
            data.setId(idGenerator.nextId());
            data.setCreateTime(LocalDateTime.now());
        }
        data.setUpdateTime(LocalDateTime.now());
        return McpInfraMapper.toDomain(mongoTemplate.save(data));
    }

    @Override
    public void deleteById(Long id) {
        mongoTemplate.remove(Query.query(Criteria.where("id").is(id)), McpServerDO.class);
    }
}
