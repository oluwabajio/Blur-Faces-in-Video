package blur.faces.videos;


import static org.opencv.videoio.Videoio.CAP_PROP_FRAME_COUNT;


import static blur.faces.videos.utils.AppUtils.copy;
import static blur.faces.videos.utils.AppUtils.setupCascadeClassifier;

import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;


import com.ctech.bitmp4.MP4Encoder;


import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.Objdetect;
import org.opencv.videoio.VideoCapture;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
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
    private File output;
    private File finall;
    private String videoFileName;
    private String path = "";
    private Uri uriOutputVideo;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentVideoBinding.inflate(inflater, container, false);
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);

        startVideoOperation();
        initListeners();

        return binding.getRoot();
    }

    private void initListeners() {
        binding.btnOpen.setOnClickListener(v -> playVideo());

        binding.btnSave.setOnClickListener(v -> saveVideo());
    }

    private void saveVideo() {
        try {
            copy(new FileInputStream(output), getFileOutputStream());
            Toast.makeText(getActivity(), "File Saved Successfully to " + path, Toast.LENGTH_SHORT).show();
            binding.tvLocation.setText("File Saved Successfully to " + path);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getActivity(), "Error Saving File", Toast.LENGTH_SHORT).show();
        }
    }

    private void playVideo() {

        Uri uri = Uri.parse(new File(path).getAbsolutePath());
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setDataAndType(uri, "video/mp4");
        startActivity(intent);
    }

    private void startVideoOperation() {
//        initProgressDialog();
        binding.btnSave.setVisibility(View.GONE);
        binding.surfaceview.setVisibility(View.GONE);
        binding.lyProcessing.setVisibility(View.VISIBLE);
        processVideo();
    }


    private void processVideo() {


        try {

            String uriString = getArguments().getString("uristring");
            Uri videoUri = Uri.parse(uriString);

            InputStream inputStream = getActivity().getContentResolver().openInputStream(videoUri);

            File folder = getActivity().getCacheDir();
            File copyFile = new File(getActivity().getCacheDir().getAbsolutePath() + "/filee.mp4");
            output = new File(folder, "output.mp4");


            copy(inputStream, copyFile);

            if (copyFile.exists()) {
                Toast.makeText(getActivity(), "File exists "+ copyFile.getPath(), Toast.LENGTH_SHORT).show();
            }


//
            VideoCapture videoCapture = new VideoCapture(copyFile.getPath());
//

  Mat frame = new Mat();
            MP4Encoder encoder = new MP4Encoder();
            encoder.setFrameDelay(50);
            encoder.setOutputFilePath(output.getPath());
            encoder.setOutputSize(500, 500);
            encoder.startEncode();

            if (videoCapture.isOpened()) {
                int counter = 0;
                int noOfFrames = 0;
                int length = (int) videoCapture.get(CAP_PROP_FRAME_COUNT);
                Log.e(TAG, "processVideo: video frame length = $length");
                while (videoCapture.read(frame)) {

                    if (counter % 30 == 0) {

                        Bitmap bmp =
                                Bitmap.createBitmap(
                                        frame.cols(),
                                        frame.rows(),
                                        Bitmap.Config.ARGB_8888
                                );
                        Utils.matToBitmap(frame, bmp);
//                        bitmapArray.add(bmp);
                        noOfFrames++;
                        Log.e(TAG, "doInBackground: counter = " + counter);
                        Log.e(TAG, "doInBackground: noOfFrames = " + noOfFrames);
                        encoder.addFrame(bmp);


                    }
                    counter++;

                }


            }
            encoder.stopEncode();
            Toast.makeText(getActivity(), "Encoding completed", Toast.LENGTH_SHORT).show();
            copyVideoToDevice(output.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getActivity(), "Error = " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }


    private void copyVideoToDevice(String fixed_path) {


        try {


            InputStream in = new FileInputStream(new File(fixed_path));

            OutputStream out = getFileOutputStream();

            // Copy the bits from instream to outstream
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();

            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String msg = "Successful " + fixed_path;
                    Toast.makeText(getActivity(), msg, Toast.LENGTH_LONG)
                            .show();


                }
            });

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getActivity(), "Error Saving File", Toast.LENGTH_SHORT).show();
        }
    }


