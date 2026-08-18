package online.yudream.base.infra.platform.chat.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.chat.aggregate.ChatSession;
import online.yudream.base.domain.platform.chat.repo.ChatSessionRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.chat.dataobj.ChatSessionDO;
import online.yudream.base.infra.platform.chat.mapper.ChatInfraMapper;
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
public class ChatSessionRepoImpl implements ChatSessionRepo {

    private final MongoTemplate mongo;
    private final IdGenerator ids;

    @Override
    public ChatSession save(ChatSession session) {
        ChatSessionDO dataObj = ChatInfraMapper.session(session);
        if (dataObj.getId() == null) {
            dataObj.setId(ids.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        return ChatInfraMapper.session(mongo.save(dataObj));
    }

    @Override
    public Optional<ChatSession> findById(Long id) {
        return Optional.ofNullable(ChatInfraMapper.session(mongo.findById(id, ChatSessionDO.class)));
    }

    @Override
    public List<ChatSession> findByUserId(Long userId) {
        Query query = Query.query(Criteria.where("userId").is(userId))
                .with(Sort.by(Sort.Direction.DESC, "lastMessageAt"));
        return mongo.find(query, ChatSessionDO.class).stream().map(ChatInfraMapper::session).toList();
    }

    @Override
    public void deleteById(Long id) {
        mongo.remove(Query.query(Criteria.where("id").is(id)), ChatSessionDO.class);
    }
}
