package blur.faces.videos;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_MPEG4;
import static org.bytedeco.opencv.global.opencv_core.CV_8UC1;
import static org.bytedeco.opencv.global.opencv_imgproc.CV_BGR2GRAY;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;

import android.content.Context;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.os.Environment;
import android.os.PowerManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameRecorder;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatExpr;
import org.bytedeco.opencv.opencv_core.Range;
import org.bytedeco.opencv.opencv_core.RectVector;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.Objdetect;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import blur.faces.videos.databinding.FragmentCameraSBinding;
import blur.faces.videos.utils.CvCameraPreview;


public class CameraSFragment extends Fragment implements View.OnClickListener, CvCameraPreview.CvCameraViewListener {

    FragmentCameraSBinding binding;
    private final static String CLASS_LABEL = "RecordActivity";
    private final static String LOG_TAG = CLASS_LABEL;
    private PowerManager.WakeLock wakeLock;
    private boolean recording;
    private CvCameraPreview cameraView;
    private File savePath = new File(Environment.getExternalStorageDirectory(), "stream.mp4");
    private FFmpegFrameRecorder recorder;
    private long startTime = 0;
    private static final String TAG = "CameraSFragment";
    private OpenCVFrameConverter.ToMat converterToMat = new OpenCVFrameConverter.ToMat();
    private final Object semaphore = new Object();
    private int absoluteFaceSize = 0;
    private CascadeClassifier cascadeClassifier;
    private org.bytedeco.opencv.opencv_objdetect.CascadeClassifier cascadeClassifier2;
    private FaceDetector mDetector;
    OpenCVFrameConverter converter = new OpenCVFrameConverter() {
        @Override
        public Frame convert(Object o) {
            return null;
        }

        @Override
        public Object convert(Frame frame) {
            return null;
        }
    };


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {


        binding = FragmentCameraSBinding.inflate(inflater, container, false);


//    setContentView(R.layout.activity_record);

        cameraView = binding.cameraView;

        PowerManager pm = (PowerManager) getActivity().getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, CLASS_LABEL);
        wakeLock.acquire();

