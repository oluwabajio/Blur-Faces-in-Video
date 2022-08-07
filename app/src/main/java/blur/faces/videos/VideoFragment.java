package blur.faces.videos;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_MPEG4;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_MPEG4;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P;

import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;


import org.bytedeco.javacv.AndroidFrameConverter;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FFmpegLogCallback;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;

import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.Objdetect;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import blur.faces.videos.databinding.FragmentVideoBinding;
import blur.faces.videos.view_models.SharedViewModel;

//NB: mp4 doesnt work with ouutput stream (hack = use matroska )
//mp4 work with file path online

public class VideoFragment extends Fragment {

    FragmentVideoBinding binding;
    private ProgressDialog progressDialog;
    private CascadeClassifier cascadeClassifier;
    private static final String TAG = "VideoFragment";
    private int absoluteFaceSize = 0;
    SharedViewModel sharedViewModel;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentVideoBinding.inflate(inflater, container, false);
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);

        startVideoOperation();

        return binding.getRoot();
    }

    private void startVideoOperation() {
        //initProgressDialog();
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                processVideo();
            }
        });
    }

    private void processVideo() {

        CascadeClassifier cascadeClassifier = setupCascadeClassifier();

        try {
            //    InputStream inputStream = getActivity().getContentResolver().openInputStream(sharedViewModel.getSelectedVideoUri().getValue());

            FFmpegLogCallback.set();

            File input = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "face3.mp4");
            File output = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "akt.mp4");


            InputStream istr = getActivity().getAssets().open("face.avi");
            FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(input.getAbsolutePath());
            grabber.start();

//            FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputStream);
//            grabber.start();




            OutputStream outputStream = getFileOutputStream();
        //    FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputStream, grabber.getImageWidth(), grabber.getImageHeight(), grabber.getAudioChannels());
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
            recorder.setFormat("matroska");
            recorder.setAudioCodec(AV_CODEC_ID_AAC);
            recorder.setPixelFormat(AV_PIX_FMT_YUV420P);
            recorder.start();

            Frame frame = null;
            try {

                boolean isStart = true;


                while (isStart && (frame = grabber.grabFrame()) != null) {
                    Log.e(TAG, "frame--->" + frame + "frame.image---->" + frame.image);
//
                    if (frame == null) break;
                    if (frame.image == null) {
                        recorder.record(frame);
                        continue;
                    }



                    Mat srcMat = converter.convertToOrgOpenCvCoreMat(frame);
                    Mat greyscaledMat = new Mat();

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

                        Mat imageROI = new Mat(greyscaledMat, facesArray[i]);
                        Mat mask = srcMat.submat(facesArray[i]);
                        Imgproc.GaussianBlur(mask, mask, new Size(55, 55), 55); // or any other processing

                    }


                    frame = converter.convert(srcMat);


                    recorder.record(frame);
                }

                recorder.stop();
                grabber.stop();
                dismissProgressDialog();

            } catch (Exception e) {
                dismissProgressDialog();
            }

        } catch (IOException e) {
            dismissProgressDialog();
            e.printStackTrace();
        }
    }

    private OutputStream getFileOutputStream() {

        String videoFileName = "video_" + System.currentTimeMillis() + ".mp4";

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
            File outputDirectory = new File(Environment.getExternalStoragePublicDirectory("") + "/Video/");
            if (!outputDirectory.exists()) {
                outputDirectory.mkdirs();
            }
            String outputDir = outputDirectory.getAbsolutePath();
            String outputFile = outputDir + "/vid_" + new SimpleDateFormat("yyyyMM_dd-HHmmss").format(new Date()) + ".mp4";
            File file = new File(outputFile);

            OutputStream out = null;
            try {
                out = new FileOutputStream(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            return out;

        }
    }

    private void dismissProgressDialog() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                dismissLoadingDialog();
            }
        });
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

    private void dismissLoadingDialog() {
        if (isAdded() && progressDialog != null) {
            progressDialog.dismiss();
        }
    }

    private void initProgressDialog() {
        progressDialog = new ProgressDialog(getActivity());
        progressDialog.setMessage("Please Wait..."); // Setting Message
        progressDialog.setTitle("Blur Faces in Video"); // Setting Title
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER); // Progress Dialog Style Spinner
        progressDialog.show(); // Display Progress Dialog
        progressDialog.setCancelable(false);

    }


//Use seekablebytearray
//    @Test
//    public void testVideoBytesEqual() {
//        // if this test fails it might be due to indeterministic multithreaded encoding
//        System.out.println("SeekableByteArrayOutputStreamVideo");
//        File tempFile = new File(Loader.getTempDir(), "test.mp4");
//        try {
//            createVideo(new FFmpegFrameRecorder(tempFile, WIDTH, HEIGHT, 0));
//            byte[] fileBytes = Files.readAllBytes(tempFile.toPath());
//
//            SeekableByteArrayOutputStream byteArrayOutputStream = new SeekableByteArrayOutputStream();
//            createVideo(new FFmpegFrameRecorder(byteArrayOutputStream, WIDTH, HEIGHT, 0));
//            assertArrayEquals(fileBytes, byteArrayOutputStream.toByteArray());
//        } catch (Exception e) {
//            fail("Exception should not have been thrown: " + e);
//        } finally {
//            tempFile.delete();
//        }
//    }

}