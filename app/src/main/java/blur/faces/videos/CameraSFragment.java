package blur.faces.videos;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_MPEG4;
import static org.bytedeco.opencv.global.opencv_core.CV_8UC1;
import static org.bytedeco.opencv.global.opencv_imgproc.CV_BGR2GRAY;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;

import static blur.faces.videos.utils.AppUtils.copy;
import static blur.faces.videos.utils.AppUtils.setupCascadeClassifier;

import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.Toast;
import org.bytedeco.javacv.AndroidFrameConverter;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameRecorder;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatExpr;
import org.bytedeco.opencv.opencv_core.Range;
import org.bytedeco.opencv.opencv_core.RectVector;
import org.opencv.android.Utils;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.Objdetect;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import blur.faces.videos.databinding.FragmentCameraSBinding;
import blur.faces.videos.utils.CvCameraPreview;


public class CameraSFragment extends Fragment implements View.OnClickListener, CvCameraPreview.CvCameraViewListener {

    FragmentCameraSBinding binding;
    private final static String CLASS_LABEL = "RecordActivity";
    private final static String LOG_TAG = CLASS_LABEL;
    private PowerManager.WakeLock wakeLock;
    private boolean recording;
    private CvCameraPreview cameraView;
    //    private File savePath = new File(Environment.getExternalStorageDirectory(), "stream.mp4");
    private File savePath;
    private FFmpegFrameRecorder recorder;
    private long startTime = 0;
    private static final String TAG = "CameraSFragment";
    private OpenCVFrameConverter.ToMat converterToMat = new OpenCVFrameConverter.ToMat();
    private OpenCVFrameConverter.ToMat converterToMat2 = new OpenCVFrameConverter.ToMat();
    private final Object semaphore = new Object();
    private int absoluteFaceSize = 0;
    private CascadeClassifier cascadeClassifier;
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

    Mat glmat = null;
    Mat facemat = null;
    private String videoFileName = "";
    private String path = "";
    private ProgressDialog progressDialog;
    private int recorderWidth = 0;
    private int recorderHeight = 0;
    private boolean flipBoolean;
    private int camType;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {


        binding = FragmentCameraSBinding.inflate(inflater, container, false);

        camType = getArguments().getInt("camType");

//        cameraView = binding.cameraView;
        cameraView = new CvCameraPreview(getActivity(), camType, 2);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
        params.leftMargin = 0;


        binding.lyCameraView.addView(cameraView, params);




        initLayout();
        return binding.getRoot();
    }

