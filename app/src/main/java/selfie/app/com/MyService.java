package selfie.app.com;

import android.app.Service;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.camera2.CameraDevice;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import static android.content.ContentValues.TAG;

/**
 * Created by Satyam on 06/11/2017.
 */

public class MyService extends Service {

    int imageFormat;
    Camera mCamera;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand: Entered");
        ///
        CameraDevice.StateCallback scb = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice camera) {
                Log.i(TAG, "onOpened: Camera opened");
            }

            @Override
            public void onDisconnected(CameraDevice camera) {

            }

            @Override
            public void onError(CameraDevice camera, int error) {

            }
        };


        ///



        final String NowPictureFileName = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)+"new.jpg";
        final int PreviewSizeWidth = 200;
        final int PreviewSizeHeight = 500;
        Camera.PreviewCallback pc = new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                Camera.Parameters parameters = camera.getParameters();
                imageFormat = parameters.getPreviewFormat();

                if (imageFormat == ImageFormat.NV21)
                {
                    Rect rect = new Rect(0, 0, PreviewSizeWidth, PreviewSizeHeight);
                    YuvImage img = new YuvImage(data, ImageFormat.NV21, PreviewSizeWidth, PreviewSizeHeight, null);
                    OutputStream outStream = null;
                    File file = new File(NowPictureFileName);
                    try
                    {
                        outStream = new FileOutputStream(file);
                        img.compressToJpeg(rect, 100, outStream);
                        outStream.flush();
                        outStream.close();
                        Log.i(TAG, "onPreviewFrame: Saved");
                    }
                    catch (FileNotFoundException e)
                    {
                        e.printStackTrace();
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        };

        mCamera = Camera.open();
        mCamera.setPreviewCallback(pc);

        return super.onStartCommand(intent, flags, startId);
    }
}
