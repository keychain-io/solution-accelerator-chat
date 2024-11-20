package io.keychain.chat.views.qrcode;

import static android.view.View.VISIBLE;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.google.zxing.Result;

import io.keychain.chat.R;
import io.keychain.mobile.BaseActivity;
import me.dm7.barcodescanner.zxing.ZXingScannerView;

public class QrCodeActivity extends BaseActivity implements ZXingScannerView.ResultHandler {
    private static final String TAG = "QrCodeActivity";
    private ZXingScannerView mScannerView = null;
    private static final int REQUEST_CAMERA = 0;
    public static final String JSON_EXTRA = "QRJSON";

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_scanner);
    }

    public void onStart() {
        Log.d(TAG, "onStart()");
        super.onStart();

        mScannerView = new ZXingScannerView(this);

        int permissionCheck = this.checkSelfPermission(Manifest.permission.CAMERA);
        if (permissionCheck == PackageManager.PERMISSION_DENIED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_CAMERA);
        }

        ViewGroup contentFrame = findViewById(R.id.content_frame);
        contentFrame.addView(mScannerView);
        contentFrame.setVisibility(VISIBLE);

        mScannerView.setResultHandler(this);
        mScannerView.startCamera();
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause()");
        super.onPause();
        if (mScannerView == null)
            return;
        mScannerView.stopCamera();
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume()");
        super.onResume();
        if (mScannerView == null)
            return;
        mScannerView.startCamera();
        mScannerView.setResultHandler(this);
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop()");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy()");
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mScannerView.startCamera();
            } else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent();
                intent.putExtra(JSON_EXTRA, "");
                setResult(Activity.RESULT_CANCELED, intent);
                finish();
            }
        }
    }

    @Override
    public void handleResult(Result rawResult) {
        // Do something with the result here
        Log.i(TAG, "Got QRCode data: " + rawResult.getText());
        Intent intent = new Intent();
        try {
            String pairInfo = rawResult.getText();
            Log.i(TAG, "Decoded QRCode data: " + pairInfo);
            intent.putExtra(JSON_EXTRA, pairInfo);
            setResult(Activity.RESULT_OK, intent);
        } catch (Exception e) {
            Log.e(TAG, "Exception creating pair info: " + e.getMessage());
            intent.putExtra(JSON_EXTRA, "");
            setResult(Activity.RESULT_CANCELED, intent);
        } finally {
            mScannerView.stopCamera();
            mScannerView = null;
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}