//    private OutputStream getFileOutputStream() {
//        videoFileName = "video_" + System.currentTimeMillis() + ".mp4";
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//           ContentValues valuesvideos = new ContentValues();
//            valuesvideos.put(
//                    MediaStore.Video.Media.RELATIVE_PATH,
//                    Environment.DIRECTORY_MOVIES + "/RepairVideo"
//            );
//            valuesvideos.put(MediaStore.Video.Media.TITLE, videoFileName);
//            valuesvideos.put(MediaStore.Video.Media.DISPLAY_NAME, videoFileName);
//            valuesvideos.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
//            valuesvideos.put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
//            valuesvideos.put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis());
//            Uri collection =
//                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
//            Uri uriSavedVideo = requireActivity().getContentResolver().insert(collection, valuesvideos)
//            uriOutputVideo = uriSavedVideo
//            try {
//             ParcelFileDescriptor pfd = requireActivity().getContentResolver().openFileDescriptor(
//                        uriSavedVideo, "rw"
//                );
//               OutputStream out = requireActivity().getContentResolver().openOutputStream(
//                        uriSavedVideo
//                );
//
//                path = "/Internal storage/Movies/RepairVideo/$videoFileName"
//               return out;
//            } catch (java.lang.Exception e){
//                e.printStackTrace();
//                Toast.makeText(getActivity(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show()
//               return null;
//            }
//
//        } else {
//            File outputDirectory = new File(
//                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
//                            .toString() + "/RepairVideo/");
//            if (!outputDirectory.exists()) {
//                outputDirectory.mkdirs();
//            }
//           String outputDir = outputDirectory.getAbsolutePath();
//            String outputFile = outputDir + videoFileName;
//            File file = new File(outputFile);
//            uriOutputVideo = Uri.parse(file.getAbsolutePath());
//            path = "/Internal storage/Movies/RepairVideo/$videoFileName"
//            OutputStream out = null;
//            try {
//                out = new FileOutputStream(file);
//            } catch (FileNotFoundException e){
//                e.printStackTrace();
//            }
//           return out;
//        }
//    }


    private void playVideoOnScreen() {


        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                binding.btnSave.setVisibility(View.VISIBLE);
                binding.surfaceview.setVisibility(View.VISIBLE);
                binding.lyProcessing.setVisibility(View.GONE);


                binding.surfaceview.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

                try {
                    FileInputStream fi = new FileInputStream(output);
                    MediaPlayer mediaPlayer = new MediaPlayer();
                    mediaPlayer.setDataSource(fi.getFD());
                    mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);


                    binding.surfaceview.getHolder().addCallback(new SurfaceHolder.Callback() {
                        @Override
                        public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
                            mediaPlayer.setDisplay(binding.surfaceview.getHolder());
                        }

                        @Override
                        public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {

                        }

                        @Override
                        public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {

                        }
                    });

                    mediaPlayer.prepare();
                    mediaPlayer.start();

                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                    Log.e(TAG, "run: Error: " + e.getMessage());
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                    Log.e(TAG, "run: Error: " + e.getMessage());
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG, "run: Error: " + e.getMessage());
                }


            }
        });

    }

    private static double round(double value, int precision) {
        int scale = (int) Math.pow(10, precision);
        return (double) Math.round(value * scale) / scale;
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

    private void dismissProgressDialog() {
        try {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    dismissLoadingDialog();
                }
            });
        } catch (Exception e) {
        }
    }

    private void dismissLoadingDialog() {
        if (isAdded() && progressDialog != null) {
            progressDialog.dismiss();
        }
    }


}