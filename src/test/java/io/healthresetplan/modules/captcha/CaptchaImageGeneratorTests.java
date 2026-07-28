package io.healthresetplan.modules.captcha;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptchaImageGeneratorTests {

    @Test
    void generatesPngImagesWithoutExposingCoordinates() {
        CaptchaImageGenerator.GeneratedCaptcha generated = new CaptchaImageGenerator().generate();

        assertTrue(generated.targetX() >= CaptchaImageGenerator.PIECE_SIZE);
        assertTrue(generated.targetX()
                <= CaptchaImageGenerator.WIDTH - CaptchaImageGenerator.PIECE_SIZE);
        assertEquals((byte) 0x89, Base64.getDecoder()
                .decode(generated.backgroundImageBase64())[0]);
        assertEquals((byte) 0x89, Base64.getDecoder()
                .decode(generated.pieceImageBase64())[0]);
    }
}
