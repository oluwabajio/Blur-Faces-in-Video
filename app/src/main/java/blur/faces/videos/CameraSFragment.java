package blur.faces.videos;


import static blur.faces.videos.utils.AppUtils.copy;

import android.app.ProgressDialog;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.os.PowerManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.opencv.objdetect.CascadeClassifier;

import java.io.File;

import blur.faces.videos.databinding.FragmentCameraSBinding;


public class CameraSFragment extends Fragment {

    FragmentCameraSBinding binding;
    private final static String CLASS_LABEL = "RecordActivity";
    private final static String LOG_TAG = CLASS_LABEL;
    private PowerManager.WakeLock wakeLock;
    private boolean recording;

    //    private File savePath = new File(Environment.getExternalStorageDirectory(), "stream.mp4");
    private File savePath;
    private long startTime = 0;
    private static final String TAG = "CameraSFragment";
    private final Object semaphore = new Object();
    private int absoluteFaceSize = 0;
    private CascadeClassifier cascadeClassifier;

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

        return binding.getRoot();
    }

    }