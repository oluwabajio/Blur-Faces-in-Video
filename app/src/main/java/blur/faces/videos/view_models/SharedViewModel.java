package blur.faces.videos.view_models;

import android.graphics.Bitmap;
import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class SharedViewModel extends ViewModel {
    private final MutableLiveData<Bitmap> selectedBitmap = new MutableLiveData<Bitmap>();
    private final MutableLiveData<Uri> selectedVideoUri = new MutableLiveData<Uri>();


    public void setSelectBitmap(Bitmap bitmap) {
        selectedBitmap.setValue(bitmap);
    }
    public LiveData<Bitmap> getSelectedBitmap() {
        return selectedBitmap;
    }

    public void setSelectedVideoUri(Uri uri) {
        selectedVideoUri.setValue(uri);
    }
    public LiveData<Uri> getSelectedVideoUri() {
        return selectedVideoUri;
    }

}
