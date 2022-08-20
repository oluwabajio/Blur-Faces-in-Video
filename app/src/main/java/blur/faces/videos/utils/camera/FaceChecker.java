package blur.faces.videos.utils.camera;

import android.graphics.PointF;
import android.graphics.RectF;

import androidx.annotation.NonNull;

import com.google.android.material.math.MathUtils;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;

import java.util.List;
import java.util.Locale;

public class FaceChecker {

    public enum Direction {
        FRONT(0),
        LEFT_30(-30),
        LEFT_45(-45),
        RIGHT_30(30),
        RIGHT_45(45),
        ;

        final float angle;

        Direction(float angle) {
            this.angle = angle;
        }

        public boolean check(float angleY, float errorValue) {
            return (angle - errorValue) < angleY && angleY < (angle + errorValue);
        }
    }

    private static final float RATIO_ERROR_VALUE    = 0.2f;         // 가로비율 오차값
    private static final float ANGLE_ERROR_VALUE    = 7f;           // 각도 오차값(xyz 공통)
    private static final float POSITION_ERROR_VALUE = 35f;          // 위치 오차값
    private static final float EYE_OPEN_ERROR_VALUE = 0.65f;        // 눈열림 오차값

    private final RectF mTargetRect;

    private Direction mDirection;

    private boolean isDebug = false;
    private String debugText;

    public FaceChecker(@NonNull RectF targetRect) {
        mTargetRect = targetRect;
    }

    public void setDirection(Direction dir) {
        mDirection = dir;
    }

    public Direction getDirection() {
        return mDirection;
    }

    // 자동촬영 판정
    public boolean check(Face face) {
        FaceContour contour = face.getContour(FaceContour.FACE);

        if(contour == null) {
            return false;
        }

        RectF faceRect = getFaceRect(contour.getPoints());

        // 정면카메라의 경우 좌우반전
        float leftEyeOpen = face.getRightEyeOpenProbability() == null ? 0 : face.getRightEyeOpenProbability();
        float rightEyeOpen = face.getLeftEyeOpenProbability() == null ? 0 : face.getLeftEyeOpenProbability();

        if(isDebug) {
            debugText = "current dir: " + mDirection.name() + "\n";

            // debugText 출력을 위해 각각 따로 호출
            boolean checkAngle = checkAngle(face.getHeadEulerAngleX(), face.getHeadEulerAngleY(), face.getHeadEulerAngleZ());
            boolean checkPosition = checkPosition(faceRect);
            boolean checkWidthRatio = checkWidthRatio(faceRect.width());
            boolean checkEyesOpen = checkEyesOpen(leftEyeOpen, rightEyeOpen);

            return checkAngle && checkPosition && checkWidthRatio && checkEyesOpen;
        }

        return checkAngle(face.getHeadEulerAngleX(), face.getHeadEulerAngleY(), face.getHeadEulerAngleZ())
                && checkPosition(faceRect)
                && checkWidthRatio(faceRect.width())
                && checkEyesOpen(leftEyeOpen, rightEyeOpen)
                ;
    }

    // 얼굴 각도판정
    private boolean checkAngle(float x, float y, float z) {
        if(mDirection == null) {
            return false;
        }

        boolean checkX = (-ANGLE_ERROR_VALUE < x) && (x < ANGLE_ERROR_VALUE);
        boolean checkZ = (-ANGLE_ERROR_VALUE < z) && (z < ANGLE_ERROR_VALUE);
        boolean checkY = mDirection.check(y, ANGLE_ERROR_VALUE);

        if(isDebug) {
            debugText += String.format(Locale.getDefault(), "Angle[%f, %f, %f]\n", x, y, z);
        }

        return checkX && checkZ && checkY;
    }

    // 위치판정
    private boolean checkPosition(RectF face) {
        float distance = MathUtils.dist(face.centerX(), face.centerY(), mTargetRect.centerX(), mTargetRect.centerY());
        if(isDebug) {
            debugText += String.format(Locale.getDefault(), "distance: %f\n", distance);
            debugText += String.format(Locale.getDefault(), "target center[%f, %f]\nface center [%f, %f]\n", mTargetRect.centerX(), mTargetRect.centerY(), face.centerX(), face.centerY());
        }
        return distance < POSITION_ERROR_VALUE;
    }

    // 가로길이 비율 판정
    private boolean checkWidthRatio(float faceWidth) {
        float ratio = faceWidth / mTargetRect.width();
        if(isDebug) {
            debugText += String.format(Locale.getDefault(), "face width: %f\ntarget width: %f\nface ratio:%f\n", faceWidth, mTargetRect.width(), ratio);
        }
        return 1 - RATIO_ERROR_VALUE < ratio && ratio < 1 + RATIO_ERROR_VALUE;
    }

    // 눈 열림 판정
    private boolean checkEyesOpen(float left, float right) {
        if(isDebug) {
            debugText += String.format(Locale.getDefault(), "eyes open[%f, %f]\n", left, right);
        }
        return left > EYE_OPEN_ERROR_VALUE && right > EYE_OPEN_ERROR_VALUE;
    }

    // 얼굴 Landmark -> Rect 변환
    private RectF getFaceRect(List<PointF> landmarks) {
        return new RectF(
                (float)landmarks.stream().mapToDouble(x -> x.x).min().orElse(0),
                (float)landmarks.stream().mapToDouble(x -> x.y).min().orElse(0),
                (float)landmarks.stream().mapToDouble(x -> x.x).max().orElse(0),
                (float)landmarks.stream().mapToDouble(x -> x.y).max().orElse(0)
        );
    }

    public void setDebug(boolean debug) {
        isDebug = debug;
    }

    public String getDebugText() {
        return debugText;
    }
}
