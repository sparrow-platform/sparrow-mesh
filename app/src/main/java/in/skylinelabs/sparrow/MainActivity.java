package in.skylinelabs.sparrow;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.MenuItem;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    Intent mServiceIntent;
    private Sparrow sparrowService;
    Context ctx;
    public Context getCtx() {
        return ctx;
    }

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_home:
                    Intent intent = new Intent(MainActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish();
                    return true;

                case R.id.navigation_dashboard:

                    return true;
                case R.id.navigation_notifications:

                    return true;
            }
            return false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        BottomNavigationView navView = findViewById(R.id.nav_view);
        navView.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);
        Log.i(" Sparrow", "App started");


        /********************PERMISSIONS*******************/
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.BLUETOOTH)) {
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH},
                    1);
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.BLUETOOTH_ADMIN)) {
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_ADMIN},
                    2);
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.ACCESS_WIFI_STATE)) {
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_WIFI_STATE},
                    3);
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CHANGE_WIFI_STATE)) {
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CHANGE_WIFI_STATE},
                    4);
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)) {
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    5);
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.FOREGROUND_SERVICE)) {
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.FOREGROUND_SERVICE},
                    6);
        }

        /********************PERMISSIONS*******************/



        ctx = this;
        sparrowService = new Sparrow(getCtx());
        mServiceIntent = new Intent(getCtx(), sparrowService.getClass());
        if (!isMyServiceRunning(sparrowService.getClass())) {
//            startService(mServiceIntent);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(new Intent(ctx, Sparrow.class));
            } else {
                ctx.startService(new Intent(ctx, Sparrow.class));
            }
        }

    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        Log.i(" THIS IS NAME",serviceClass.getName());
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                Log.i (" Sparrow Service ", true+"");
                return true;
            }
        }
        Log.i (" Sparrow Service ", false+"");
        return false;
    }


    @Override
    protected void onDestroy() {
        stopService(mServiceIntent);
        Log.i(" Sparrow", "App destroyed");
        super.onDestroy();

    }



}
