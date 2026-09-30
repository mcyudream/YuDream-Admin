package online.yudream.base.infra.platform.mobile.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.mobile.aggregate.MobileDevice;
import online.yudream.base.domain.platform.mobile.repo.MobileDeviceRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.mobile.dataobj.MobileDeviceDO;
import online.yudream.base.infra.platform.mobile.mapper.MobileInfraMapper;
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
public class MobileDeviceRepoImpl implements MobileDeviceRepo {

    private final MongoTemplate mongo;
    private final IdGenerator ids;

    @Override
    public MobileDevice save(MobileDevice device) {
        MobileDeviceDO dataObj = MobileInfraMapper.toDataObj(device);
        if (dataObj.getId() == null) {
            dataObj.setId(ids.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        return MobileInfraMapper.toDomain(mongo.save(dataObj));
    }

    @Override
    public Optional<MobileDevice> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        MobileDeviceDO dataObj = mongo.findById(id, MobileDeviceDO.class);
        if (dataObj == null || !dataObj.active()) {
            return Optional.empty();
        }
        return Optional.of(MobileInfraMapper.toDomain(dataObj));
    }

    @Override
    public Optional<MobileDevice> findByUserIdAndPushToken(Long userId, String pushToken) {
        if (userId == null || pushToken == null || pushToken.isBlank()) {
            return Optional.empty();
        }
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("userId").is(userId),
                Criteria.where("pushToken").is(pushToken),
                activeCriteria()
        ));
        return Optional.ofNullable(mongo.findOne(query, MobileDeviceDO.class))
                .map(MobileInfraMapper::toDomain);
    }

    @Override
    public List<MobileDevice> findByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        Query query = Query.query(new Criteria().andOperator(
                        Criteria.where("userId").is(userId),
                        activeCriteria()
                ))
                .with(Sort.by(Sort.Direction.DESC, "registeredAt"));
        return mongo.find(query, MobileDeviceDO.class).stream()
                .map(MobileInfraMapper::toDomain)
                .toList();
    }

    private Criteria activeCriteria() {
        return new Criteria().orOperator(
                Criteria.where("deleted").is(false),
                Criteria.where("deleted").exists(false)
        );
    }
}
