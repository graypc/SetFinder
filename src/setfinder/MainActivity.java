package setfinder;

import java.io.File;
import java.net.URI;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.highgui.Highgui;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.os.Build;
import android.provider.MediaStore;

public class MainActivity extends Activity implements CvCameraViewListener2
{
	public static final String LOG_TAG = "SetFinder";
	private CameraBridgeViewBase m_OpenCvCameraView;
	private static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 100;
	private Uri m_Uri = null;
	File m_Photo = null;
	ImageView m_ImageView;
	Bitmap m_Bitmap;
	
	private void displayImage(Bitmap bitmap)
	{
    	RelativeLayout layout  = (RelativeLayout)findViewById(R.id.OpenCvLayout);
    	ImageView imageView = new ImageView(this);
    	imageView.setImageBitmap(m_Bitmap);
    	imageView.setAdjustViewBounds(true);
	    
    	layout.addView(imageView);
    	setContentView(layout);
	}

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) 
    {
    	if (requestCode != CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) 
    		return;

    	if (resultCode != RESULT_OK) 
    		return;

    	Mat source = Highgui.imread(m_Photo.getAbsolutePath());
    	
    	if (source == null)
    		Log.d(LOG_TAG, "source == null");
    	else
    		Log.d(LOG_TAG, "source good");

    	this.getContentResolver().notifyChange(m_Uri, null);
    	ContentResolver cr = this.getContentResolver();
    	Bitmap bitmap = null;
    	try
        {
            m_Bitmap = android.provider.MediaStore.Images.Media.getBitmap(cr, m_Uri);
        }
        catch (Exception e)
        {
            Log.d(LOG_TAG, "Failed to load", e);
        }
    	
    	displayImage(Bitmap.createBitmap(bitmap, 0, 0, 2048, 1920));
    }

    private BaseLoaderCallback m_OpenCVLoaderCallback = new BaseLoaderCallback(this) 
    { 
    	@Override
    	public void onManagerConnected(int status) 
    	{ 
    		switch (status) 
    		{
    		case LoaderCallbackInterface.SUCCESS: 
    		{
    			Log.i(LOG_TAG, "OpenCV loaded successfully");
    			//mOpenCvCameraView.enableView(); 
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
    
    public void displayImage(Mat img)
    {
    	
    }

    public boolean grabImage(ImageView imageView)
    {
        this.getContentResolver().notifyChange(m_Uri, null);
        ContentResolver cr = this.getContentResolver();
        Bitmap bitmap;
        try
        {
            bitmap = android.provider.MediaStore.Images.Media.getBitmap(cr, m_Uri);
            imageView.setImageBitmap(bitmap);
            return true;
        }
        catch (Exception e)
        {
            Log.d(LOG_TAG, "Failed to load", e);
            return false;
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_8, this, m_OpenCVLoaderCallback);
    }
    
	@Override
	protected void onCreate(Bundle savedInstanceState) 
	{
		Log.i(LOG_TAG, "onCreate()");
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.layout);
		m_ImageView = (ImageView)findViewById(R.id.imageView);
		
		
		//m_OpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.OpenCvView); 
		//m_OpenCvCameraView.setVisibility(SurfaceView.VISIBLE); 
		//m_OpenCvCameraView.setCvCameraViewListener(this);
		//System.loadLibrary(Core.NATIVE_LIBRARY_NAME);	
		Button takePictureButton = (Button)findViewById(R.id.take_picture_button);
		m_ImageView = new ImageView(this);
		
		takePictureButton.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				onTakePictureButtonClicked();
			}            
		});
		
	}

	public void onTakePictureButtonClicked()
	{
		Log.i(LOG_TAG, "onTakePictureButtonClicked()");

	    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
	    try
	    {
	    	m_Photo = new File("/sdcard/picture.jpg");
	    }
	    catch(Exception e)
	    {
	        Log.v(LOG_TAG, "Can't create file to take picture!");
	        Log.v(LOG_TAG, e.getMessage());
	        e.printStackTrace();
	        return;
	    }

	    m_Uri = Uri.fromFile(m_Photo);
	    intent.putExtra(MediaStore.EXTRA_OUTPUT, m_Uri);

	    //start camera intent
	    startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
	
		//OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_8, this, mLoaderCallback);
	}

	@Override
	public void onPause() 
	{
		super.onPause();
		if (m_OpenCvCameraView != null)
			m_OpenCvCameraView.disableView();
	}

	public void onDestroy() 
	{ 
		super.onDestroy();
		if (m_OpenCvCameraView != null)
			m_OpenCvCameraView.disableView();
	}

	@Override
	public void onCameraViewStarted(int width, int height) 
	{
		Log.i(LOG_TAG, "onCameraViewStarted()");
	}

	@Override
	public void onCameraViewStopped() 
	{
		Log.i(LOG_TAG, "onCameraViewStopped()");
	}

	@Override
	public Mat onCameraFrame(CvCameraViewFrame inputFrame) 
	{
		Log.i(LOG_TAG, "onCameraFrame()");
		return inputFrame.rgba();
	}
}
