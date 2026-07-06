package online.yudream.base.infra.system.dashboard.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.domain.system.dashboard.aggregate.DashboardLayout;
import online.yudream.base.domain.system.dashboard.enumerate.DashboardLayoutOwnerType;
import online.yudream.base.domain.system.dashboard.repo.DashboardLayoutRepo;
import online.yudream.base.infra.system.dashboard.dataobj.DashboardLayoutDO;
import online.yudream.base.infra.system.dashboard.mapper.DashboardLayoutInfraMapper;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DashboardLayoutRepoImpl implements DashboardLayoutRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public DashboardLayout save(DashboardLayout layout) {
        DashboardLayoutDO dataObj = DashboardLayoutInfraMapper.toDataObj(layout);
        if (dataObj.getId() == null) {
            dataObj.setId(idGenerator.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        return DashboardLayoutInfraMapper.toDomain(mongoTemplate.save(dataObj));
    }

    @Override
    public Optional<DashboardLayout> findByOwner(DashboardLayoutOwnerType ownerType, Long ownerId) {
        Query query = Query.query(Criteria.where("ownerType").is(ownerType));
        if (ownerId == null) {
            query.addCriteria(Criteria.where("ownerId").is(null));
        } else {
            query.addCriteria(Criteria.where("ownerId").is(ownerId));
        }
        return Optional.ofNullable(DashboardLayoutInfraMapper.toDomain(mongoTemplate.findOne(query, DashboardLayoutDO.class)));
    }

    @Override
    public void deleteByOwner(DashboardLayoutOwnerType ownerType, Long ownerId) {
        Query query = Query.query(Criteria.where("ownerType").is(ownerType));
        if (ownerId == null) {
            query.addCriteria(Criteria.where("ownerId").is(null));
        } else {
            query.addCriteria(Criteria.where("ownerId").is(ownerId));
        }
        mongoTemplate.remove(query, DashboardLayoutDO.class);
    }
}
