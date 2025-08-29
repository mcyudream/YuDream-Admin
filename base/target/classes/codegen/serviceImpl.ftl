package ${packageServiceImpl};

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import ${packageRepository}.${className}${repoSuffix};
import ${packageService}.${className}Service;

@Service
@RequiredArgsConstructor
public class ${className}ServiceImpl implements ${className}Service {

    private final ${className}${repoSuffix} ${namePrefix}Mapper;

}
