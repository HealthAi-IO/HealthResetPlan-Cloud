package io.healthresetplan.modules.user;

import io.healthresetplan.common.result.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @GetMapping("/me")
    public R<Map<String, Object>> me() {
        // TODO: 从 SecurityContext 取当前用户
        return R.ok(Map.of(
                "userId", "demo-user",
                "nickname", "健康用户",
                "hasCloudSync", false
        ));
    }
}
