package blur.faces.videos.utils.camera;

import android.graphics.RectF;
import android.media.Image;
import android.util.Log;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

public class FaceDetectionAnalyzer implements ImageAnalysis.Analyzer {
    private static final String TAG = "FaceDetectionAnalyzer";

    private static final float TARGET_WIDTH_RATIO = 0.48f;
    private static final float TARGET_HEIGHT_RATIO = 1.4f;

    private final FaceDetector mDetector;
    private final GraphicOverlay mGraphic;
    private final FaceDetectionListener mListener;

    private final FaceChecker faceChecker;

    private boolean isDetected = false;
    private boolean isDebug = false;

    public FaceDetectionAnalyzer(Size imageSize, GraphicOverlay graphic, FaceDetectionListener l) {
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .build();

        mDetector = FaceDetection.getClient(options);
        mGraphic = graphic;
        mListener = l;

        RectF targetRect = new RectF(
                0,
                0,
                imageSize.getWidth() * TARGET_WIDTH_RATIO,
                imageSize.getWidth() * TARGET_WIDTH_RATIO * TARGET_HEIGHT_RATIO
        );
        targetRect.offset(
                (imageSize.getWidth()  - targetRect.width()) * 0.5f,
                (imageSize.getHeight()  - targetRect.height()) * 0.5f
        );

        mGraphic.init(imageSize, targetRect);
        faceChecker = new FaceChecker(targetRect);
    }

    @OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        if(isDetected) {
            return;
        }

        Image mediaImage = imageProxy.getImage();
        if(mediaImage != null) {
            int rotate = imageProxy.getImageInfo().getRotationDegrees();
            InputImage inputImage = InputImage.fromMediaImage(mediaImage, rotate);

            mDetector.process(inputImage)
                    .addOnSuccessListener(faces -> {
                        if (faces.size() > 0) {
                            Face face = faces.get(0);

                             faceChecker.check(face); //log the face details

                            FaceContour contour = face.getContour(FaceContour.FACE);
                            if(contour != null) {
                                mGraphic.setFaceContour(contour);
                            }

                            if(isDebug) {
                                mGraphic.setDebugText(faceChecker.getDebugText());
                            }
                        }
                    })
                    .addOnFailureListener(Throwable::printStackTrace)
                    .addOnCompleteListener(task -> imageProxy.close());
        } else {
            Log.e(TAG, "===============================================================");
            Log.e(TAG, "analyze >> mediaImage is NULL");
            Log.e(TAG, "===============================================================");
        }
    }

    public void startAnalysis(FaceChecker.Direction direction) {
        faceChecker.setDirection(direction);
        isDetected = false;
    }

    public void setDebug(boolean debug) {
        Log.d(TAG, "===============================================================");
        Log.d(TAG, "FaceDetection DEBUG >>> " + debug);
        Log.d(TAG, "===============================================================");

        isDebug = debug;
        if(mGraphic != null) {
            mGraphic.setDebug(debug);
        }
        if(faceChecker != null) {
            faceChecker.setDebug(debug);
        }
    }
}
