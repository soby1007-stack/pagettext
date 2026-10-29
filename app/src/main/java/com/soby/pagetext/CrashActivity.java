package com.soby.pagetext;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 크래시 팝업: 별도 프로세스(:crash)에서 떠서 원인 체인 + 최근 로그를 보여준다. */
public class CrashActivity extends Activity {
    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final String report = String.valueOf(getIntent().getStringExtra("report"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        root.setPadding(dp(10), dp(10), dp(10), dp(10));

        TextView title = new TextView(this);
        title.setText("앱이 비정상 종료됐어요. 아래 로그를 복사해서 보내주세요.");
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        ScrollView sv = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(report);
        tv.setTextIsSelectable(true);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextSize(11);
        sv.addView(tv, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout row = new LinearLayout(this);
        Button copy = new Button(this);
        copy.setText("로그 복사");
        copy.setOnClickListener(v -> {
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("crash", report));
                Toast.makeText(this, "복사됨", Toast.LENGTH_SHORT).show();
            } catch (Throwable t) {
                Toast.makeText(this, "복사 실패: " + t, Toast.LENGTH_LONG).show();
            }
        });
        Button restart = new Button(this);
        restart.setText("다시 시작");
        restart.setOnClickListener(v -> {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });
        Button close = new Button(this);
        close.setText("닫기");
        close.setOnClickListener(v -> finish());
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(restart, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(close, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(row);

        setContentView(root);
    }
}
