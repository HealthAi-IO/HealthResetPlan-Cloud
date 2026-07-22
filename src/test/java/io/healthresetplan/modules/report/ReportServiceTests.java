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

        assertTrue(ReportService.hasAllIndicators(complete));
        assertFalse(ReportService.hasAllIndicators(incomplete));
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
