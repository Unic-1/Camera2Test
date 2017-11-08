package selfie.app.com.tutorial;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import selfie.app.com.R;

public class Camear2TutorialActivity extends AppCompatActivity {

    private final int CAMERA_REQUEST_CODE = 0;
    private final int WRITE_STORAGE_REQUEST_CODE = 1;
    private TextureView mTextureView;
    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            setupCamera(width, height);
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    private CameraDevice mCameraDevice;
    private CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            mCameraDevice = camera;
            startPreview();
            //Toast.makeText(getApplicationContext(), "Camera is started!", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
        }
    };
    private HandlerThread mBackgroundHandlerThread;
    private Handler mBackgroundHandler;
    private String mCameraId;
    private Size mPreviewSize;
    private CaptureRequest.Builder mCaptureRequestBuilder;

    private ImageButton mRecordImageButton;
    private boolean mIsRecording = false;

    private File mVideoDirectoryPath;
    private String mVideoFileName;
    private static SparseIntArray ORIENTATION = new SparseIntArray();
    static {
        ORIENTATION.append(Surface.ROTATION_0, 0);
        ORIENTATION.append(Surface.ROTATION_90, 90);
        ORIENTATION.append(Surface.ROTATION_180, 180);
        ORIENTATION.append(Surface.ROTATION_270, 270);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camear2_tutorial);

        mTextureView = (TextureView) findViewById(R.id.textureView);
        mRecordImageButton = (ImageButton) findViewById(R.id.videoButton);

        mRecordImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mIsRecording) {
                    mIsRecording = false;
                    mRecordImageButton.setImageResource(R.mipmap.video_away_button);
                } else {
                    checkStoragePermission();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        startBackgroundThread();

        if (mTextureView.isAvailable()) {
            setupCamera(mTextureView.getWidth(), mTextureView.getHeight());
            openCamera();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    protected void onPause() {
        closeCamera();

        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        View decorView = getWindow().getDecorView();

        if (hasFocus) {
            // Hides the navbar and makes the app full screen
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == CAMERA_REQUEST_CODE) {
            if(grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(), "Camera permission is required!", Toast.LENGTH_SHORT).show();
            }
        } else if(requestCode == WRITE_STORAGE_REQUEST_CODE) {
            if(grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Video needs storage permission to store", Toast.LENGTH_SHORT).show();
            } else {
                mIsRecording = true;
                mRecordImageButton.setImageResource(R.mipmap.video_online_button);
                try {
                    createFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Toast.makeText(this, "Permission sucessfully granted", Toast.LENGTH_SHORT).show();
            }
        }

    }

    // Setting up the camera
    private void setupCamera(int width, int height) {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                // Setting the orientation of the screen based on its current orientation
                int deviceRotation = getWindowManager().getDefaultDisplay().getRotation();
                int totalRotation = sensorToDeviceRotation(cameraCharacteristics, deviceRotation);
                boolean swapRotation = totalRotation == 90 || totalRotation == 270;
                int swapWidth = width;
                int swapHeight = height;
                if(swapRotation) {
                    swapWidth = height;
                    swapHeight = width;
                }

                // Getting the preview size
                mPreviewSize = getPreviewSize(map.getOutputSizes(SurfaceTexture.class), swapWidth, swapHeight);

                // Storing camera id
                mCameraId = cameraId;
                Log.i("setupCamera", "id: "+mCameraId);
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // Starts the camera
    private void openCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);
                } else {
                    // Checks if the user has clicked "Deny" before
                    if(shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                        Toast.makeText(this, "Camera permission is required!", Toast.LENGTH_SHORT).show();
                    }
                    requestPermissions(new String[] {Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
                }

            } else {
                cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // Start's the camera preview
    private void startPreview() {
        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);

        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(previewSurface);

            mCameraDevice.createCaptureSession(
                    Arrays.asList(previewSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            try {
                                // TODO: 07/11/2017 Process the frames here inside CaptureCallback
                                session.setRepeatingRequest(mCaptureRequestBuilder.build(),
                                        new CameraCaptureSession.CaptureCallback() {
                                            @Override
                                            public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                                                super.onCaptureStarted(session, request, timestamp, frameNumber);
                                            }

                                            @Override
                                            public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {
                                                super.onCaptureProgressed(session, request, partialResult);
                                            }

                                            @Override
                                            public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                                                super.onCaptureCompleted(session, request, result);
                                            }

                                            @Override
                                            public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
                                                super.onCaptureFailed(session, request, failure);
                                            }

                                            @Override
                                            public void onCaptureSequenceCompleted(CameraCaptureSession session, int sequenceId, long frameNumber) {
                                                super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
                                            }

                                            @Override
                                            public void onCaptureSequenceAborted(CameraCaptureSession session, int sequenceId) {
                                                super.onCaptureSequenceAborted(session, sequenceId);
                                            }

                                            @Override
                                            public void onCaptureBufferLost(CameraCaptureSession session, CaptureRequest request, Surface target, long frameNumber) {
                                                super.onCaptureBufferLost(session, request, target, frameNumber);
                                            }
                                        }, mBackgroundHandler);
                                /*session.setRepeatingRequest(mCaptureRequestBuilder.build(),
                                        null, mBackgroundHandler);*/
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Toast.makeText(getApplicationContext(),
                                    "Unable to setup camera preview", Toast.LENGTH_SHORT).show();
                        }
                    },
                    null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    // Closes the camera and free's the resources
    private void closeCamera() {
        mCameraDevice.close();
        mCameraDevice = null;
    }

    // Start's the background thread for the camera
    private void startBackgroundThread() {
        mBackgroundHandlerThread = new HandlerThread("Camera2Thread");
        mBackgroundHandlerThread.start();
        mBackgroundHandler = new Handler(mBackgroundHandlerThread.getLooper());
    }

    // Stops the background thread
    private void stopBackgroundThread() {
        mBackgroundHandlerThread.quitSafely();
        try {
            mBackgroundHandlerThread.join();
            mBackgroundHandlerThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    // Calculates the angle of screen orientation
    private int sensorToDeviceRotation(CameraCharacteristics cameraCharacteristics, int deviceRotation) {
        int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        deviceRotation = ORIENTATION.get(deviceRotation);
        return (sensorOrientation + deviceRotation + 360) % 360;
    }

    // Used by Collection for comparing sizes
    private static class CompareSizes implements Comparator<Size> {

        @Override
        public int compare(Size o1, Size o2) {
            return Long.signum((long) o1.getHeight() * o1.getWidth()/
                    (long)o2.getHeight() *  o2.getWidth());
        }
    }

    // Searches and returns the optimal preview size
    private Size getPreviewSize(Size[] choices, int width, int height) {
        List<Size> bestMatch = new ArrayList<>();
        for(Size choice: choices) {
            // Checking the aspect ratio
            if(choice.getHeight() == choice.getWidth() * height / width
                    && choice.getWidth() >= width
                    && choice.getHeight() >= height) {
                bestMatch.add(choice);
            }
        }
        if(bestMatch.size() > 0) {
            return Collections.min(bestMatch, new CompareSizes());
        } else {
            return choices[0];
        }
    }

    private void createFolder() {
        File directoryPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        mVideoDirectoryPath = new File(directoryPath, "camera2Video");
        if(!mVideoDirectoryPath.exists()) {
            mVideoDirectoryPath.mkdirs();
        }
    }

    private File createFile() throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String prepend = "VIDEO_"+timestamp;
        File videoFile = File.createTempFile(prepend, ".mp4", mVideoDirectoryPath);
        mVideoFileName = videoFile.getAbsolutePath();
        return videoFile;
    }

    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                mIsRecording = true;
                mRecordImageButton.setImageResource(R.mipmap.video_online_button);
                try {
                    createFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                if(shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    Toast.makeText(this, "Video needs to be stored.", Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_STORAGE_REQUEST_CODE);
            }
        } else {
            mIsRecording = true;
            mRecordImageButton.setImageResource(R.mipmap.video_online_button);
            try {
                createFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
