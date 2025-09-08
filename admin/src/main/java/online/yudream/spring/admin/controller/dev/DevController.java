package online.yudream.spring.admin.controller.dev;

import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.annotation.Resource;
import online.yudream.spring.admin.service.CodegenService;
import online.yudream.spring.base.common.R;
import online.yudream.spring.entity.dto.GencodeDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SaCheckRole({"admin","super"})
@RequestMapping("/dev")
public class DevController {
    @Resource
    private CodegenService codegenService;

    @Value("${spring.profiles.active:profile}")
    private String active;


    @PostMapping("/gencode")
    public R<String> gencode(@RequestBody GencodeDto gencodeDto){
        if (active.equals("dev")) {
            codegenService.gencode(gencodeDto);
            return R.success("success");
        }
        return R.fail("非开发模式禁止使用本接口");
    }
}
