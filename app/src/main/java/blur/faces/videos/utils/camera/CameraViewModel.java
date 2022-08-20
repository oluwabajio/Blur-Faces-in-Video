package blur.faces.videos.utils.camera;

import android.graphics.Bitmap;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class CameraViewModel extends ViewModel {

    private final Deque<FaceChecker.Direction> takePictureOrder;
    private final Map<FaceChecker.Direction, Bitmap> takePictures;

    private boolean isLastOrder;

    private final MutableLiveData<Boolean> observableFinished;

    public CameraViewModel() {
        takePictures = new HashMap<>();
        takePictureOrder = new ArrayDeque<>();
        observableFinished = new MutableLiveData<>(false);

        // 전체방향
        setTakePictureOrder(FaceChecker.Direction.values());
    }

    public void setFinished(boolean finished) {
        observableFinished.setValue(finished);
    }

    public LiveData<Boolean> getIsFinished() {
        return observableFinished;
    }

    public boolean isLastOrder() {
        return isLastOrder;
    }

    public void setPicture(FaceChecker.Direction dir, Bitmap picture) {
        takePictures.put(dir, picture);
    }

    public Map<FaceChecker.Direction, Bitmap> getTakePictures() {
        return takePictures;
    }

    public void setTakePictureOrder(FaceChecker.Direction... order) {
        takePictureOrder.addAll(Arrays.asList(order.clone()));
        isLastOrder = false;
    }

    public FaceChecker.Direction getNextOrder() {
        FaceChecker.Direction direction = takePictureOrder.pop();
        isLastOrder = takePictureOrder.isEmpty();
        return direction;
    }
}
