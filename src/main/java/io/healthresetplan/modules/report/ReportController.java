package io.healthresetplan.modules.report;

import io.healthresetplan.common.result.R;
import io.healthresetplan.modules.report.dto.AnalyzeResponse;
import io.healthresetplan.modules.report.dto.ReportSaveRequest;
import io.healthresetplan.modules.report.entity.HealthReport;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * 上传图片，视觉大模型一步完成 OCR + 结构化解析。
     * 结果为明文，返回给客户端供用户确认，不入库。
     */
    @PostMapping("/analyze")
    public R<AnalyzeResponse> analyze(@RequestParam("file") MultipartFile file) {
        return R.ok(reportService.analyze(file));
    }

    /**
     * 保存用户确认后的报告（客户端已在本地加密）。
     * 幂等：相同 clientId 重复提交只更新，不重复插入。
     */
    @PostMapping
    public R<Void> save(@Valid @RequestBody ReportSaveRequest req) {
        String userId = currentUserId();
        reportService.save(req, userId);
        return R.ok();
    }

    /** 分页查询报告列表（仅元数据，加密内容由客户端本地解密）。 */
    @GetMapping
    public R<List<HealthReport>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = currentUserId();
        return R.ok(reportService.list(userId, page, size));
    }

    /** 软删除报告。 */
    @DeleteMapping("/{clientId}")
    public R<Void> delete(@PathVariable String clientId) {
        String userId = currentUserId();
        reportService.delete(clientId, userId);
        return R.ok();
    }

    private String currentUserId() {
        return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
