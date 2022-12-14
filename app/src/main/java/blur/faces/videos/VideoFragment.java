package blur.faces.videos;

import static org.bytedeco.javacpp.avcodec.AV_CODEC_ID_MPEG4;
import static org.bytedeco.javacpp.opencv_imgproc.cvtColor;
import static org.bytedeco.javacpp.opencv_imgproc.equalizeHist;

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

import org.bytedeco.javacpp.avcodec;
import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacv.AndroidFrameConverter;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
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

          InputStream  istr = getActivity().getAssets().open("face.mp4");
//
            FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(istr);
            grabber.start();

//            FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputStream);
//            grabber.start();


          //  File output = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "akt.avi");
//            FileOutputStream fileout = new FileOutputStream(output.getAbsolutePath());

            OutputStream outputStream = getFileOutputStream();

            FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputStream, grabber.getImageWidth(), grabber.getImageHeight(), grabber.getAudioChannels());
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
//            recorder.setVideoQuality(0);
            recorder.setAudioChannels(2);
            recorder.setSampleRate(grabber.getSampleRate());
//            recorder.setFormat("mp4");
            recorder.setFormat("matroska");
            recorder.start();
//
//
//            recorder.setInterleaved(true);
//            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
//            recorder.setFormat("matroska");
//            recorder.setVideoQuality(0);
//            System.out.println("???????????????????????????" + grabber.getVideoBitrate());
//            recorder.setVideoBitrate(grabber.getVideoBitrate());
//            recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P); // yuv420p
//            int frameRate = 30;
//            recorder.setFrameRate(grabber.getFrameRate());
//            recorder.setGopSize(frameRate * 2);
//            recorder.setAudioOption("crf", "0");
//            recorder.setAudioQuality(0);// ????????????
//            recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
//            recorder.setAudioChannels(2);
//            recorder.setSampleRate(44100);
         //   recorder.start();

            Frame frame = null;
            try {

                boolean isStart = true;


                while (isStart && (frame = grabber.grabFrame()) != null) {
                    Log.e(TAG, "frame--->" + frame + "frame.image---->" + frame.image);
//
                    if (frame == null) break;
                    if (frame.image == null) continue;

//                    org.bytedeco.javacpp.opencv_core.Mat matImaage = converter.convertToMat(frame);
//                    Bitmap bitmapImage = androidFrameConverter.convert(frame);

                    org.bytedeco.javacpp.opencv_core.Mat src = converter.convertToMat(frame);
                    org.bytedeco.javacpp.opencv_core.Mat greyScaledImage = new org.bytedeco.javacpp.opencv_core.Mat();
                    //Converting the image to grey scale
                    cvtColor(src, greyScaledImage, Imgproc.COLOR_RGB2GRAY);

                    equalizeHist(greyScaledImage, greyScaledImage);

                    if (this.absoluteFaceSize == 0) {
                        int height = greyScaledImage.rows();
                        if (Math.round(height * 0.2f) > 0) {
                            this.absoluteFaceSize = Math.round(height * 0.2f);
                        }
                    }


                    Mat greyScaledImageMat = converter.convertToOrgOpenCvCoreMat(converter.convert(greyScaledImage));
                    Mat srcMat = converter.convertToOrgOpenCvCoreMat(converter.convert(src));


                    MatOfRect faces = new MatOfRect();
                    cascadeClassifier.detectMultiScale(greyScaledImageMat, faces, 1.1, 2, 0 | Objdetect.CASCADE_SCALE_IMAGE,
                            new Size(this.absoluteFaceSize, this.absoluteFaceSize), new Size());

                    Rect[] facesArray = faces.toArray();
                    Log.e(TAG, "processOpencv: Detected faces = " + facesArray.length);
                    for (int i = 0; i < facesArray.length; i++) {

                        Mat imageROI = new Mat(greyScaledImageMat, facesArray[i]);

                        Mat mask = srcMat.submat(facesArray[i]);
                        Imgproc.GaussianBlur(mask, mask, new Size(55, 55), 55); // or any other processing

                    }


                    //  frame = converter.convert(greyScaledImage);
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
        private void dismissProgressDialog () {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    dismissLoadingDialog();
                }
            });
        }

        private CascadeClassifier setupCascadeClassifier () {
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

        private void dismissLoadingDialog () {
            if (isAdded() && progressDialog != null) {
                progressDialog.dismiss();
            }
        }

        private void initProgressDialog () {
            progressDialog = new ProgressDialog(getActivity());
            progressDialog.setMessage("Please Wait..."); // Setting Message
            progressDialog.setTitle("Handwriting To Text"); // Setting Title
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER); // Progress Dialog Style Spinner
            progressDialog.show(); // Display Progress Dialog
            progressDialog.setCancelable(false);

        }
    }