package in.skylinelabs.sparrow;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.Timer;
import java.util.TimerTask;

public class Sparrow extends Service {
    public int counter=0;
    public Sparrow(Context applicationContext) {
        super();
        Log.i("Sparrow Service ENTER", "Service started");
        //Service started here
    }

    public Sparrow() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        startTimer();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i("Sparrow Service EXIT", "Service Destroyed");
        Intent broadcastIntent = new Intent(this, SparrowRestartBroadcastReceiver.class);
        sendBroadcast(broadcastIntent);
        stoptimertask();
        //Service Destroyed here
    }




    private Timer timer;
    private TimerTask timerTask;
    long oldTime=0;
    public void startTimer() {
        //set a new Timer
        timer = new Timer();

        //initialize the TimerTask's job
        initializeTimerTask();

        //schedule the timer, to wake up every 1 second
        timer.schedule(timerTask, 1000, 1000); //
    }

    /**
     * it sets the timer to print the counter every x seconds
     */
    public void initializeTimerTask() {
        timerTask = new TimerTask() {
            public void run() {
                Log.i("in timer", "in timer ++++  "+ (counter++));
            }
        };
    }

    /**
     * not needed
     */
    public void stoptimertask() {
        //stop the timer, if it's not already null
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }




    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}