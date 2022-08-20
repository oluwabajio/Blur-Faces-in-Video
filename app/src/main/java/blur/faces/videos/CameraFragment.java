package blur.faces.videos;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.common.util.concurrent.ListenableFuture;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import blur.faces.videos.databinding.FragmentCameraBinding;
import blur.faces.videos.utils.camera.CameraViewModel;
import blur.faces.videos.utils.camera.FaceChecker;
import blur.faces.videos.utils.camera.FaceDetectionAnalyzer;


public class CameraFragment extends Fragment {

    private static final Size IMAGE_SIZE = new Size(720, 1280);

    FragmentCameraBinding binding;
    private static final String TAG = "CameraFragment";

    private CameraViewModel mViewModel;

    private ImageCapture imageCapture = null;
    private ImageAnalysis imageAnalysis = null;
    private FaceDetectionAnalyzer faceDetectionAnalyzer = null;
    private ExecutorService analysisExecutor;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentCameraBinding.inflate(inflater, container, false);


        mViewModel = new ViewModelProvider(requireActivity()).get(CameraViewModel.class);
        analysisExecutor = Executors.newSingleThreadExecutor();

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        startCamera();
    }

    private void startCamera() {
        binding.viewFinder.setScaleType(PreviewView.ScaleType.FILL_CENTER);

        faceDetectionAnalyzer = new FaceDetectionAnalyzer(IMAGE_SIZE, binding.detectionGraphic, direction -> {
//            imageAnalysis.clearAnalyzer();
            //    takePicture(direction);
        });
        faceDetectionAnalyzer.setDebug(true);

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(requireActivity());
        cameraProviderFuture.addListener(() -> {
            try {
                Preview preview = new Preview.Builder()
                        .setTargetResolution(IMAGE_SIZE)
                        .build();
                preview.setSurfaceProvider(binding.viewFinder.getSurfaceProvider());

                imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(IMAGE_SIZE)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageCapture = new ImageCapture.Builder()
                        .setTargetResolution(IMAGE_SIZE)
                        .build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }

            startAnalysis();
        }, ContextCompat.getMainExecutor(requireActivity()));
    }

    private void startAnalysis() {
        faceDetectionAnalyzer.startAnalysis(mViewModel.getNextOrder());
        imageAnalysis.setAnalyzer(analysisExecutor, faceDetectionAnalyzer);
    }

    private void takePicture(FaceChecker.Direction direction) {
        if (imageCapture != null) {
            imageCapture.takePicture(ContextCompat.getMainExecutor(requireContext()), new ImageCapture.OnImageCapturedCallback() {
                @OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
                @Override
                public void onCaptureSuccess(@NonNull ImageProxy imageProxy) {
                    super.onCaptureSuccess(imageProxy);
                    Bitmap bitmap = convertBitmap(imageProxy);
                    //    binding.captureImage.setImageBitmap(bitmap);
                    mViewModel.setPicture(direction, bitmap);
                    imageProxy.close();

                    if (mViewModel.isLastOrder()) {
                        mViewModel.setFinished(true);
                    } else {
                        startAnalysis();
                    }
                }

                @Override
                public void onError(@NonNull ImageCaptureException exception) {
                    super.onError(exception);
                    Log.e(TAG, "takePicture ERROR >>" + exception);
                }
            });
        }
    }

    private Bitmap convertBitmap(ImageProxy image) {
        ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
        byteBuffer.rewind();
        byte[] bytes = new byte[byteBuffer.capacity()];
        byteBuffer.get(bytes);
        byte[] clonedBytes = bytes.clone();
        Bitmap result = BitmapFactory.decodeByteArray(clonedBytes, 0, clonedBytes.length);

        int degrees = image.getImageInfo().getRotationDegrees();
        if (degrees > 0) {
            // 이미지 회전처리
            Matrix matrix = new Matrix();
            matrix.setScale(-1, 1);
            matrix.postRotate(360 - degrees);
            result = Bitmap.createBitmap(result, 0, 0, result.getWidth(), result.getHeight(), matrix, true);
        }

        return result;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (analysisExecutor != null) {
            analysisExecutor.shutdown();
            analysisExecutor = null;
        }
    }
}