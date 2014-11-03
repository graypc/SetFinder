/*
 * Copyright (C) 2010,2011,2012 Samuel Audet
 *
 * FacePreview - A fusion of OpenCV's facedetect and Android's CameraPreview samples,
 *               with JavaCV + JavaCPP as the glue in between.
 *
 * This file was based on CameraPreview.java that came with the Samples for 
 * Android SDK API 8, revision 1 and contained the following copyright notice:
 *
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 * IMPORTANT - Make sure the AndroidManifest.xml file looks like this:
 *
 * <?xml version="1.0" encoding="utf-8"?>
 * <manifest xmlns:android="http://schemas.android.com/apk/res/android"
 *     package="org.bytedeco.javacv.facepreview"
 *     android:versionCode="1"
 *     android:versionName="1.0" >
 *     <uses-sdk android:minSdkVersion="4" />
 *     <uses-permission android:name="android.permission.CAMERA" />
 *     <uses-feature android:name="android.hardware.camera" />
 *     <application android:label="@string/app_name">
 *         <activity
 *             android:name="FacePreview"
 *             android:label="@string/app_name"
 *             android:screenOrientation="landscape">
 *             <intent-filter>
 *                 <action android:name="android.intent.action.MAIN" />
 *                 <category android:name="android.intent.category.LAUNCHER" />
 *             </intent-filter>
 *         </activity>
 *     </application>
 * </manifest>
 */

package com.setfinder;

import android.os.Looper;
import com.setfinder.R;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Button;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.util.List;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.opencv_objdetect;

import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_objdetect.*;

// ----------------------------------------------------------------------

public class MainActivity extends Activity
{
    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    private ImageProcessor m_ImageProcessor;
    private CameraPreview m_Preview;
    private Camera m_Camera = null;
    private boolean m_PreviewMode = true;
    private Handler m_Handler = null;

    public MainActivity()
    {
        m_Handler = new Handler(Looper.getMainLooper())
        {
            @Override
            public void handleMessage(Message inputMessage)
            {
                String msg = (String)inputMessage.obj;

                Bundle bundle = inputMessage.getData();
                Integer size = (Integer)bundle.get("Size");
                Log.d(LOG_TAG, "handleMessage().  msg[" + Integer.toString(size) + "]");
            }
        };
    }

    private Camera.PictureCallback m_PictureCallback = new Camera.PictureCallback()
    {
        @Override
        public void onPictureTaken(byte[] data, Camera camera)
        {
            Log.d(LOG_TAG, "onPictureTaken() data.length[" + Integer.toString(data.length) + "]");

            Camera.Size size = camera.getParameters().getPreviewSize();
            ImageDecoder decoder = new ImageDecoder(data, size, m_Handler);
            decoder.run();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) 
	{
        // Hide the window title.
        //requestWindowFeature(Window.FEATURE_NO_TITLE);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        m_Camera = Camera.open();

        m_ImageProcessor =          new ImageProcessor(this);
        m_Preview =                 new CameraPreview(this, m_Camera);

        FrameLayout layout =        (FrameLayout)findViewById(R.id.frame_layout);
        Button button =  (Button)findViewById(R.id.button);

        button.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (m_Camera == null)
                    return;

                // Currently in preview mode.  Take picture.
                if (m_PreviewMode)
                {
                    m_Camera.takePicture(null, null, m_PictureCallback);
                    m_PreviewMode = false;
                    return;
                }

                // Picture has already been taken.  Start preview mode.
                m_Camera.startPreview();
                m_PreviewMode = true;
            }
        });

        layout.addView(m_Preview);
    }
}

// ----------------------------------------------------------------------

class ImageDecoder implements Runnable
{
    private static final String LOG_TAG = ImageDecoder.class.getSimpleName();
    private byte[] m_JpgData = null;
    private CvMemStorage m_Storage;
    private IplImage m_RawImage = null;
    private Camera.Size m_Size;
    private Handler m_Handler;

    public ImageDecoder(byte[] jpgData, Camera.Size size, Handler handler)
    {
        m_JpgData = jpgData;
        m_Size = size;
        m_Handler = handler;
    }

    @Override
    public void run()
    {
        Log.d(LOG_TAG, "run().");
        Loader.load(opencv_objdetect.class);

        //TODO.  Failing to load shared libs with one of these calls
        IplImage grayImage = IplImage.create(m_Size.width, m_Size.height, IPL_DEPTH_8U, 1);
        BytePointer rawImagePointer = new BytePointer(m_JpgData);
        cvSetData(grayImage, rawImagePointer, m_Size.width * 3);
        Message msg = Message.obtain(m_Handler);
        Bundle bundle = new Bundle();
        bundle.putInt("Size", grayImage.arraySize());
        msg.setData(bundle);
        msg.sendToTarget();
    }
}

class ImageProcessor extends View
{
    private static final String LOG_TAG = ImageProcessor.class.getSimpleName();
    public static final int SUBSAMPLING_FACTOR = 4;

    private IplImage grayImage;
    private CvHaarClassifierCascade classifier;
    private CvMemStorage storage;
    private CvSeq faces;

