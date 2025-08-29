package online.yudream.spring.admin.controller.test;

import online.yudream.spring.base.common.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestController {

    @GetMapping
    public R<String> test() {
        return R.success("test","test");
    }
}
