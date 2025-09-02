package online.yudream.spring.admin.service.impl;

import io.micrometer.common.util.StringUtils;
import jakarta.annotation.Resource;
import online.yudream.spring.admin.service.RoleService;
import online.yudream.spring.base.common.SearchPageDto;
import online.yudream.spring.base.exception.BaseException;
import online.yudream.spring.base.exception.NotFoundException;
import online.yudream.spring.base.utils.SearchUtils;
import online.yudream.spring.entity.entity.Role;
import online.yudream.spring.entity.mapper.RoleMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

@Service
public class RoleServiceImpl implements RoleService {
    @Resource
    private RoleMapper roleMapper;
    @Resource
    private SearchUtils searchUtils;

    @Override
    public Page<Role> getAllRoles(SearchPageDto searchPageDto) {
        Criteria criteria = searchUtils.searchCriteria(searchPageDto);
        return searchUtils.findPage(Role.class, searchPageDto, criteria);
    }


    @Override
    public Role createRole(Role role) {
        Role rawRole = roleMapper.findById(role.getId()).orElse(null);
        if (rawRole == null) {
            return roleMapper.save(role);
        }
        if(role.getLevel()>0){
            rawRole.setLevel(role.getLevel());
        } else {
            throw new BaseException("exception.role.level");
        }
        throw new BaseException("已存在的角色");
    }

    @Override
    public void editRole(Role role) {
        Role rawRole = roleMapper.findById(role.getId()).orElse(null);
        if (rawRole == null) {
            throw new NotFoundException();
        }

        if (StringUtils.isNotBlank(role.getName())) {
            rawRole.setName(role.getName());
        }
        if (StringUtils.isNotBlank(role.getDescription())) {
            rawRole.setDescription(role.getDescription());
        }
        if (rawRole.getLevel()==0) {
            throw new BaseException("exception.role.superlevel");
        }
        if( role.getLevel()>0){
            rawRole.setLevel(role.getLevel());
        } else {
            throw new BaseException("exception.role.level");
        }
        roleMapper.save(rawRole);
    }


    @Override
    public void deleteRole(String id) {
        roleMapper.deleteById(id);
    }
}
