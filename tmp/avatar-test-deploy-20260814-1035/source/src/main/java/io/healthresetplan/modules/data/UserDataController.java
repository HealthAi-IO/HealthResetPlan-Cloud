package io.healthresetplan.modules.data;

import io.healthresetplan.common.result.R;
import io.healthresetplan.modules.data.dto.UserDataResponse;
import io.healthresetplan.modules.data.dto.UserDataSaveRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/data")
public class UserDataController {

    private final UserDataService service;

    public UserDataController(UserDataService service) {
        this.service = service;
    }

    @GetMapping
    public R<UserDataResponse> load() {
        return R.ok(service.load(currentUserId()));
    }

    @PutMapping
    public R<UserDataResponse> save(@Valid @RequestBody UserDataSaveRequest request) {
        return R.ok(service.save(currentUserId(), request));
    }

    @DeleteMapping
    public R<Void> clear() {
        service.delete(currentUserId());
        return R.ok();
    }

    private String currentUserId() {
        return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
