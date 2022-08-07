package blur.faces.videos.utils;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import blur.faces.videos.R;

public class AppUtils {

    private static CascadeClassifier cascadeClassifier;

    public static CascadeClassifier setupCascadeClassifier(Activity activity) {
        InputStream is = activity.getResources().openRawResource(R.raw.haarcascade);
        File cascadeDir = activity.getDir("cascade", Context.MODE_PRIVATE);
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
            Log.e("TAG", "processOpencv: Error loading file");
            Toast.makeText(activity, "Error loading file", Toast.LENGTH_SHORT).show();
        }
        return cascadeClassifier;
    }
}
