package blur.faces.videos;

import static blur.faces.videos.utils.AppUtils.setupCascadeClassifier;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.Objdetect;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

import blur.faces.videos.databinding.FragmentPictureBinding;
import blur.faces.videos.view_models.SharedViewModel;


public class PictureFragment extends Fragment {

    private FragmentPictureBinding binding;
    private CascadeClassifier cascadeClassifier;
    private static final String TAG = "PictureFragment";
    private int absoluteFaceSize = 0;
    SharedViewModel sharedViewModel;
    Uri imgUri;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentPictureBinding.inflate(inflater, container, false);
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);

processOpencv();

binding.btnOpen.setOnClickListener( v -> {

//    Uri uri = Uri.parse(videoPath);
    Intent intent = new Intent(Intent.ACTION_VIEW, imgUri);
    intent.setDataAndType(imgUri, "image/jpg");
    startActivity(intent);

});
        return binding.getRoot();

    }



    private void processOpencv() {
        CascadeClassifier cascadeClassifier = setupCascadeClassifier(getActivity());
        if (cascadeClassifier == null) {
           Log.e(TAG, "Error loading classifier file \"" +" classifierName" + "\".");
            System.exit(1);
            Log.e(TAG, "processOpencv: Error loading file");
            Toast.makeText(getActivity(), "Error loading file", Toast.LENGTH_SHORT).show();
        }


        Bitmap bitmap = sharedViewModel.getSelectedBitmap().getValue();

        Mat src = new Mat();
        Bitmap bmpSrc = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Utils.bitmapToMat(bmpSrc, src);

        //Creating the empty destination matrix
        Mat greyScaledImage = new Mat();

        //Converting the image to gray sacle and saving it in the dst(greyScaledImage) matrix
        Imgproc.cvtColor(src, greyScaledImage, Imgproc.COLOR_RGB2GRAY);

        Imgproc.equalizeHist(greyScaledImage, greyScaledImage);

        if (this.absoluteFaceSize == 0) {
            int height = greyScaledImage.rows();
            if (Math.round(height * 0.2f) > 0) {
                this.absoluteFaceSize = Math.round(height * 0.2f);
            }
        }

        MatOfRect faces = new MatOfRect();
        cascadeClassifier.detectMultiScale(greyScaledImage, faces, 1.1, 2, 0 | Objdetect.CASCADE_SCALE_IMAGE,
                new Size(this.absoluteFaceSize, this.absoluteFaceSize), new Size());

        Rect[] facesArray = faces.toArray();
        Log.e(TAG, "processOpencv: Detected faces = " + facesArray.length);
        for (int i = 0; i < facesArray.length; i++) {
            // Imgproc.rectangle(src, facesArray[i].tl(), facesArray[i].br(), new Scalar(0, 255, 0, 255), 3);

            Mat imageROI = new Mat(greyScaledImage, facesArray[i]);

            Mat mask = src.submat(facesArray[i]);
            Imgproc.GaussianBlur(mask, mask, new Size(55, 55), 55); // or any other processing

        }

        Bitmap bmp = Bitmap.createBitmap(src.cols(), src.rows(), Bitmap.Config.ARGB_8888);

        Utils.matToBitmap(src, bmp);
        binding.imgImage.setImageBitmap(bmp);
        try {
            saveImage(bmp);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getActivity(), ""+e.getMessage(), Toast.LENGTH_SHORT).show();
        }

    }


    private void saveImage(Bitmap bitmap) throws IOException {

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HH_mm_ss");
       String name = "blurred_image_"+ dateFormat.format(new Date());
        String imagesDir = "";
        OutputStream fos;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = getActivity().getContentResolver();
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name + ".jpg");
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg");
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/BlurFaces");
            Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
            fos = resolver.openOutputStream(Objects.requireNonNull(imageUri));
            imagesDir = "Gallery/BlurFaces/";
            imgUri = imageUri;
        } else {
           imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() +   "/BlurFaces/";
            File image = new File(imagesDir, name + ".jpg");
            fos = new FileOutputStream(image);
            imgUri = Uri.parse(image.getAbsolutePath());
        }
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
        Objects.requireNonNull(fos).close();

        binding.imageSavedLocation.setText(imagesDir+name+".jpg");
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}