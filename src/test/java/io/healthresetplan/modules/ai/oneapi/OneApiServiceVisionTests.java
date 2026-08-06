package io.healthresetplan.modules.ai.oneapi;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OneApiServiceVisionTests {

    @Test
    void emptyVisionContentIsNotUsable() {
        assertFalse(OneApiService.hasUsableVisionContent(" \n"));
        assertTrue(OneApiService.hasUsableVisionContent("{\"indicators\":[]}"));
    }

    @Test
    void visionCompletionUsesActualProviderAndModel() {
        OneApiService.VisionCompletion completion =
                new OneApiService.VisionCompletion("doubao", "ep-test", "result");

        assertEquals("doubao / ep-test", completion.label());
    }

    @Test
    void providerSelectionKeepsPreferredModelFirstAndFallsBack() {
        OneApiProperties properties = new OneApiProperties();
        properties.setChatOrder(List.of("doubao", "qwen", "glm", "deepseek"));
        OneApiService service = new OneApiService(properties, null);

        assertEquals(
                List.of("glm", "doubao", "qwen", "deepseek"),
                service.providerSelection("glm"));
        assertEquals(
                List.of("doubao", "qwen", "glm", "deepseek"),
                service.providerSelection(null));
    }

    @Test
    void structuredOutputDisablesThinkingForVolcengineProviders() {
        assertTrue(OneApiService.isVolcengineProvider("doubao"));
        assertTrue(OneApiService.isVolcengineProvider("glm"));
        assertTrue(OneApiService.isVolcengineProvider("deepseek"));
        assertFalse(OneApiService.isVolcengineProvider("qwen"));
    }
}
