package com.mobilehack.findmystuff;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.androidnetworking.AndroidNetworking;
import com.androidnetworking.common.Priority;
import com.androidnetworking.error.ANError;
import com.androidnetworking.interfaces.JSONObjectRequestListener;
import com.mobilehack.findmystuff.helpers.CameraPreviewHelper;
import com.mobilehack.findmystuff.helpers.NV21ConversionHelper;
import com.mobilehack.findmystuff.helpers.SNPEHelper;

import io.fotoapparat.parameter.Resolution;
import io.fotoapparat.preview.Frame;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

import android.arch.lifecycle.Lifecycle;
import android.graphics.Color;
import android.graphics.Matrix;
import android.widget.Toast;


import org.json.JSONObject;

import okhttp3.OkHttpClient;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;


/**
 * Main Camera screen of the bot application
 * @author Thomas Binu
 * @author Anitha Ramaswamy
 * @author Ashuthosh Giri
 */
public class MainActivity extends AppCompatActivity {


    String sendIP = "http://10.73.240.58:8000";

    int port = 8000;

    private SNPEHelper mSnpeHelper;
    private CameraPreviewHelper mCameraPreviewHelper;
    private NV21ConversionHelper mNV21ConversionHelper;

    private OverlayRenderer mOverlayRenderer;
    private boolean mNetworkLoaded;
    private int mNV21FrameRotation;
    private boolean mInferenceSkipped;
    private Bitmap mNV21PreviewBitmap;
    private Bitmap mModelInputBitmap;
    private Canvas mModelInputCanvas;
    private Paint mModelBitmapPaint;
    ImageView screenShotView;
    String searchLabel;

    BlueToothManager blueToothManager = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mOverlayRenderer = findViewById(R.id.overlayRenderer);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        OkHttpClient okHttpClient = new OkHttpClient().newBuilder().build();
        AndroidNetworking.initialize(getApplicationContext(), okHttpClient);
        final AndroidWebServer androidWebServer = new AndroidWebServer(this, port);


        final Button sendButton = (Button) findViewById(R.id.sendButton);
        final TextView statusTextView = (TextView) findViewById(R.id.statusText);
        final Button serverButton = (Button) findViewById(R.id.serverButton);
        screenShotView = findViewById(R.id.screenshotview);

        mOverlayRenderer.setStatusView(statusTextView);
        //mOverlayRenderer.setScreenshotView(this, screenShotView, fotoapparat);

        statusTextView.setText(R.string.readyMessage_txt);

        blueToothManager = new BlueToothManager(this);

        if (!androidWebServer.isAlive()) {

            try {
                androidWebServer.start();
                serverButton.setText(getText(R.string.stop_server_txt));
                statusTextView.setText(getString(R.string.serverListenTxt) + getIpAccess() + port);

            } catch (IOException e) {
                e.printStackTrace();
                statusTextView.setText(e.getMessage());
                //textView.setText(R.string.serverNotRunning_txt);

            }
        }

