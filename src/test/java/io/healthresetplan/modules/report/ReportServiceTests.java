package io.healthresetplan.modules.report;

import io.healthresetplan.modules.report.dto.AnalyzeResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportServiceTests {

    @Test
    void acceptsOnlyResultsContainingEverySourceRow() {
        AnalyzeResponse complete = responseWithCounts(17, 17);
        AnalyzeResponse incomplete = responseWithCounts(17, 16);
        AnalyzeResponse withAdditionalConclusion = responseWithCounts(19, 20);

        assertTrue(ReportService.hasAllIndicators(complete));
        assertFalse(ReportService.hasAllIndicators(incomplete));
        assertTrue(ReportService.hasAllIndicators(withAdditionalConclusion));
    }

    @Test
    void acceptsNarrativeReportWithVisibleText() {
        AnalyzeResponse narrative = responseWithCounts(0, 0);
        narrative.setRawText("精神心理科处方及诊断说明");

        assertTrue(ReportService.hasAllIndicators(narrative));
        assertFalse(ReportService.hasAllIndicators(responseWithCounts(0, 0)));
    }

    private AnalyzeResponse responseWithCounts(int sourceRows, int indicators) {
        AnalyzeResponse response = new AnalyzeResponse();
        response.setSourceRowCount(sourceRows);
        List<AnalyzeResponse.Indicator> items = new ArrayList<>();
        for (int i = 0; i < indicators; i++) {
            items.add(new AnalyzeResponse.Indicator());
        }
        response.setIndicators(items);
        return response;
    }
}
