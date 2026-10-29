package com.soby.pagetext;

import android.app.Application;
import android.content.Intent;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DebugLog.init(this);
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            try {
                DebugLog.e("CRASH", "uncaught on thread " + thread.getName(), e);
                String report = "Thread: " + thread.getName() + "\n\n" + DebugLog.chain(e)
                        + "\n--- 최근 로그 ---\n" + DebugLog.tail(30000);
                if (report.length() > 60000) report = report.substring(report.length() - 60000);
                Intent i = new Intent(this, CrashActivity.class);
                i.putExtra("report", report);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
            } catch (Throwable ignored) {
            }
            try {
                Thread.sleep(400);
            } catch (InterruptedException ignored) {
            }
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        });
    }
}
