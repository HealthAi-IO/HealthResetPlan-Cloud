package io.healthresetplan.modules.ai.oneapi;

import org.junit.jupiter.api.Test;

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
}
