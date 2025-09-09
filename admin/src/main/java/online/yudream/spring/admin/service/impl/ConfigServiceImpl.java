package online.yudream.spring.admin.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import online.yudream.spring.entity.mapper.ConfigMapper;
import online.yudream.spring.admin.service.ConfigService;

@Service
@RequiredArgsConstructor
public class ConfigServiceImpl implements ConfigService {

    private final ConfigMapper configMapper;

}