    public ImageProcessor(MainActivity context) //throws IOException
	{
        super(context);

        /*
        // Load the classifier file from Java resources.
        File classifierFile = Loader.extractResource(getClass(),
            "/org/bytedeco/javacv/facepreview/haarcascade_frontalface_alt.xml",
            context.getCacheDir(), "classifier", ".xml");
        if (classifierFile == null || classifierFile.length() <= 0) {
            throw new IOException("Could not extract the classifier file from Java resource.");
        }
        */

        // Preload the opencv_objdetect module to work around a known bug.
        Loader.load(opencv_objdetect.class);
        /*
        classifier = new CvHaarClassifierCascade(cvLoad(classifierFile.getAbsolutePath()));
        classifierFile.delete();
        if (classifier.isNull()) {
            throw new IOException("Could not load the classifier file.");
        }
        */
        storage = CvMemStorage.create();
    }

    public void onPreviewFrame(final byte[] data, final Camera camera)
	{
        Log.d(LOG_TAG, "onPreviewFrame");

        /*
        try 
		{
            Camera.Size size = camera.getParameters().getPreviewSize();
            processImage(data, size.width, size.height);
            camera.addCallbackBuffer(data);
        } 
		catch (RuntimeException e) 
		{
            // The camera has probably just been released, ignore.
        }
        */
    }

    /*
    protected void processImage(byte[] data, int width, int height) 
	{
        // First, downsample our image and convert it into a grayscale IplImage
        int f = SUBSAMPLING_FACTOR;

        if (grayImage == null || grayImage.width() != width/f || grayImage.height() != height/f) 
		{
            grayImage = IplImage.create(width/f, height/f, IPL_DEPTH_8U, 1);
        }

        int imageWidth  = grayImage.width();
        int imageHeight = grayImage.height();
        int dataStride = f*width;
        int imageStride = grayImage.widthStep();
        ByteBuffer imageBuffer = grayImage.getByteBuffer();

        for (int y = 0; y < imageHeight; y++) 
		{
            int dataLine = y*dataStride;
            int imageLine = y*imageStride;
            for (int x = 0; x < imageWidth; x++) 
			{
                imageBuffer.put(imageLine + x, data[dataLine + f*x]);
            }
        }

        cvClearMemStorage(storage);
        faces = cvHaarDetectObjects(grayImage, classifier, storage, 1.1, 3, CV_HAAR_DO_CANNY_PRUNING);
        postInvalidate();
    }
    */

    @Override
    protected void onDraw(Canvas canvas) 
	{
        /*
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setTextSize(20);

        String s = "FacePreview - This side up.";
        float textWidth = paint.measureText(s);
        canvas.drawText(s, (getWidth()-textWidth)/2, 20, paint);

        if (faces != null) {
            paint.setStrokeWidth(2);
            paint.setStyle(Paint.Style.STROKE);
            float scaleX = (float)getWidth()/grayImage.width();
            float scaleY = (float)getHeight()/grayImage.height();
            int total = faces.total();
            for (int i = 0; i < total; i++) {
                CvRect r = new CvRect(cvGetSeqElem(faces, i));
                int x = r.x(), y = r.y(), w = r.width(), h = r.height();
                canvas.drawRect(x*scaleX, y*scaleY, (x+w)*scaleX, (y+h)*scaleY, paint);
            }
        }
        */
    }
}

// ----------------------------------------------------------------------

class CameraPreview extends SurfaceView implements SurfaceHolder.Callback
{
    private SurfaceHolder m_Holder;
    private Camera m_Camera;

    private static final String LOG_TAG = CameraPreview.class.getSimpleName();

    public CameraPreview(Context context, Camera camera)
    {
        super(context);
        m_Camera = camera;

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        m_Holder = getHolder();
        m_Holder.addCallback(this);

        // deprecated setting, but required on Android versions prior to 3.0
        m_Holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public void surfaceCreated(SurfaceHolder holder)
    {
        // The Surface has been created, now tell the camera where to draw the preview.
        try
        {
            m_Camera.setPreviewDisplay(holder);
            m_Camera.startPreview();
        }
        catch (IOException e)
        {
            Log.d(LOG_TAG, "surfaceCreated().  ERROR.  Setting camera preview: " + e.getMessage());
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder)
    {
        // empty. Take care of releasing the Camera preview in your activity.
        m_Camera.stopPreview();
        m_Camera.release();
        m_Camera = null;
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h)
    {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        if (m_Holder.getSurface() == null)
        {
            // preview surface does not exist
            return;
        }

        // stop preview before making changes
        try
        {
            m_Camera.stopPreview();
        }
        catch (Exception e)
        {
            // ignore: tried to stop a non-existent preview
        }

        // set preview size and make any resize, rotate or
        // reformatting changes here

        // start preview with new settings
        try
        {
            m_Camera.setPreviewDisplay(m_Holder);
            m_Camera.startPreview();

        }
        catch (Exception e)
        {
            Log.d(LOG_TAG, "surfaceChanged().  ERROR.  Starting camera preview: " + e.getMessage());
        }
    }
}

