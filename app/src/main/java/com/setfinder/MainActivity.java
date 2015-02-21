package com.setfinder;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.*;
import android.widget.ImageView;
import android.util.DisplayMetrics;
import com.setfinder.R;
import android.util.Log;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Button;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends Activity
{
    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    private CameraPreview m_Preview;
    private Camera m_Camera = null;
    private boolean m_PreviewMode = true;
    private Handler m_Handler = null;

    private BaseLoaderCallback  m_LoaderCallback = new BaseLoaderCallback(this)
    {
        @Override
        public void onManagerConnected(int status)
        {
            switch (status)
            {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(LOG_TAG, "OpenCV loaded successfully");
                    //m_OpenCvCameraView.enableView();
                    super.onManagerConnected(status);
                    break;
                }

                default:
                {
                    super.onManagerConnected(status);
                    break;
                }
            }
        }
    };

    public MainActivity()
    {
        m_Handler = new Handler(Looper.getMainLooper())
        {
            @Override
            public void handleMessage(Message inputMessage)
            {
                Bundle bundle = inputMessage.getData();
                Bitmap bitmap =  (Bitmap)bundle.getParcelable("Image");

                ImageView view = (ImageView)findViewById(R.id.setfinder_activity_surface_view);
                view.setImageBitmap(Bitmap.createScaledBitmap(
                        bitmap, 720, 480, false));
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
            ImageDecoder decoder = new ImageDecoder(data, size, m_Handler, getApplicationContext());
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

                ImageView view = (ImageView)findViewById(R.id.setfinder_activity_surface_view);
                view.setImageBitmap(null);

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
    @Override
    public void onResume()
    {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this.getApplicationContext(), m_LoaderCallback);
    }
}

// ----------------------------------------------------------------------

class ImageDecoder implements Runnable
{
    private static final String LOG_TAG = ImageDecoder.class.getSimpleName();
    private byte[] m_JpgData = null;
    private Camera.Size m_Size;
    private Handler m_Handler;
    private Context m_Context;
    private int m_RawHeight = 1920;
    private int m_RawWidth = 2560;
    private int m_ScreenHeight = 480;
    private int m_ScreenWidth = 800;

    public ImageDecoder(byte[] jpgData, Camera.Size size, Handler handler, Context context)
    {
        m_JpgData = jpgData;
        m_Size = size;
        m_Handler = handler;
        m_Context = context;

    }

    private File getOutputMediaFile()
    {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.
        File mediaStorageDir = new File(Environment.getExternalStorageDirectory()
                + "/Android/data/"
                + m_Context.getPackageName()
                + "/Files");

        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists())
        {
            if (! mediaStorageDir.mkdirs())
            {
                return null;
            }
        }
        // Create a media file name
        String timeStamp = new SimpleDateFormat("ddMMyyyy_HHmm").format(new Date());
        File mediaFile;
        String mImageName="MI_"+ timeStamp +".jpg";
        mediaFile = new File(mediaStorageDir.getPath() + File.separator + mImageName);
        return mediaFile;
    }

    Mat convertToGrayMat()
    {
        Bitmap srcBitmap = BitmapFactory.decodeByteArray(m_JpgData, 0, m_JpgData.length);

        Mat mat1 = new Mat(m_RawHeight, m_RawWidth, CvType.CV_8UC4, new Scalar(4));
        Mat mat2 = new Mat(m_RawHeight, m_RawWidth, CvType.CV_8UC1, new Scalar(1));

        Utils.bitmapToMat(srcBitmap, mat1);
        Imgproc.cvtColor(mat1, mat2, Imgproc.COLOR_RGBA2GRAY, 4);

        return mat2;
    }

    Bitmap convertToGrayBitmap()
    {
        Bitmap srcBitmap = BitmapFactory.decodeByteArray(m_JpgData, 0, m_JpgData.length);

        Mat mat1 = new Mat(m_RawHeight, m_RawWidth, CvType.CV_8UC4, new Scalar(4));
        Mat mat2 = new Mat(m_RawHeight, m_RawWidth, CvType.CV_8UC1, new Scalar(1));
        Mat mat3 = new Mat(m_ScreenHeight, m_ScreenWidth, CvType.CV_8UC1, new Scalar(1));

        Utils.bitmapToMat(srcBitmap, mat1);
        Imgproc.cvtColor(mat1, mat2, Imgproc.COLOR_RGBA2GRAY, 4);
        Imgproc.resize(mat2, mat3, new org.opencv.core.Size(480, 720));
        Bitmap dstBitmap = Bitmap.createBitmap(mat3.cols(), mat3.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mat3, dstBitmap);

        return dstBitmap;
    }

    Bitmap convertToBitmap(Mat mat)
    {
        Bitmap bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mat, bitmap);
        return bitmap;
    }

    @Override
    public void run()
    {
        int val = 20;
        Mat grayMat = convertToGrayMat();
        Mat edgeMat = new Mat(grayMat.size(), grayMat.type());
        Imgproc.blur(grayMat, grayMat, new org.opencv.core.Size(3, 3));
        Imgproc.Canny(grayMat, edgeMat, val, 3*val);

        Message msg = Message.obtain(m_Handler);
        Bundle bundle = new Bundle();

        bundle.putParcelable("Image", convertToBitmap(edgeMat));
        msg.setData(bundle);
        msg.sendToTarget();
    }
}

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

