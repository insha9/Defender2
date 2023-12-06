package com.concordia.insha.defender.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.concordia.insha.analyzer.GAnalyzer;
import com.concordia.insha.analyzer.IEAnalyzer;
import com.concordia.insha.collector.NetworkCollector;
import com.concordia.insha.defender.DefenderDBHelper;
import com.concordia.insha.defender.DefenderTask;
import com.concordia.insha.collector.MemoryCollector;
import com.concordia.insha.collector.EventCollector;
import com.concordia.insha.collector.CPUUsageCollector;

import com.concordia.insha.collector.ProcessCollector;
import com.concordia.insha.detector.GDetector;
import com.concordia.insha.detector.IEDetector;
import com.concordia.insha.detector.ThreatDetector;
import com.concordia.insha.model.APackage;


public class DefenderService extends Service {
    private static String TAG = DefenderService.class.getName();
    private static int TIMER = 5000; // 5 sec trigger timer
    BroadcastReceiver eventCollector = null;

    private Timer triggerTimer;
    private TimerTask triggerTimerTask;
    private IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        public DefenderService getService() {
            return DefenderService.this;
        }
    }

    public DefenderService() {
    }

    public int onStartCommand(Intent intent, int flags, int startId) {

        startDetection();

        return START_STICKY;
    }

    public void startDetection(){
        eventCollector = new EventCollector();

        DefenderDBHelper aidsDB = DefenderDBHelper.getInstance(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);

        registerReceiver(eventCollector, filter);

        IntentFilter packageFilter = new IntentFilter();
        packageFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageFilter.addDataScheme("package");

        registerReceiver(eventCollector, packageFilter);

        PackageManager pMgr = this.getPackageManager();
        List<PackageInfo> pInfoList = pMgr
                .getInstalledPackages(PackageManager.GET_PERMISSIONS);

        for (PackageInfo pi : pInfoList) {

            APackage pkg = APackage.InstanceFromPackageInfo(pi);
            aidsDB.insertPackage(pkg);
        }

        final ArrayList<DefenderTask> idsTasks = new ArrayList<DefenderTask>();

        idsTasks.add(new ProcessCollector());
        idsTasks.add(new CPUUsageCollector());
        idsTasks.add(new MemoryCollector());
        idsTasks.add(new NetworkCollector());

        idsTasks.add(new IEAnalyzer());
        idsTasks.add(new IEDetector());
        idsTasks.add(new GAnalyzer());
        idsTasks.add(new GDetector());
        idsTasks.add(new ThreatDetector());


        triggerTimerTask = new TimerTask() {
            @Override
            public void run() {
                for (DefenderTask t : idsTasks) {
                    t.Checked++;

                    if (t.Checked * (TIMER / 1000) >= t.RunEvery) {
                        // TODO run this in a thread?
                        Log.i(TAG, String.format("Running %s", t.toString()));
                        t.doWork(DefenderService.this);
                        t.Checked = 0;
                    }
                }
            }
        };

        triggerTimer = new Timer();
        triggerTimer.scheduleAtFixedRate(triggerTimerTask, 0, TIMER);
    }

    public void stopDetection(){
        if (triggerTimer != null) {
            triggerTimer.cancel();
        }

        if (eventCollector != null) {

            unregisterReceiver(eventCollector);
            eventCollector = null;
        }
    }

    public void resetData(){
        DefenderDBHelper defenderDB = DefenderDBHelper.getInstance(this);
        defenderDB.resetAllData();
    }

    public void onDestroy() {
        if (triggerTimer != null) {
            triggerTimer.cancel();
        }

        if (eventCollector != null) {
            unregisterReceiver(eventCollector);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