        initLayout();
        return binding.getRoot();
    }


    @Override
    public void onResume() {
        super.onResume();

        if (wakeLock == null) {
            PowerManager pm = (PowerManager) getActivity().getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, CLASS_LABEL);
            wakeLock.acquire();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
        }

        if (recorder != null) {
            try {
                recorder.release();
            } catch (FrameRecorder.Exception e) {
                e.printStackTrace();
            }
        }
    }


    private void initLayout() {
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .build();
        mDetector = FaceDetection.getClient(options);

        cascadeClassifier = setupCascadeClassifier();
        cascadeClassifier2 = setupCascadeClassifier2();

        cameraView.setCvCameraViewListener(this);
        binding.btnStart.setOnClickListener(this);
    }

    private void initRecorder(int width, int height) {
        int degree = getRotationDegree();
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraView.getCameraId(), info);
        boolean isFrontFaceCamera = info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT;
        Log.e(LOG_TAG, "init recorder with width = " + width + " and height = " + height + " and degree = "
                + degree + " and isFrontFaceCamera = " + isFrontFaceCamera);
        int frameWidth, frameHeight;
        /*
         0 = 90CounterCLockwise and Vertical Flip (default)
         1 = 90Clockwise
         2 = 90CounterClockwise
         3 = 90Clockwise and Vertical Flip
         */
        switch (degree) {
            case 0:
                frameWidth = width;
                frameHeight = height;
                break;
            case 90:
                frameWidth = height;
                frameHeight = width;
                break;
            case 180:
                frameWidth = width;
                frameHeight = height;
                break;
            case 270:
                frameWidth = height;
                frameHeight = width;
                break;
            default:
                frameWidth = width;
                frameHeight = height;
        }

        Log.e(LOG_TAG, "saved file path: " + savePath.getAbsolutePath());
        recorder = new FFmpegFrameRecorder(savePath, frameWidth, frameHeight, 0);
        recorder.setFormat("mp4");
        recorder.setVideoCodec(AV_CODEC_ID_MPEG4);
        recorder.setVideoQuality(1);
        // Set in the surface changed method
        recorder.setFrameRate(16);

        Log.e(LOG_TAG, "recorder initialize success");
    }

    private int getRotationDegree() {
        int result;

        int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        if (Build.VERSION.SDK_INT >= 9) {
            // on >= API 9 we can proceed with the CameraInfo method
            // and also we have to keep in mind that the camera could be the front one
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(cameraView.getCameraId(), info);

            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                result = (info.orientation + degrees) % 360;
                result = (360 - result) % 360;  // compensate the mirror
            } else {
                // back-facing
                result = (info.orientation - degrees + 360) % 360;
            }
        } else {
            // TODO: on the majority of API 8 devices, this trick works good
            // and doesn't produce an upside-down preview.
            // ... but there is a small amount of devices that don't like it!
            result = Math.abs(degrees - 90);
        }
        return result;
    }

    public void startRecording() {
        try {
            synchronized (semaphore) {
                recorder.start();
            }
            startTime = System.currentTimeMillis();
            recording = true;
        } catch (FFmpegFrameRecorder.Exception e) {
            e.printStackTrace();
        }
    }

    public void stopRecording() {
        if (recorder != null && recording) {
            recording = false;
            Log.e(LOG_TAG, "Finishing recording, calling stop and release on recorder");
            try {
                synchronized (semaphore) {
                    recorder.stop();
                    recorder.release();
                }
            } catch (FFmpegFrameRecorder.Exception e) {
                e.printStackTrace();
            }
            recorder = null;
        }
    }

    @Override
    public void onClick(View v) {
        if (!recording) {
            startRecording();
            recording = true;
            Log.e(LOG_TAG, "Start Button Pushed");
            binding.btnStart.setText("Stop");
            binding.btnStart.setBackgroundResource(android.R.drawable.ic_dialog_info);
        } else {
            // This will trigger the audio recording loop to stop and then set isRecorderStart = false;
            stopRecording();
            recording = false;
            Log.e(LOG_TAG, "Stop Button Pushed");
//            btnRecorderControl.setText("Start");
            binding.btnStart.setVisibility(View.GONE);
            Toast.makeText(getActivity(), "Video file was saved to \"" + savePath + "\"", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        initRecorder(width, height);
    }

    @Override
    public void onCameraViewStopped() {
        stopRecording();
    }

    @Override
    public Mat onCameraFrame(Mat mat) {
        if (recording && mat != null) {
            synchronized (semaphore) {
                try {




//                    Frame newFrame = detectFace3(mat);
                    Frame newFrame = detectFace(converter.convert(mat));
                    long t = 1000 * (System.currentTimeMillis() - startTime);
                    if (t > recorder.getTimestamp()) {
                        recorder.setTimestamp(t);
                    }
                    recorder.record(newFrame);
//                    recorder.record(frame);
//    detectFrame2();


                } catch (Exception e) {
                    Log.e(LOG_TAG, e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        return mat;
    }

    private void detectFrame2() {

//                    OpenCVFrameConverter converter = new OpenCVFrameConverter() {
//                        @Override
//                        public Frame convert(Object o) {
//                            return null;
//                        }
//
//                        @Override
//                        public Object convert(Frame frame) {
//                            return null;
//                        }
//                    };
//                    AndroidFrameConverter androidFrameConverter = new AndroidFrameConverter();
//
//                    InputImage inputImage = InputImage.fromBitmap(androidFrameConverter.convert(frame), 0);
//
//                    mDetector.process(inputImage)
//                            .addOnSuccessListener(faces -> {
//                                if (faces.size() > 0) {
//                                    Face face = faces.get(0);
//                                    Rect[] facesArray = new Rect[faces.size()];
//
//                                    for (int i = 0; i < faces.size(); i++) {
//                                        android.graphics.Rect re = faces.get(i).getBoundingBox();
//                                        facesArray[i] = new org.opencv.core.Rect(re.left, re.top, re.width(), re.height());
//                                    }
//
//                                    org.opencv.core.Mat srcMat = converter.convertToOrgOpenCvCoreMat(frame);
//
//                                    Log.e(TAG, "processOpencv: Detected faces = " + facesArray.length);
//                                    for (int i = 0; i < facesArray.length; i++) {
//
////                                        org.opencv.core.Mat imageROI = new org.opencv.core.Mat(greyscaledMat, facesArray[i]);
//                                        org.opencv.core.Mat mask = srcMat.submat(facesArray[i]);
//                                        Imgproc.GaussianBlur(mask, mask, new Size(55, 55), 55); // or any other processing
//
//                                    }
//
//
//                                    Frame newFrame = converter.convert(srcMat);
//
//                                    long t = 1000 * (System.currentTimeMillis() - startTime);
//                                    if (t > recorder.getTimestamp()) {
//                                        recorder.setTimestamp(t);
//                                    }
//                                    try {
//                                        if (t > recorder.getTimestamp()) {
//                                            recorder.setTimestamp(t);
//                                        }
////                                        recorder.record(newFrame);
//                                        recorder.record(newFrame);
//                                    } catch (FrameRecorder.Exception e) {
//                                        e.printStackTrace();
//                                    }
//
//
//                                }
//                            })
//                            .addOnFailureListener(Throwable::printStackTrace)
//                            .addOnCompleteListener(task -> {});
    }


    private Frame detectFace(Frame frame) {
        OpenCVFrameConverter converter = new OpenCVFrameConverter() {
            @Override
            public Frame convert(Object o) {
                return null;
            }

            @Override
            public Object convert(Frame frame) {
                return null;
            }
        };



        org.opencv.core.Mat srcMat = converter.convertToOrgOpenCvCoreMat(frame);
        org.opencv.core.Mat greyscaledMat = new org.opencv.core.Mat();

        Imgproc.cvtColor(srcMat, greyscaledMat, Imgproc.COLOR_RGB2GRAY);
        Imgproc.equalizeHist(greyscaledMat, greyscaledMat);


        if (this.absoluteFaceSize == 0) {
            int height = greyscaledMat.rows();
            if (Math.round(height * 0.2f) > 0) {
                this.absoluteFaceSize = Math.round(height * 0.2f);
            }
        }


        MatOfRect faces = new MatOfRect();
        cascadeClassifier.detectMultiScale(greyscaledMat, faces, 1.1, 2, 0 | Objdetect.CASCADE_SCALE_IMAGE,
                new Size(this.absoluteFaceSize, this.absoluteFaceSize), new Size());

        Rect[] facesArray = faces.toArray();
        Log.e(TAG, "processOpencv: Detected faces = " + facesArray.length);
        for (int i = 0; i < facesArray.length; i++) {

            org.opencv.core.Mat imageROI = new org.opencv.core.Mat(greyscaledMat, facesArray[i]);
            org.opencv.core.Mat mask = srcMat.submat(facesArray[i]);
            Imgproc.GaussianBlur(mask, mask, new Size(55, 55), 55); // or any other processing

        }


        frame = converter.convert(srcMat);
        return frame;

    }

    private Frame detectFace3(Mat mat) {
        OpenCVFrameConverter converter = new OpenCVFrameConverter() {
            @Override
            public Frame convert(Object o) {
                return null;
            }

            @Override
            public Object convert(Frame frame) {
                return null;
            }
        };



        Mat grayMat = new Mat(mat.rows(), mat.cols());
        cvtColor(mat, grayMat, CV_BGR2GRAY);
        RectVector faces = new RectVector();

        if (this.absoluteFaceSize == 0) {
            int height = grayMat.rows();
            if (Math.round(height * 0.2f) > 0) {
                this.absoluteFaceSize = Math.round(height * 0.2f);
            }
        }

        cascadeClassifier2.detectMultiScale(grayMat, faces, 1.1, 2, 0 | Objdetect.CASCADE_SCALE_IMAGE,
                new org.bytedeco.opencv.opencv_core.Size(this.absoluteFaceSize, this.absoluteFaceSize), new org.bytedeco.opencv.opencv_core.Size());

//        Rect[] facesArray = faces.toArray();
        MatExpr blackImage = null;
        for (int i = 0; i < faces.size(); i++) {

            org.bytedeco.opencv.opencv_core.Mat imageROI = new org.bytedeco.opencv.opencv_core.Mat(grayMat, faces.get(i));



           // img.apply(new Range(273, 333), new Range(100, 160)).put(img);
//            blackImage.asMat().copyTo(mat.apply(new Range(250, 750), new Range(250, 750)));
            blackImage = Mat.zeros(new org.bytedeco.opencv.opencv_core.Size(faces.get(i).width(), faces.get(i).height()), CV_8UC1);
//            blackImage.asMat().copyTo(mat, mat.apply(faces.get(i)) );
//            blackImage.asMat().copyTo(mat.apply(faces.get(i)).put(mat) );
//            blackImage.asMat().copyTo(mat.apply(new Range(0, 20), new Range(0, 20)));
//            mat.apply(new Range(273, 333), new Range(100, 160)).put(blackImage.asMat());
            mat.copyTo(blackImage.asMat().apply(new Range(250, 750), new Range(250, 750)));
            //   Mat f = new Mat(new Rect(4,5,7,8));
           // blackImage.asMat().apply()
//            org.bytedeco.opencv.opencv_core.Mat mask = mat.submat(faces.get(i));
//            org.bytedeco.opencv.global.opencv_imgproc.GaussianBlur(mask, mask, new org.bytedeco.opencv.opencv_core.Size(55, 55), 55); // or any other processing

        }
        Frame    frame = converter.convert(mat);
        return frame;

    }


    private CascadeClassifier setupCascadeClassifier() {
        InputStream is = getResources().openRawResource(R.raw.haarcascade);
        File cascadeDir = getActivity().getDir("cascade", Context.MODE_PRIVATE);
        File mCascadeFile = new File(cascadeDir, "cascade.xml");
        FileOutputStream os = null;
        try {
            os = new FileOutputStream(mCascadeFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // We can "cast" Pointer objects by instantiating a new object of the desired class.
        cascadeClassifier = new CascadeClassifier(mCascadeFile.getAbsolutePath());
        if (cascadeClassifier == null) {
            System.err.println("Error loading classifier file \"" + "classifierName" + "\".");
            System.exit(1);
            Log.e(TAG, "processOpencv: Error loading file");
            Toast.makeText(getActivity(), "Error loading file", Toast.LENGTH_SHORT).show();
        }
        return cascadeClassifier;
    }

    private org.bytedeco.opencv.opencv_objdetect.CascadeClassifier setupCascadeClassifier2() {
        InputStream is = getResources().openRawResource(R.raw.haarcascade);
        File cascadeDir = getActivity().getDir("cascade", Context.MODE_PRIVATE);
        File mCascadeFile = new File(cascadeDir, "cascade.xml");
        FileOutputStream os = null;
        try {
            os = new FileOutputStream(mCascadeFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // We can "cast" Pointer objects by instantiating a new object of the desired class.
        cascadeClassifier2 = new org.bytedeco.opencv.opencv_objdetect.CascadeClassifier(mCascadeFile.getAbsolutePath());
        if (cascadeClassifier2 == null) {
            System.err.println("Error loading classifier file \"" + "classifierName" + "\".");
            System.exit(1);
            Log.e(TAG, "processOpencv: Error loading file");
            Toast.makeText(getActivity(), "Error loading file", Toast.LENGTH_SHORT).show();
        }
        return cascadeClassifier2;
    }
}