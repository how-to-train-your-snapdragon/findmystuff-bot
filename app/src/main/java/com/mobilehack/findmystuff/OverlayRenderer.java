package com.mobilehack.findmystuff;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import io.fotoapparat.Fotoapparat;


/**
 * Renderer for the image detection box
 * @author Thomas Binu
 * @author Anitha Ramaswamy
 * @author Ashuthosh Giri
 */
public class OverlayRenderer extends View {

    private ReentrantLock mLock = new ReentrantLock();
    private ArrayList<Box> mBoxes = new ArrayList<>();
    private boolean mHasResults;
    private final Map<Integer, Integer> mColorIdxMap = new ArrayMap<>();
    private Paint mOutlinePaint = new Paint();
    private Paint mCenterPaint = new Paint();
    private Paint mTextPaint = new Paint();
    private float mBoxScoreThreshold = 0.4f;
    private String searchLabel;

    private ImageView screenshotView;
    AppCompatActivity context;
    Fotoapparat fotoapparat;


//    public void setScreenshotView(AppCompatActivity appCompatActivity, ImageView screenshotView, Fotoapparat fotoapparat) {
//        this.screenshotView = screenshotView;
//        this.context = appCompatActivity;
//        this.fotoapparat = fotoapparat;
//    }

    private TextView statusView;

    public OverlayRenderer(Context context) {
        super(context);
        init();
    }

    public OverlayRenderer(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public OverlayRenderer(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public void setGeometryOfTheUnderlyingCameraFeed() {
        // TODO...
    }

    public void setNextBoxScoreThreshold(float scoreThreshold) {
        mBoxScoreThreshold = scoreThreshold;
    }

    public void setStatusView(TextView statusView) {
        this.statusView = statusView;
    }

    public float getBoxScoreThreshold() {
        return mBoxScoreThreshold;
    }

    public void setSearchLabel(String searchLabel) {
        this.searchLabel = searchLabel;
    }

    public void setBoxesFromAnotherThread(ArrayList<Box> nextBoxes) {
        mLock.lock();
        if (nextBoxes == null) {
            mHasResults = false;
            for (Box box : mBoxes)
                box.type_score = 0;
        } else {
            mHasResults = true;
            for (int i = 0; i < nextBoxes.size(); i++) {
                final Box otherBox = nextBoxes.get(i);
                if (i >= mBoxes.size())
                    mBoxes.add(new Box());
                otherBox.copyTo(mBoxes.get(i));
            }
        }
        mLock.unlock();
        postInvalidate();
    }

//    public int getCurrentBoxesCount() {
//        return mBoxes.size();
//    }


    private void init() {
        mOutlinePaint.setStyle(Paint.Style.STROKE);
        mOutlinePaint.setStrokeWidth(6);
        mCenterPaint.setColor(Color.RED);
        mTextPaint.setStyle(Paint.Style.FILL);
        mTextPaint.setTextSize(32);
        mTextPaint.setColor(Color.WHITE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int viewWidth = getWidth();
        final int viewHeight = getHeight();
        // in case there were no results, just draw an X on screen.. totally optional
        if (!mHasResults) {
            mOutlinePaint.setColor(Color.WHITE);
            canvas.drawLine(viewWidth, 0, 0, viewHeight, mOutlinePaint);
            canvas.drawLine(0, 0, viewWidth, viewHeight, mOutlinePaint);
            return;
        }
        final int virtualSize = Math.max(viewHeight, viewHeight);
        final int virtualDx = (virtualSize - viewWidth) / 2;
        final int virtualDy = (virtualSize - viewHeight) / 2;
        mLock.lock();
        for (int i = 0; i < mBoxes.size(); i++) {
            final Box box = mBoxes.get(i);
            // skip rendering below the threshold
            if (box.type_score < mBoxScoreThreshold)
                break;
            // compute the final geometry
            float bl = virtualSize * box.left - virtualDx;
            float bt = virtualSize * box.top - virtualDy;
            float br = virtualSize * box.right - virtualDx;
            float bb = virtualSize * box.bottom - virtualDy;
            float bcx = (bl + br) / 2;
            float bcy = (bt + bb) / 2;
            // draw the center
            mCenterPaint.setColor(colorForIndex(box.type_id));
            canvas.drawRect(bcx - 4, bcy - 4, bcx + 4, bcy + 4, mCenterPaint);
            // draw the text
            String textLabel = (box.type_name != null && !box.type_name.isEmpty()) ? box.type_name : String.valueOf(box.type_id + 2);
            //textLabel += ", " + box.type_score;
            canvas.drawText(textLabel, bl + 10, bt + 30, mTextPaint);


            // draw the box
            mOutlinePaint.setColor(colorForIndex(box.type_id));
            canvas.drawRect(bl, bt, br, bb, mOutlinePaint);
        }
        mLock.unlock();
    }

    private int colorForIndex(int index) {
        // create color on the fly if missing
        if (!mColorIdxMap.containsKey(index)) {
            float[] hsv = {(float) (Math.random() * 360), (float) (0.5 + Math.random() * 0.5), (float) (0.5 + Math.random() * 0.5)};
            mColorIdxMap.put(index, Color.HSVToColor(hsv));
        }
        return mColorIdxMap.get(index);
    }
}
