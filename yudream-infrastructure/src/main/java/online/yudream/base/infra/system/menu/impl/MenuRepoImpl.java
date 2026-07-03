package online.yudream.base.infra.system.menu.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.system.menu.aggregate.Menu;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.repo.MenuRepo;
import online.yudream.base.infra.system.menu.dataobj.MenuDO;
import online.yudream.base.infra.system.menu.mapper.MenuInfraMapper;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 菜单仓库实现。
 */
@Service
@RequiredArgsConstructor
public class MenuRepoImpl implements MenuRepo {

    private final MongoTemplate mongoTemplate;

    @Override
    public Menu save(Menu menu) {
        MenuDO menuDO = MenuInfraMapper.toDataObj(menu);
        MenuDO existing = mongoTemplate.findOne(
                Query.query(Criteria.where("code").is(menu.getCode())),
                MenuDO.class
        );
        if (existing != null) {
            menuDO.setId(existing.getId());
            menuDO.setVersion(existing.getVersion());
            menuDO.setCreateTime(existing.getCreateTime());
        }
        MenuDO saved = mongoTemplate.save(menuDO);
        return MenuInfraMapper.toDomain(saved);
    }

    @Override
    public Optional<Menu> findByCode(String code) {
        Query query = Query.query(Criteria.where("code").is(code));
        MenuDO menuDO = mongoTemplate.findOne(query, MenuDO.class);
        return Optional.ofNullable(MenuInfraMapper.toDomain(menuDO));
    }

    @Override
    public List<Menu> findAll() {
        List<MenuDO> list = mongoTemplate.findAll(MenuDO.class);
        return list.stream()
                .map(MenuInfraMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Menu> findByTypeIn(List<MenuNodeType> types) {
        Query query = Query.query(Criteria.where("type").in(types));
        List<MenuDO> list = mongoTemplate.find(query, MenuDO.class);
        return list.stream()
                .map(MenuInfraMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsByCode(String code) {
        Query query = Query.query(Criteria.where("code").is(code));
        return mongoTemplate.exists(query, MenuDO.class);
    }
}
