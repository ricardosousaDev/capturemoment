package com.ricardosousadev.capturemoment;

import android.Manifest;
import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.TimePicker;
import android.widget.Toast;

import com.ricardosousadev.capturemoment.services.TakePhotoService;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, Handler.Callback, TimePickerDialog.OnTimeSetListener {
    private String TAG = "CameraPreview";
    private SurfaceView mCameraSurfaceView;
    static final int PERMISSIONS_REQUEST_CAMERA = 100;
    static final int PERMISSIONS_REQUEST_STORAGE = 200;
    private static final int MSG_CAMERA_OPENED = 1;
    private static final int MSG_SURFACE_READY = 2;
    private final Handler mHandler = new Handler(this);
    SurfaceHolder mSurfaceHolder;
    CameraManager mCameraManager;
    String[] mCameraIDsList;
    CameraDevice.StateCallback mCameraStateCB;
    CameraDevice mCameraDevice;
    CameraCaptureSession mCaptureSession;
    Button mCancelService;
    TimePickerDialog mTimePickerDialog;
    boolean mSurfaceCreated = true;
    boolean mIsCameraConfigured = false;
    private Surface mCameraSurface = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mCameraSurfaceView = (SurfaceView)findViewById(R.id.camera_preview);
        mSurfaceHolder = mCameraSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);
        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        mCancelService = (Button)findViewById(R.id.cancel_running_service_button);

        Calendar calendar = Calendar.getInstance();
        mTimePickerDialog = new TimePickerDialog(this, this, calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE), true);

        try {
            mCameraIDsList = this.mCameraManager.getCameraIdList();
            for (String id : mCameraIDsList) {
                Log.v(TAG, "CameraID: " + id);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        mCameraStateCB = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice camera) {
                Log.e(TAG, "onOpened");

                mCameraDevice = camera;
                mHandler.sendEmptyMessage(MSG_CAMERA_OPENED);
            }

            @Override
            public void onDisconnected(CameraDevice camera) {
                Log.e(TAG, "onDisconnected");
            }

            @Override
            public void onError(CameraDevice camera, int error) {
                Log.e(TAG, "onError");
            }
        };

        mCameraSurfaceView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkStoragePermission()) {
                    mTimePickerDialog.show();
                } else {
                    warnAboutStoragePermissionIfNeeded();
                }
            }
        });

        checkStoragePermission();
    }

    @Override
    protected void onStart() {
        super.onStart();

        checkPermissionAndStartCamera();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopCamera();
    }

    private void stopCamera() {
        try {
            if (mCaptureSession != null) {
                mCaptureSession.stopRepeating();
                mCaptureSession.close();
                mCaptureSession = null;
            }

            mIsCameraConfigured = false;
        } catch (final CameraAccessException e) {
            // Doesn't matter, cloising device anyway
            e.printStackTrace();
        } catch (final IllegalStateException e2) {
            // Doesn't matter, cloising device anyway
            e2.printStackTrace();
        } finally {
            if (mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
                mCaptureSession = null;
            }
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_CAMERA_OPENED:
            case MSG_SURFACE_READY:
                // if both surface is created and camera device is opened
                // - ready to set up preview and other things
                if (mSurfaceCreated && (mCameraDevice != null)
                        && !mIsCameraConfigured) {
                    configureCamera();
                }
                break;
        }

        return true;
    }

    private void configureCamera() {
        // prepare list of surfaces to be used in capture requests
        List<Surface> sfl = new ArrayList<>();

        sfl.add(mCameraSurface); // surface for viewfinder preview

        // configure camera with all the surfaces to be ever used
        try {
            mCameraDevice.createCaptureSession(sfl, new CaptureSessionListener(), null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        mIsCameraConfigured = true;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case PERMISSIONS_REQUEST_CAMERA:
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        mCameraManager.openCamera(mCameraIDsList[1], mCameraStateCB, new Handler());
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case PERMISSIONS_REQUEST_STORAGE:
                warnAboutStoragePermissionIfNeeded();
                break;
        }
    }

    private void checkPermissionAndStartCamera() {
        //requesting permission
        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {

            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSIONS_REQUEST_CAMERA);
                Log.e(TAG, "request permission");
            }
        } else {
            Log.e(TAG, "PERMISSION_ALREADY_GRANTED");
            try {
                mCameraManager.openCamera(mCameraIDsList[0], mCameraStateCB, new Handler());
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.v(TAG,"Permission is granted");
                return true;
            } else {

                Log.v(TAG,"Permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return false;
            }
        }
        else { //permission is automatically granted on sdk<23 upon installation
            Log.v(TAG,"Permission is granted");
            return true;
        }
    }

    private void warnAboutStoragePermissionIfNeeded() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            Toast.makeText(this, "Please enable storage permissions to continue.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mCameraSurface = holder.getSurface();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mCameraSurface = holder.getSurface();
        mSurfaceCreated = true;
        mHandler.sendEmptyMessage(MSG_SURFACE_READY);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mSurfaceCreated = false;
    }

    private class CaptureSessionListener extends CameraCaptureSession.StateCallback {
        @Override
        public void onConfigureFailed(final CameraCaptureSession session) {
            Log.d(TAG, "CaptureSessionConfigure failed");
        }

        @Override
        public void onConfigured(final CameraCaptureSession session) {
            Log.d(TAG, "CaptureSessionConfigure onConfigured");
            mCaptureSession = session;

            try {
                CaptureRequest.Builder previewRequestBuilder = mCameraDevice
                        .createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                previewRequestBuilder.addTarget(mCameraSurface);
                mCaptureSession.setRepeatingRequest(previewRequestBuilder.build(),
                        null, null);
            } catch (CameraAccessException e) {
                Log.d(TAG, "setting up preview failed");
                e.printStackTrace();
            }
        }
    }

    public void cancelRunningService(View view) {
        mCancelService.setVisibility(View.GONE);
        checkPermissionAndStartCamera();

        AlarmManager alarmManager =(AlarmManager)getSystemService(Context.ALARM_SERVICE);
        Intent serviceIntent = new Intent(getApplicationContext(), TakePhotoService.class);
        PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), 0, serviceIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        alarmManager.cancel(pendingIntent);
    }

    @Override
    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        stopCamera();
        mCancelService.setVisibility(View.VISIBLE);

        Calendar calendar = Calendar.getInstance();

        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);

        AlarmManager alarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
        Intent serviceIntent = new Intent(getApplicationContext(), TakePhotoService.class);
        PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), 0, serviceIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pendingIntent);

        mCameraSurfaceView.setVisibility(View.GONE);
        findViewById(R.id.instructions_parent_view).setVisibility(View.GONE);
        findViewById(R.id.success_view).setVisibility(View.VISIBLE);
    }
}
