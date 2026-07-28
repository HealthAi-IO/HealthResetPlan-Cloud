package io.healthresetplan.modules.captcha;

import nu.pattern.OpenCV;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
public class CaptchaImageGenerator {

    static final int WIDTH = 320;
    static final int HEIGHT = 160;
    static final int PIECE_SIZE = 48;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile boolean loaded;

    public GeneratedCaptcha generate() {
        ensureOpenCvLoaded();

        Mat background = createBackground();
        Mat mask = createPieceMask();
        int targetX = PIECE_SIZE + RANDOM.nextInt(WIDTH - PIECE_SIZE * 2 - 16);
        int targetY = 18 + RANDOM.nextInt(HEIGHT - PIECE_SIZE - 36);
        Mat pieceCanvas = Mat.zeros(HEIGHT, PIECE_SIZE, CvType.CV_8UC4);

        try {
            Rect sourceRect = new Rect(targetX, targetY, PIECE_SIZE, PIECE_SIZE);
            Mat source = background.submat(sourceRect);
            Mat sourceBgra = new Mat();
            Mat pieceRoi = pieceCanvas.submat(new Rect(0, targetY, PIECE_SIZE, PIECE_SIZE));
            Imgproc.cvtColor(source, sourceBgra, Imgproc.COLOR_BGR2BGRA);
            sourceBgra.copyTo(pieceRoi, mask);

            Mat darkened = new Mat();
            source.convertTo(darkened, -1, 0.48, -8);
            darkened.copyTo(source, mask);
            drawMaskOutline(source, mask);

            String backgroundBase64 = encodePng(background);
            String pieceBase64 = encodePng(pieceCanvas);

            source.release();
            sourceBgra.release();
            pieceRoi.release();
            darkened.release();
            return new GeneratedCaptcha(targetX, backgroundBase64, pieceBase64);
        } finally {
            background.release();
            mask.release();
            pieceCanvas.release();
        }
    }

    private Mat createBackground() {
        Mat image = new Mat(HEIGHT, WIDTH, CvType.CV_8UC3);
        Scalar start = randomColor(55, 165);
        Scalar end = randomColor(105, 220);
        for (int y = 0; y < HEIGHT; y++) {
            double ratio = (double) y / (HEIGHT - 1);
            Scalar rowColor = new Scalar(
                    start.val[0] * (1 - ratio) + end.val[0] * ratio,
                    start.val[1] * (1 - ratio) + end.val[1] * ratio,
                    start.val[2] * (1 - ratio) + end.val[2] * ratio);
            Imgproc.line(image, new Point(0, y), new Point(WIDTH, y), rowColor, 1);
        }

        for (int i = 0; i < 22; i++) {
            Point center = new Point(RANDOM.nextInt(WIDTH), RANDOM.nextInt(HEIGHT));
            int radius = 5 + RANDOM.nextInt(28);
            Imgproc.circle(image, center, radius, randomColor(70, 235), -1, Imgproc.LINE_AA);
        }
        Imgproc.GaussianBlur(image, image, new org.opencv.core.Size(7, 7), 0);

        Mat noise = new Mat(image.size(), image.type());
        Core.randn(noise, 0, 7);
        Core.add(image, noise, image);
        noise.release();
        return image;
    }

    private Mat createPieceMask() {
        Mat mask = Mat.zeros(PIECE_SIZE, PIECE_SIZE, CvType.CV_8UC1);
        Imgproc.rectangle(mask, new Point(7, 7), new Point(41, 41), new Scalar(255), -1);
        Imgproc.circle(mask, new Point(24, 7), 8, new Scalar(255), -1, Imgproc.LINE_AA);
        Imgproc.circle(mask, new Point(41, 25), 8, new Scalar(255), -1, Imgproc.LINE_AA);
        Imgproc.circle(mask, new Point(7, 25), 7, new Scalar(0), -1, Imgproc.LINE_AA);
        Imgproc.circle(mask, new Point(25, 41), 7, new Scalar(0), -1, Imgproc.LINE_AA);
        return mask;
    }

    private void drawMaskOutline(Mat target, Mat mask) {
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Mat copy = mask.clone();
        Imgproc.findContours(copy, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        Imgproc.drawContours(target, contours, -1, new Scalar(245, 245, 245), 1, Imgproc.LINE_AA);
        contours.forEach(Mat::release);
        hierarchy.release();
        copy.release();
    }

    private String encodePng(Mat image) {
        MatOfByte buffer = new MatOfByte();
        try {
            if (!Imgcodecs.imencode(".png", image, buffer)) {
                throw new IllegalStateException("验证码图片编码失败");
            }
            return Base64.getEncoder().encodeToString(buffer.toArray());
        } finally {
            buffer.release();
        }
    }

    private Scalar randomColor(int min, int max) {
        int range = max - min + 1;
        return new Scalar(
                min + RANDOM.nextInt(range),
                min + RANDOM.nextInt(range),
                min + RANDOM.nextInt(range));
    }

    private static void ensureOpenCvLoaded() {
        if (loaded) {
            return;
        }
        synchronized (CaptchaImageGenerator.class) {
            if (!loaded) {
                OpenCV.loadLocally();
                loaded = true;
            }
        }
    }

    public record GeneratedCaptcha(
            int targetX,
            String backgroundImageBase64,
            String pieceImageBase64) {
    }
}
