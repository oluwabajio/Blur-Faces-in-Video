package blur.faces.videos;


import static org.bytedeco.javacpp.avcodec.AV_CODEC_ID_MPEG4;
import static org.bytedeco.javacpp.opencv_imgproc.cvtColor;
import static org.bytedeco.javacpp.opencv_imgproc.equalizeHist;
import static org.opencv.core.CvType.CV_8UC1;

import static blur.faces.videos.utils.AppUtils.setupCascadeClassifier;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacv.AndroidFrameConverter;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.jcodec.api.FrameGrab;
import org.jcodec.api.JCodecException;
import org.jcodec.api.android.AndroidSequenceEncoder;
import org.jcodec.common.AndroidUtil;
import org.jcodec.common.AudioFormat;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Picture;
import org.jcodec.common.model.Rational;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.Objdetect;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import blur.faces.videos.databinding.FragmentHomeBinding;
import blur.faces.videos.view_models.SharedViewModel;


//my options, jcodec, medi coded bytebuffer to bytebuffer, mediacodec opengl surface, javacv oropencv frame grabber

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;

    private CascadeClassifier cascadeClassifier;
    private static final String TAG = "HomeFragment";
    private int PICK_IMAGE = 567;
    private int PICK_VIDEO = 783;
    SharedViewModel sharedViewModel;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        initListeners();
//        AsyncTask.execute(new Runnable() {
//            @Override
//            public void run() {
//                processVideoWorking();
//            }
//        });


        return binding.getRoot();

    }

    private void initListeners() {
        binding.btnSelectPicture.setOnClickListener(v -> {
            selectPicture();
        });

        binding.btnSelectVideo.setOnClickListener(v -> {
            selectVideo();

        });


    }


    private void selectPicture() {

        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE);


    }

    private void selectVideo() {

        Intent intent = new Intent();
        intent.setType("video/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Video"), PICK_VIDEO);


    }


    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


    }


    private void processVideoWorking() {

        CascadeClassifier cascadeClassifier = setupCascadeClassifier(getActivity());
        InputStream istr = null;

        try {
            istr = getActivity().getAssets().open("face.mp4");

            FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(istr);
            grabber.start();


            File output = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "akt.avi");
            FileOutputStream fileout = new FileOutputStream(output.getAbsolutePath());


            FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(output.getAbsolutePath(), grabber.getImageWidth(), grabber.getImageHeight(), grabber.getAudioChannels());
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
            AndroidFrameConverter androidFrameConverter = new AndroidFrameConverter();

            recorder.setVideoCodec(AV_CODEC_ID_MPEG4);
            //recorder.setVideoBitrate(10 * 1024 * 1024);
            recorder.setFrameRate(24.0);
            recorder.setVideoQuality(0);
            recorder.setAudioChannels(2);
            recorder.setSampleRate(grabber.getSampleRate());
            recorder.setFormat("mp4");
            recorder.start();


            Frame frame = null;
            try {

                boolean isStart = true;


                Bitmap bitmap = null;
                while (isStart && (frame = grabber.grabFrame()) != null) {
                    Log.e(TAG, "frame--->" + frame + "frame.image---->" + frame.image);

                    if (frame == null) break;
                    if (frame.image == null) continue;

                    org.bytedeco.javacpp.opencv_core.Mat matImaage = converter.convertToMat(frame);
                    Bitmap bitmapImage = androidFrameConverter.convert(frame);

                    org.bytedeco.javacpp.opencv_core.Mat src = converter.convertToMat(frame);
                    org.bytedeco.javacpp.opencv_core.Mat dst = new org.bytedeco.javacpp.opencv_core.Mat();
                    //Converting the image to grey scale
                    cvtColor(src, dst, Imgproc.COLOR_RGB2GRAY);
                    // Imgproc.cvtColor(src, dst, Imgproc.COLOR_RGB2GRAY);
                    frame = converter.convert(dst);


                    recorder.record(frame);
//                try {
//
//                    mat = imgeconvert.convertToMat(frame);
//
//                    int hei = picwidth * mat.rows() / mat.cols();
//                    resize(mat, mat, new opencv_core.Size(picwidth, hei));
//
//                    bitmap = bitmapConverter.convert(imgeconvert.convert(mat));
//
//
//                    Log.i(TAG, "bitmapcount=" + bitmap.getByteCount());
//                    if (bitmaprecvface != null) {
//
//                        bitmaprecvface.recv(bitmap);
//                    }
//
//                } catch (Exception e) {
//                    Log.i(TAG, "error" + e.getMessage());
//                    continue;
//                }
                }

                recorder.stop();
                grabber.stop();

            } catch (Exception e) {

            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == Activity.RESULT_OK) {
            if (data == null) {
                return;
            }
            Uri imageUri = data.getData();
            try {
                InputStream inputStream = getActivity().getContentResolver().openInputStream(data.getData());
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getActivity().getContentResolver(), imageUri);
                sharedViewModel.setSelectBitmap(bitmap);
                NavHostFragment.findNavController(HomeFragment.this).navigate(R.id.pictureFragment);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        if (requestCode == PICK_VIDEO && resultCode == Activity.RESULT_OK) {
            if (data == null) {
                return;
            }
            Uri videoUri = data.getData();
            try {
                InputStream inputStream = getActivity().getContentResolver().openInputStream(data.getData());
                sharedViewModel.setSelectedVideoUri(videoUri);
                NavHostFragment.findNavController(HomeFragment.this).navigate(R.id.videoFragment);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

}