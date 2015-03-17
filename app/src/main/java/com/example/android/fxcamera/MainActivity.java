package com.example.android.fxcamera;

import android.os.Bundle;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.Activity;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.hardware.Camera.Size;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.Toast;

public class MainActivity extends Activity {

    Preview mPreview;
    Camera mCamera;

    // For video
    private MediaRecorder mMediaRecorder;
    private boolean isRecording = false;
    ImageButton imageButtonVideo;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);

        mPreview = new Preview(this, (SurfaceView) findViewById(R.id.surfaceView));
        mPreview.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        ((RelativeLayout) findViewById(R.id.layout)).addView(mPreview);
        mPreview.setKeepScreenOn(true);

        ImageButton imageButtonCamera = (ImageButton) findViewById(R.id.imageButtonCamera);
        imageButtonCamera.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mCamera.takePicture(shutterCallback, rawCallback, jpegCallback);
            }
        });

        imageButtonVideo = (ImageButton) findViewById(R.id.imageButtonVideo);
        imageButtonVideo.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isRecording) {
                    stopVideoRecorder();
                    resetCam();
                } else {
                    new VideoPrepareTask().execute(null, null, null);
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCamera = CameraHelper.getDefaultCameraInstance();
        if (mCamera != null) {
            resetCam();
        } else {
            Toast.makeText(this, getString(R.string.camera_not_found), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onPause() {
        stopVideoRecorder();
        if (mCamera != null) {
            mCamera.stopPreview();
            mPreview.setCamera(null);
            releaseCamera();
        }
        super.onPause();
    }

    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }

    private void resetCam() {
        mCamera.startPreview();
        mPreview.setCamera(mCamera);
    }

    private void refreshGallery(File file) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(Uri.fromFile(file));
        sendBroadcast(mediaScanIntent);
    }

    ShutterCallback shutterCallback = new ShutterCallback() {
        public void onShutter() {
        }
    };

    PictureCallback rawCallback = new PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
        }
    };

    PictureCallback jpegCallback = new PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
            new SaveImageTask().execute(data);
            resetCam();
        }
    };

    private class SaveImageTask extends AsyncTask<byte[], Void, Void> {

        @Override
        protected Void doInBackground(byte[]... data) {
            try {
                File outFile = CameraHelper.getOutputMediaFile(CameraHelper.MEDIA_TYPE_IMAGE);
                FileOutputStream outStream = new FileOutputStream(outFile);
                outStream.write(data[0]);
                outStream.flush();
                outStream.close();
                refreshGallery(outFile);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }


    private boolean prepareVideoRecorder() {
        mMediaRecorder = new MediaRecorder();

        // Step 1: Unlock and set camera to MediaRecorder
        mCamera.unlock();
        mMediaRecorder.setCamera(mCamera);

        // Step 2: Set sources
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
        Size previewSize = mPreview.getPreviewSize();
        profile.videoFrameWidth = previewSize.width;
        profile.videoFrameHeight = previewSize.height;
        mMediaRecorder.setProfile(profile);

        // Step 4: Set output file
        File outFile = CameraHelper.getOutputMediaFile(CameraHelper.MEDIA_TYPE_VIDEO);
        mMediaRecorder.setOutputFile(outFile.toString());
        refreshGallery(outFile);

        // Step 5: Prepare configured MediaRecorder
        try {
            mMediaRecorder.prepare();
        } catch (IllegalStateException e) {
            releaseVideoRecorder();
            return false;
        } catch (IOException e) {
            releaseVideoRecorder();
            return false;
        }
        return true;
    }

    private void stopVideoRecorder() {
        if (mMediaRecorder != null) {
            mMediaRecorder.stop();  // stop the recording
        }
        releaseVideoRecorder(); // release the MediaRecorder object

        // inform the user that recording has stopped
        imageButtonVideo.setBackgroundResource(R.mipmap.video);
        isRecording = false;
    }

    private void releaseVideoRecorder() {
        if (mMediaRecorder != null) {
            // clear recorder configuration
            mMediaRecorder.reset();
            // release the recorder object
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }

    private class VideoPrepareTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... voids) {
            // initialize video camera
            if (prepareVideoRecorder()) {
                // Camera is available and unlocked, MediaRecorder is prepared,
                // now you can start recording
                mMediaRecorder.start();
                isRecording = true;
            } else {
                // prepare didn't work, release the camera
                releaseVideoRecorder();
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (!result) {
                MainActivity.this.finish();
            }
            // inform the user that recording has started
            imageButtonVideo.setBackgroundResource(R.mipmap.stop);
        }
    }
}