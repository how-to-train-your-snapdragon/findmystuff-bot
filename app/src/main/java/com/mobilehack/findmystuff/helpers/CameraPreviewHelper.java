package com.mobilehack.findmystuff.helpers;

import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.OnLifecycleEvent;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;


import com.mobilehack.findmystuff.MainActivity;

import io.fotoapparat.Fotoapparat;
import io.fotoapparat.parameter.Resolution;
import io.fotoapparat.parameter.ScaleType;
import io.fotoapparat.preview.Frame;
import io.fotoapparat.result.BitmapPhoto;
import io.fotoapparat.result.PendingResult;
import io.fotoapparat.result.WhenDoneListener;
import io.fotoapparat.view.CameraView;

import static io.fotoapparat.log.LoggersKt.logcat;
import static io.fotoapparat.selector.FocusModeSelectorsKt.autoFocus;
import static io.fotoapparat.selector.FocusModeSelectorsKt.continuousFocusPicture;
import static io.fotoapparat.selector.FocusModeSelectorsKt.fixed;
import static io.fotoapparat.selector.LensPositionSelectorsKt.back;
import static io.fotoapparat.selector.PreviewFpsRangeSelectorsKt.highestFps;
import static io.fotoapparat.selector.ResolutionSelectorsKt.highestResolution;
import static io.fotoapparat.selector.SelectorsKt.firstAvailable;

public class CameraPreviewHelper implements LifecycleObserver {

    private final Fotoapparat mCamera;

    public interface Callbacks {
        // implement this to select which Camera Preview Feed resolution to use
        Resolution selectPreviewResolution(Iterable<Resolution> resolutions);

        // implement this to receive each frame; which you'll have to adjust for colorspace/resolution/rotation
        void onCameraPreviewFrame(Frame frame);
    }

    @SuppressWarnings("unchecked")
    public CameraPreviewHelper(Context context, CameraView cameraView, Callbacks callbacks, boolean letterbox) {
        mCamera = Fotoapparat
                .with(context)
                .into(cameraView)
                .lensPosition(back())
                // the following doesn't change the preview bytes, only the 'zoom' of the displayed texture
                .previewScaleType(letterbox ? ScaleType.CenterInside : ScaleType.CenterCrop)
                .previewFpsRange(highestFps())
                .previewResolution(callbacks::selectPreviewResolution)
                .photoResolution(highestResolution())
                .focusMode(firstAvailable(continuousFocusPicture(), autoFocus(), fixed()))
                .frameProcessor(callbacks::onCameraPreviewFrame)
                .logger(logcat())

                .build();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    private void startPreview() {
        // Log.e(MainActivity.LOGTAG, "Start camera");
        mCamera.start();
    }

    public void setBitMap(ImageView screenshotView, BitMapInterface bitMapInterface) {

        PendingResult<BitmapPhoto> bitmapPendingResult = mCamera.takePicture().toBitmap();
        bitmapPendingResult.whenDone(new WhenDoneListener<BitmapPhoto>() {
            @Override
            public void whenDone(BitmapPhoto bitmapPhoto) {

                Bitmap bitmap = bitmapPhoto.bitmap;
                screenshotView.setImageBitmap(bitmapPhoto.bitmap);
                screenshotView.setRotation(-bitmapPhoto.rotationDegrees);
                bitMapInterface.gotBitMap(bitmap);

            }
        });

    }


    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    private void stopPreview() {
        //Log.e(MainActivity.LOGTAG, "Stop camera");
        mCamera.stop();
    }

    public interface BitMapInterface {

        void gotBitMap(Bitmap bitmap);
    }
}
