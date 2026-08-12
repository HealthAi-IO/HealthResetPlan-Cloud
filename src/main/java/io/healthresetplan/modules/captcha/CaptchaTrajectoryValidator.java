package io.healthresetplan.modules.captcha;

import io.healthresetplan.modules.captcha.dto.CaptchaTrajectoryPoint;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CaptchaTrajectoryValidator {

    private static final double POSITION_TOLERANCE = 8.0;

    public boolean isValid(List<CaptchaTrajectoryPoint> points, double finalX, int targetX, int maxX) {
        if (points == null || points.size() < 6 || points.size() > 300
                || !Double.isFinite(finalX) || Math.abs(finalX - targetX) > POSITION_TOLERANCE) {
            return false;
        }

        CaptchaTrajectoryPoint first = points.get(0);
        CaptchaTrajectoryPoint last = points.get(points.size() - 1);
        long duration = last.t() - first.t();
        if (duration < 180 || duration > 8_000 || Math.abs(last.x() - finalX) > 8) {
            return false;
        }

        double minY = first.y();
        double maxY = first.y();
        double minX = first.x();
        double maxSeenX = first.x();
        double speedSum = 0;
        double speedSquareSum = 0;
        int speedCount = 0;
        double previousSpeed = -1;
        boolean accelerated = false;
        boolean decelerated = false;

        for (int i = 0; i < points.size(); i++) {
            CaptchaTrajectoryPoint point = points.get(i);
            if (!Double.isFinite(point.x()) || !Double.isFinite(point.y())
                    || point.x() < -2 || point.x() > maxX + 2) {
                return false;
            }
            minX = Math.min(minX, point.x());
            maxSeenX = Math.max(maxSeenX, point.x());
            minY = Math.min(minY, point.y());
            maxY = Math.max(maxY, point.y());

            if (i == 0) {
                continue;
            }
            CaptchaTrajectoryPoint previous = points.get(i - 1);
            long deltaT = point.t() - previous.t();
            if (deltaT <= 0 || deltaT > 500) {
                return false;
            }
            double speed = Math.abs(point.x() - previous.x()) / deltaT;
            speedSum += speed;
            speedSquareSum += speed * speed;
            speedCount++;
            if (previousSpeed >= 0) {
                if (speed > previousSpeed * 1.08) {
                    accelerated = true;
                }
                if (speed < previousSpeed * 0.92) {
                    decelerated = true;
                }
            }
            previousSpeed = speed;
        }

        if (Math.abs(first.x()) > 4 || maxSeenX - minX < Math.max(30, targetX * 0.75)
                || maxY - minY > 40
                || (!accelerated && !decelerated)) {
            return false;
        }

        double meanSpeed = speedSum / speedCount;
        double variance = speedSquareSum / speedCount - meanSpeed * meanSpeed;
        double coefficientOfVariation = meanSpeed == 0 ? 0 : Math.sqrt(Math.max(variance, 0)) / meanSpeed;
        return coefficientOfVariation >= 0.04;
    }
}