        androidWebServer.setAndroidServerInterface(new AndroidWebServer.AndroidServerInterface() {
            @Override
            public void gotMessage(String message) {
                searchLabel = message;
                blueToothManager.sendMessage("1");
            }
        });


    }

    // this function makes sure we load the network the first time it's required - we could do it
    // in the onCreate, but this will block the first paint of the UI, leaving a gray box
    private void ensureNetCreated() {
        if (mSnpeHelper == null) {

            // load the neural network for object detection with SNPE
            mSnpeHelper = new SNPEHelper(getApplication());
            mNetworkLoaded = mSnpeHelper.loadMobileNetSSDFromAssets();


        }
    }

    private final CameraPreviewHelper.Callbacks mCameraPreviewCallbacks = new CameraPreviewHelper.Callbacks() {
        @Override
        public Resolution selectPreviewResolution(Iterable<Resolution> resolutions) {
            // defer net creation to this moment, so the UI has time to be flushed
            ensureNetCreated();

            // This function selects the resolution (amongst the set of possible 'preview'
            // resolutions) which is closest to the input resolution of the model (but not smaller)
            final int fallbackSize = 300; // if the input is not reliable, just assume some size;
            final int targetWidth = mNetworkLoaded ? mSnpeHelper.getInputTensorWidth() : fallbackSize;
            final int targetHeight = mNetworkLoaded ? mSnpeHelper.getInputTensorHeight() : fallbackSize;
            io.fotoapparat.parameter.Resolution preferred = null;
            double preferredScore = 0;
            for (Resolution resolution : resolutions) {
                if (resolution.width < targetWidth || resolution.height < targetHeight)
                    continue;
                double score = Math.pow(targetWidth - resolution.width, 2) + Math.pow(targetHeight - resolution.height, 2);
                if (preferred == null || score < preferredScore) {
                    preferred = resolution;
                    preferredScore = score;
                }
            }
            return preferred;
        }

        @Override
        public void onCameraPreviewFrame(Frame frame) {
            // NOTE: This is executed on a different thread - don't update UI from this!
            // NOTE: frame.image in NV21 format (1.5 bytes per pixel) - often rotated (e.g. 270),
            // different frame.size.width (ex. 1600) and frame.size.height (ex. 1200) than the model
            // input.

            // skip processing if the neural net is not loaded - nothing to do with this Frame
            if (!mNetworkLoaded)
                return;
            // skip processing if the app is paused or stopped (one frame may still be pending)
            if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED))
                return;


            mNV21FrameRotation = frame.getRotation();
            mNV21PreviewBitmap = mNV21ConversionHelper.convert(frame.getImage(), frame.getSize().width, frame.getSize().height);


            final int inputWidth = mSnpeHelper.getInputTensorWidth();
            final int inputHeight = mSnpeHelper.getInputTensorHeight();
            // allocate the object only on the first time
            if (mModelInputBitmap == null || mModelInputBitmap.getWidth() != inputWidth || mModelInputBitmap.getHeight() != inputHeight) {
                // create ARGB8888 bitmap and canvas, with the right size
                mModelInputBitmap = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888);
                mModelInputCanvas = new Canvas(mModelInputBitmap);

                // compute the roto-scaling matrix (preview image -> screen image) and apply it to
                // the canvas. this includes translation for 'letterboxing', i.e. the image will
                // have black bands to the left and right if it's a portrait picture
                final Matrix mtx = new Matrix();
                final int previewWidth = mNV21PreviewBitmap.getWidth();
                final int previewHeight = mNV21PreviewBitmap.getHeight();
                final float scaleWidth = ((float) inputWidth) / previewWidth;
                final float scaleHeight = ((float) inputHeight) / previewHeight;
                final float frameScale = Math.min(scaleWidth, scaleHeight); // centerInside
                //final float frameScale = Math.max(scaleWidth, scaleHeight); // centerCrop
                final float dx = inputWidth - (previewWidth * frameScale);
                final float dy = inputHeight - (previewHeight * frameScale);
                mtx.postScale(frameScale, frameScale);
                mtx.postTranslate(dx / 2, dy / 2);
                if (frame.getRotation() != 0) {
                    mtx.postTranslate(-inputWidth / 2, -inputHeight / 2);
                    mtx.postRotate(-frame.getRotation());
                    mtx.postTranslate(inputWidth / 2, inputHeight / 2);
                }
                mModelInputCanvas.setMatrix(mtx);

                // create the "Paint", to set the antialiasing option
                mModelBitmapPaint = new Paint();
                mModelBitmapPaint.setFilterBitmap(true);

                // happens only the first time

            }
            mModelInputCanvas.drawColor(Color.BLACK);
            mModelInputCanvas.drawBitmap(mNV21PreviewBitmap, 0, 0, mModelBitmapPaint);

            final ArrayList<Box> boxes = mSnpeHelper.mobileNetSSDInference(mModelInputBitmap);


            // [2-45ms] give the bitmap to SNPE for inference

            mInferenceSkipped = boxes == null;

            if (!mInferenceSkipped) {


                HashSet<String> nearStringsSet = new HashSet<>();

                for (Box box : boxes) {


                    String textLabel = (box.type_name != null && !box.type_name.isEmpty()) ? box.type_name : String.valueOf(box.type_id + 2);

                    if (box.type_score < 0.7)
                        continue;

                    nearStringsSet.add(textLabel);

                    Log.d("objects", textLabel);


                    if (searchLabel != null && textLabel.toLowerCase().contains(searchLabel.toLowerCase())) {


                        String nearObjects = "";

                        int count = 0;

                        for (String word : nearStringsSet) {

                            if (!searchLabel.contains(word) && count <= 2) {
                                nearObjects += word + ", ";

                            }

                            count++;
                        }

                        Log.d("TEST", searchLabel);
                        String searchLabelFinal = searchLabel;
                        String nearObjectsFinal = nearObjects;

                        searchLabel = null;

                        MainActivity.this.runOnUiThread(new Runnable() {
                            public void run() {

                                Toast.makeText(MainActivity.this, searchLabelFinal + " found", Toast.LENGTH_SHORT).show();


                                final Handler handler = new Handler();
                                handler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {


                                        AsyncTask.execute(new Runnable() {
                                            @Override
                                            public void run() {

//                                                        ByteArrayOutputStream baos=new  ByteArrayOutputStream();
//                                                        bitmap.compress(Bitmap.CompressFormat.JPEG,50, baos);
//                                                        byte [] b=baos.toByteArray();
//                                                        String temp=Base64.encodeToString(b, Base64.DEFAULT);

                                                AndroidNetworking.post(sendIP)
                                                        .addQueryParameter("image", searchLabelFinal + " found")
                                                        .setTag("test")
                                                        .setPriority(Priority.MEDIUM)
                                                        .build()
                                                        .getAsJSONObject(new JSONObjectRequestListener() {
                                                            @Override
                                                            public void onResponse(JSONObject response) {


                                                            }

                                                            @Override
                                                            public void onError(ANError error) {


                                                                error.printStackTrace();
                                                            }
                                                        });

                                                blueToothManager.sendMessage("0");
                                            }
                                        });
//
//                                        mCameraPreviewHelper.setBitMap(screenShotView, new CameraPreviewHelper.BitMapInterface() {
//                                            @Override
//                                            public void gotBitMap(Bitmap bitmap) {
//
//
//                                                AsyncTask.execute(new Runnable() {
//                                                    @Override
//                                                    public void run() {
//
////                                                        ByteArrayOutputStream baos=new  ByteArrayOutputStream();
////                                                        bitmap.compress(Bitmap.CompressFormat.JPEG,50, baos);
////                                                        byte [] b=baos.toByteArray();
////                                                        String temp=Base64.encodeToString(b, Base64.DEFAULT);
//
//                                                        AndroidNetworking.post(sendIP)
//                                                                .addQueryParameter("image", searchLabelFinal + " found near " + nearObjectsFinal)
//                                                                .setTag("test")
//                                                                .setPriority(Priority.MEDIUM)
//                                                                .build()
//                                                                .getAsJSONObject(new JSONObjectRequestListener() {
//                                                                    @Override
//                                                                    public void onResponse(JSONObject response) {
//
//
//                                                                    }
//
//                                                                    @Override
//                                                                    public void onError(ANError error) {
//
//
//                                                                        error.printStackTrace();
//                                                                    }
//                                                                });
//                                                    }
//                                                });
//
//
//                                            }
//                                        });


                                    }
                                }, 1000);

                            }
                        });
                        break;

                    }
                }
            }


            // deep copy the results so we can draw the current set while guessing the next set
            mOverlayRenderer.setBoxesFromAnotherThread(boxes);


        }
    };


    @Override
    protected void onStart() {
        super.onStart();
        createCameraPreviewHelper();
    }

    private String getIpAccess() {

        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
        final String formatedIpAddress = String.format(Locale.ENGLISH, "%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
        return "http://" + formatedIpAddress + ":";
    }

    @AfterPermissionGranted(0x123)
    private void createCameraPreviewHelper() {
        // ensure we have Camera permissions before proceeding
        final String[] requiredPerms = {Manifest.permission.CAMERA};
        if (EasyPermissions.hasPermissions(this, requiredPerms)) {
            // create the camera helper and nv21 conversion helpers here
            if (mCameraPreviewHelper == null) {
                mNV21ConversionHelper = new NV21ConversionHelper(this);
                mCameraPreviewHelper = new CameraPreviewHelper(this, findViewById(R.id.camera_view), mCameraPreviewCallbacks, true);
                getLifecycle().addObserver(mCameraPreviewHelper);
            }
        } else
            EasyPermissions.requestPermissions(this, "Please surrender the Camera",
                    0x123, requiredPerms);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
