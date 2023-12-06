package com.concordia.insha.detector;

import java.util.Calendar;
import java.util.HashMap;

import android.content.Context;
import android.util.Log;

import com.concordia.insha.defender.DefenderDBHelper;
import com.concordia.insha.defender.DefenderTask;
import com.concordia.insha.analyzer.IEAnalyzer;
import com.concordia.insha.model.APackage;
import com.concordia.insha.model.Alert;
import com.concordia.insha.model.IEModel;

/*
 * Threat detector for IEModels. It compares process activity models to
 * their learned models and bumps up package threat when it execeeds it.
 *
 */
public class IEDetector extends DefenderTask {
    private final String TAG = IEDetector.class.getName();

    public IEDetector() {
        this.RunEvery = 120;
    }

    public void doWork(Context context) {
        DefenderDBHelper aidsDBHelper = DefenderDBHelper.getInstance(context);

        Calendar calendar = Calendar.getInstance();
        long currentTimeMillis = calendar.getTimeInMillis();
        Calendar prevCalendar = Calendar.getInstance();
        prevCalendar.add(Calendar.SECOND, -1 * RunEvery);
        long prevTimeMillis = prevCalendar.getTimeInMillis();

        HashMap<String, IEModel> previousProcessesModels = IEAnalyzer
                .getIEModelsForProcesses(aidsDBHelper, prevTimeMillis,
                        currentTimeMillis);

        aidsDBHelper.insertLog(String.format("Running IEDetector for %s-%s",
                calendar.toString(), prevCalendar.toString()));

        for (String pName : previousProcessesModels.keySet()) {
            IEModel learnedModel = aidsDBHelper.getIEModel(pName);

            if (learnedModel == null || learnedModel.Age < 24) {
                // model is not old enough or doesn't exist, catch it on next
                // iteration
                continue;
            }

            IEModel activityModel = previousProcessesModels.get(pName);

            float learnedCPULow = learnedModel.CPULow / learnedModel.CPUCounter;
            float learnedCPUMid = learnedModel.CPUMid / learnedModel.CPUCounter;
            float learnedCPUHigh = learnedModel.CPUHigh
                    / learnedModel.CPUCounter;

            // check if activcity model shows activity similar to learned, if
            // not bump package threat
            float cpuLow = activityModel.CPULow / activityModel.CPUCounter;
            float cpuMid = activityModel.CPUMid / activityModel.CPUCounter;
            float cpuHigh = activityModel.CPUHigh / activityModel.CPUCounter;

            if (cpuLow > learnedCPULow || cpuMid > learnedCPUMid
                    || cpuHigh > learnedCPUHigh
                    || activityModel.RxBytes > learnedModel.RxBytes
                    || activityModel.TxBytes > learnedModel.TxBytes) {
                // process behavior does not follow learned model

                // increase the threat of the package
                APackage pkg = aidsDBHelper.getPackage(pName);
                pkg.Threat_Numeric += 0.2;
                aidsDBHelper.updatePackage(pkg);

                aidsDBHelper
                        .insertLog(String
                                .format("Process %s consumed more resources than IEModel %s. Package %s acitivty %s",
                                        activityModel.ProcessName,
                                        learnedModel, pkg, activityModel));
            }
        }
    }
}
