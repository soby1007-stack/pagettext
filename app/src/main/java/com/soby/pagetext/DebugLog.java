package com.soby.pagetext;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;

/** 단계별 디버그 로그: 메모리 링버퍼 + 파일(debug.log) + logcat. */
public final class DebugLog {
    private static final String TAG = "PageText";
    private static final int MAX_LINES = 3000;
    private static final LinkedList<String> BUF = new LinkedList<>();
    private static final SimpleDateFormat FMT = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
    private static File file;

    private DebugLog() {}

    public static synchronized void init(Context c) {
        try {
            File dir = c.getExternalFilesDir(null);
            if (dir == null) dir = c.getFilesDir();
            file = new File(dir, "debug.log");
            if (file.length() > 512 * 1024) file.delete();
        } catch (Throwable t) {
            Log.e(TAG, "log init failed", t);
        }
        d("APP", "init, log file=" + file);
    }

    public static synchronized void d(String stage, String msg) {
        line(Log.DEBUG, "D", stage, msg);
    }

    public static synchronized void e(String stage, String msg, Throwable t) {
        line(Log.ERROR, "E", stage, msg + (t != null ? "\n" + chain(t) : ""));
    }

    private static void line(int prio, String lvl, String stage, String msg) {
        String s = FMT.format(new Date()) + " " + lvl + " [" + stage + "] " + msg;
        Log.println(prio, TAG, "[" + stage + "] " + msg);
        BUF.add(s);
        while (BUF.size() > MAX_LINES) BUF.removeFirst();
        if (file != null) {
            try (FileWriter w = new FileWriter(file, true)) {
                w.write(s + "\n");
            } catch (Throwable ignored) {
            }
        }
    }

    /** 원인 체인(Caused by ...)을 끝까지 풀어서 문자열로. */
    public static String chain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth < 10) {
            sb.append(depth == 0 ? "" : "Caused by: ").append(cur).append("\n");
            StackTraceElement[] st = cur.getStackTrace();
            for (int i = 0; i < st.length && i < 25; i++) sb.append("    at ").append(st[i]).append("\n");
            if (st.length > 25) sb.append("    ... ").append(st.length - 25).append(" more\n");
            cur = cur.getCause();
            depth++;
        }
        return sb.toString();
    }

    public static synchronized String dump() {
        StringBuilder sb = new StringBuilder();
        for (String s : BUF) sb.append(s).append("\n");
        return sb.toString();
    }

    public static synchronized String tail(int maxChars) {
        String s = dump();
        return s.length() > maxChars ? s.substring(s.length() - maxChars) : s;
    }
}
