package com.concordia.insha.analyzer;


import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

import android.content.Context;
import android.util.Log;

import com.concordia.insha.defender.DefenderDBHelper;
import com.concordia.insha.defender.DefenderTask;
import com.concordia.insha.model.CPUUsage;
import com.concordia.insha.model.IEModel;
import com.concordia.insha.model.Process;

/*
 * Resource usage per process. Uses IEModel to store a learned
 * model.
 */
public class IEAnalyzer extends DefenderTask {
    private final String TAG = IEAnalyzer.class.getName();

    public IEAnalyzer(){
        this.RunEvery = 120;
    }

    public void doWork(Context context) {
        DefenderDBHelper aidsDBHelper = DefenderDBHelper.getInstance(context);

        //get time now and since last invocation
        Calendar calendar = Calendar.getInstance();
        long currentTimeMillis = calendar.getTimeInMillis();
        Calendar prevCalendar = Calendar.getInstance();
        prevCalendar.add(Calendar.SECOND, -1 * RunEvery);
        long prevTimeMillis = prevCalendar.getTimeInMillis();

        aidsDBHelper.insertLog(String.format("Running IEAnalyzer for %s-%s",
                calendar.toString(), prevCalendar.toString()));

        //get IEModels of processes since last invocation
        HashMap<String, IEModel> processMap = getIEModelsForProcesses(
                aidsDBHelper, prevTimeMillis, currentTimeMillis);

        // get model
        // if null or not of age, we're still learning
        // otherwise, model is mature do nothing
        for (String pName : processMap.keySet()) {
            IEModel ieModel = aidsDBHelper.getIEModel(pName);
            IEModel newModel = processMap.get(pName);

            if (ieModel == null) {
                // first invocation
                ieModel = newModel;
                ieModel.ProcessName = pName;
                ieModel.FromTimeStamp = prevTimeMillis;
                ieModel.ToTimeStamp = currentTimeMillis;
                ieModel.Age = 1;

                aidsDBHelper.insertIEModel(ieModel);

                continue;
            }

            // check if our model is old enough (currently 24 hours)
            if (ieModel.Age < 24) {
                // model is not old enough
                // update the model with current generation
                ieModel.ToTimeStamp = currentTimeMillis;
                ieModel.CPULow = newModel.CPULow;
                ieModel.CPUMid = newModel.CPUMid;
                ieModel.CPUHigh = newModel.CPUHigh;
                ieModel.CPUCounter = newModel.CPUCounter;
                ieModel.Age = ieModel.Age + 1; // and increment the age

                aidsDBHelper.updateIEModel(ieModel);

                continue;
            }
        }
    }

    //return hashmap of IEModel keyed by process name
    public static HashMap<String, IEModel> getIEModelsForProcesses(
            DefenderDBHelper aidsDBHelper, long fromTimeMillis, long toTimeMillis) {
        // hashmap keyed by process name and then attributes
        HashMap<String, IEModel> processMap = new HashMap<String, IEModel>();

        // get processes for specified period
        List<Process> processListForPeriod = aidsDBHelper.getProcesses(
                fromTimeMillis, toTimeMillis);

        // i have to iterate over them all because some could have different PIDs
        // so i group them under process name
        for (Process p : processListForPeriod) {

            IEModel learnedModel = aidsDBHelper.getIEModel(p.Name);

            if (learnedModel != null && learnedModel.Age >= 24) {
                // model is mature, no need to do anything
                // work will be done by threat detector
                continue;
            }

            // otherwise, model is either not of age or doesn't exist

            if (!processMap.containsKey(p.Name)) {
                processMap.put(p.Name, new IEModel());
            }

            IEModel pIEModel = processMap.get(p.Name);

            // get cpuusage for each process
            List<CPUUsage> cpuUsageForProcessList = aidsDBHelper.getCPUUsage(
                    p.Pid, fromTimeMillis, toTimeMillis);

            for (CPUUsage cpu : cpuUsageForProcessList) {
                int cpuUsageInt = Integer.parseInt(cpu.CPUUsage);

                if (cpuUsageInt < 30) {
                    pIEModel.CPULow = pIEModel.CPULow + 1;
                } else if (cpuUsageInt < 60) {
                    pIEModel.CPUMid = pIEModel.CPUMid + 1;
                } else {
                    pIEModel.CPUHigh = pIEModel.CPUHigh + 1;
                }

                pIEModel.CPUCounter = pIEModel.CPUCounter + 1;
            }

            // bandwidth usage for each process
            HashMap<String, String> bandwidthUse = aidsDBHelper
                    .getBandwidthUsage(p.Uid, fromTimeMillis, toTimeMillis);
            // bandwidth is calculated per uid not pid, so its already
            // cumulative
            pIEModel.RxBytes = Integer.parseInt(bandwidthUse.get("rx"));
            pIEModel.TxBytes = Integer.parseInt(bandwidthUse.get("tx"));
        }

        return processMap;
    }
}

