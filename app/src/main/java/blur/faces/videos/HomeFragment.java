package blur.faces.videos;



import static org.opencv.core.CvType.CV_8UC1;

import static blur.faces.videos.utils.AppUtils.setupCascadeClassifier;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;




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


    private static final String TAG = "HomeFragment";
    private int PICK_IMAGE = 567;
    private int PICK_VIDEO = 783;
    SharedViewModel sharedViewModel;
    private static final int REQUEST_CODE_PERMISSION = 101;
    private static final String[] REQUIRED_PERMISSIONS = new String[] {
            Manifest.permission.CAMERA
    };

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        initListeners();


        return binding.getRoot();

    }

    private void initListeners() {
        binding.btnSelectPicture.setOnClickListener(v -> {
            selectPicture();
        });

        binding.btnSelectVideo.setOnClickListener(v -> {
            selectVideo();

        });

        binding.btnCamera.setOnClickListener( v-> {
            if(allPermissionGranted()) {
                startCamera();
            } else {
                requestPermissions( REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSION);
            }
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == REQUEST_CODE_PERMISSION) {
            if(allPermissionGranted()) {
                startCamera();
            } else {
                Toast.makeText(getActivity(), "You must allow the camera permission to use the service.", Toast.LENGTH_SHORT).show();

            }
        }
    }

    private void startCamera() {
//       NavHostFragment.findNavController(this).navigate(R.id.testFragment);
   startActivity(new Intent(getActivity(), TestActivity.class));
    }

    private boolean allPermissionGranted() {
        for(String permission : REQUIRED_PERMISSIONS) {
            if(ContextCompat.checkSelfPermission(getActivity(), permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }


}