    private void initProgressDialog() {
        progressDialog = new ProgressDialog(getActivity());
        progressDialog.setMessage("Please Wait..."); // Setting Message
        progressDialog.setTitle("Blur Faces in Video"); // Setting Title
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER); // Progress Dialog Style Spinner
        progressDialog.show(); // Display Progress Dialog
        progressDialog.setCancelable(false);

    }

    private void dismissProgressDialog() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                dismissLoadingDialog();
            }
        });
    }

    private void dismissLoadingDialog() {
        if (isAdded() && progressDialog != null) {
            progressDialog.dismiss();
        }
    }


    @Override
    public void onResume() {
        super.onResume();

//        if (wakeLock == null) {
//            PowerManager pm = (PowerManager) getActivity().getSystemService(Context.POWER_SERVICE);
//            wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, CLASS_LABEL);
//            wakeLock.acquire();
//        }
    }

    @Override
    public void onPause() {
        super.onPause();

//        if (wakeLock != null) {
//            wakeLock.release();
//            wakeLock = null;
//        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
//
//        if (wakeLock != null) {
//            wakeLock.release();
//            wakeLock = null;
//        }

        if (recorder != null) {
            try {
                recorder.release();
            } catch (FrameRecorder.Exception e) {
                Log.e(TAG, "startRecording:7 Failed, Error = " + e.getMessage());
                e.printStackTrace();
            }
        }
    }


    private void initLayout() {


        cascadeClassifier = setupCascadeClassifier(getActivity());

        cameraView.setCvCameraViewListener(this);
        binding.btnStart.setOnClickListener(this);
        binding.btnFlip.setOnClickListener(v -> {
            flipBoolean = !flipBoolean;
refreshCurrentFragment();
        });

//        initFaceAndGlass();
    }

    private void initFaceAndGlass() {

        InputStream inputStream = null;
        try {
            inputStream = getActivity().getAssets().open("eyeglasses.png");

            InputStream inputStreamFace = getActivity().getAssets().open("face.jpg");

            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            Bitmap bitmapFace = BitmapFactory.decodeStream(inputStreamFace);


            AndroidFrameConverter androidFrameConverter = new AndroidFrameConverter();
            AndroidFrameConverter androidFrameConverter2 = new AndroidFrameConverter();
            Frame fr = androidFrameConverter.convert(bitmap);
            Frame frface = androidFrameConverter2.convert(bitmapFace);

            glmat = converterToMat.convert(fr);
            facemat = converterToMat2.convert(frface);
        } catch (IOException e) {
            e.printStackTrace();
        }
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


        savePath = new File(getActivity().getExternalFilesDir("BlurFace"), "stream.mp4");
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
            Log.e(TAG, "startRecording:1 Failed, Error = " + e.getMessage());
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
                Log.e(TAG, "startRecording:2 Failed, Error = " + e.getMessage());
                e.printStackTrace();
            }
            recorder = null;
        }
        initRecorder(recorderWidth, recorderHeight);
    }

    @Override
    public void onClick(View v) {
        if (!recording) {
            startRecording();
            recording = true;
            Log.e(LOG_TAG, "Start Button Pushed");
//            binding.btnStart.setText("Stop");
//            binding.btnStart.setBackgroundResource(R.drawable.stop_record);
            binding.btnStart.setImageResource(R.drawable.stop_record);
        } else {
            // This will trigger the audio recording loop to stop and then set isRecorderStart = false;
            stopRecording();
            recording = false;
            Log.e(LOG_TAG, "Stop Button Pushed");
//            btnRecorderControl.setText("Start");
//            binding.btnStart.setVisibility(View.GONE);
            binding.btnStart.setImageResource(R.drawable.record);

            try {
                copy(new FileInputStream(savePath), getFileOutputStream());

                Toast.makeText(getActivity(), "Video file was saved to \"" + path + "\"", Toast.LENGTH_LONG).show();

            } catch (FileNotFoundException e) {
                e.printStackTrace();
                Log.e(TAG, "startRecording:9 Failed, Error = " + e.getMessage());
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "startRecording:10 Failed, Error = " + e.getMessage());
            }


        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        recorderWidth = width;
        recorderHeight = height;
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

                    Frame newFrame = detectFace(converter.convert(mat));
                    long t = 1000 * (System.currentTimeMillis() - startTime);
                    if (t > recorder.getTimestamp()) {
                        recorder.setTimestamp(t);
                    }
                    recorder.record(newFrame);

                } catch (FrameRecorder.Exception e) {
                    Log.e(LOG_TAG, e.getMessage());
                    Log.e(TAG, "startRecording:3 Failed, Error = " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        return mat;
    }


    private Frame detectFace(Frame frame) {

        org.opencv.core.Mat srcMat = converter.convertToOrgOpenCvCoreMat(frame);
        org.opencv.core.Mat greyscaledMat = new org.opencv.core.Mat();

        Imgproc.cvtColor(srcMat, greyscaledMat, Imgproc.COLOR_RGB2GRAY);
        Imgproc.equalizeHist(greyscaledMat, greyscaledMat);

        initAbsoluteFace(greyscaledMat);


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

    private void initAbsoluteFace(org.opencv.core.Mat greyscaledMat) {
        if (this.absoluteFaceSize == 0) {
            int height = greyscaledMat.rows();
            if (Math.round(height * 0.1f) > 0) {
                this.absoluteFaceSize = Math.round(height * 0.1f);
            }
        }
    }


    private OutputStream getFileOutputStream() {

        videoFileName = "video_" + System.currentTimeMillis() + ".mp4";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues valuesvideos = new ContentValues();
            valuesvideos.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/BlurFaces");
            valuesvideos.put(MediaStore.Video.Media.TITLE, videoFileName);
            valuesvideos.put(MediaStore.Video.Media.DISPLAY_NAME, videoFileName);
            valuesvideos.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            valuesvideos.put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
            valuesvideos.put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis());
            final Uri collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri uriSavedVideo = getActivity().getContentResolver().insert(collection, valuesvideos);

            try {
                ParcelFileDescriptor pfd = getActivity().getContentResolver().openFileDescriptor(uriSavedVideo, "rw");
                OutputStream out = getActivity().getContentResolver().openOutputStream(uriSavedVideo);
                path = "/Internal storage/Movies/BlurFaces/" + videoFileName;
                return out;
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(getActivity(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                return null;
            }

//                valuesvideos.clear();
//                valuesvideos.put(MediaStore.Video.Media.IS_PENDING, 0);
//                getActivity().getContentResolver().update(uriSavedVideo, valuesvideos, null, null);
//                Toast.makeText(getActivity(), "Saved Successfully", Toast.LENGTH_SHORT).show();


        } else {
            File outputDirectory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES) + "/BlurFaces/");
            if (!outputDirectory.exists()) {
                outputDirectory.mkdirs();
            }
            String outputDir = outputDirectory.getAbsolutePath();
            String outputFile = outputDir + videoFileName;
            File file = new File(outputFile);
            path = "/Internal storage/Movies/BlurFaces/" + videoFileName;

            OutputStream out = null;
            try {
                out = new FileOutputStream(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            return out;

        }
    }

    private void refreshCurrentFragment() {
        NavController navController = NavHostFragment.findNavController(this);
        int id = NavHostFragment.findNavController(this).getCurrentDestination().getId();
        navController.popBackStack(id, true);
        Bundle bundle = new Bundle();
        bundle.putInt("camType", getCamType());
        navController.navigate(id, bundle);
    }

    private int getCamType() {
        return (camType == 99) ? 98 : 99;
    }

}