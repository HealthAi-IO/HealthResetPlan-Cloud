package io.healthresetplan.modules.captcha;

import io.healthresetplan.modules.captcha.dto.CaptchaTrajectoryPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptchaTrajectoryValidatorTests {

    private final CaptchaTrajectoryValidator validator = new CaptchaTrajectoryValidator();

    @Test
    void acceptsHumanLikeTrajectory() {
        List<CaptchaTrajectoryPoint> points = new ArrayList<>();
        long[] times = {0, 31, 67, 108, 154, 205, 261, 322, 388, 459, 535, 616, 702};
        double[] xs = {0, 2, 7, 16, 29, 45, 64, 83, 99, 111, 119, 123, 124};
        double[] ys = {100, 100.3, 100.8, 101.4, 101.1, 100.5, 99.8, 99.4, 99.9, 100.7, 101.2, 100.6, 100.1};
        for (int i = 0; i < times.length; i++) {
            points.add(new CaptchaTrajectoryPoint(xs[i], ys[i], times[i]));
        }

        assertTrue(validator.isValid(points, 124, 123, 272));
    }

    @Test
    void rejectsPerfectLinearScriptTrajectory() {
        List<CaptchaTrajectoryPoint> points = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            points.add(new CaptchaTrajectoryPoint(i * 10, 100, i * 40L));
        }

        assertFalse(validator.isValid(points, 120, 120, 272));
    }
}
