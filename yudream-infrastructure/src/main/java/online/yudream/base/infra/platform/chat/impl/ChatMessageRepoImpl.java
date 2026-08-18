package online.yudream.base.infra.platform.chat.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.chat.aggregate.ChatMessage;
import online.yudream.base.domain.platform.chat.repo.ChatMessageRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.chat.dataobj.ChatMessageDO;
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
public class ChatMessageRepoImpl implements ChatMessageRepo {

    private final MongoTemplate mongo;
    private final IdGenerator ids;

    @Override
    public ChatMessage save(ChatMessage message) {
        ChatMessageDO dataObj = ChatInfraMapper.message(message);
        if (dataObj.getId() == null) {
            dataObj.setId(ids.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        return ChatInfraMapper.message(mongo.save(dataObj));
    }

    @Override
    public Optional<ChatMessage> findById(Long id) {
        return Optional.ofNullable(ChatInfraMapper.message(mongo.findById(id, ChatMessageDO.class)));
    }

    @Override
    public List<ChatMessage> findBySessionId(Long sessionId) {
        Query query = Query.query(Criteria.where("sessionId").is(sessionId))
                .with(Sort.by(Sort.Direction.ASC, "createTime"));
        return mongo.find(query, ChatMessageDO.class).stream().map(ChatInfraMapper::message).toList();
    }

    @Override
    public void deleteBySessionId(Long sessionId) {
        mongo.remove(Query.query(Criteria.where("sessionId").is(sessionId)), ChatMessageDO.class);
    }
}
