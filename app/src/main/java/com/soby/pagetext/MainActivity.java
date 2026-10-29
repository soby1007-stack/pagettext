package com.soby.pagetext;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.Selection;
import android.text.Spannable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 삼성 인터넷 공유 -> 이 앱 -> WebView로 페이지를 열고 본문 전체를 한 번에 추출해서
 * 일반 TextView(롱프레스 "모두 선택"이 항상 전체 텍스트 기준)로 보여준다.
 *
 * 파이프라인(로그 stage 이름):
 *   INTENT -> LOAD -> WEB(started/finished) -> EXTRACT(S1) -> 스크롤 -> EXTRACT(S2) -> APPLY
 */
public class MainActivity extends Activity {
    private static final String S = "MAIN";
    private static final Pattern URL_RE = Pattern.compile("https?://[^\\s]+");
    private static final String SCROLL_JS = "window.scrollTo(0, document.documentElement.scrollHeight);";

    private WebView web;
    private TextView reader, status;
    private ScrollView readerScroll;
    private EditText urlBox;
    private Button btnScope, btnView;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private String currentUrl = null;
    private String mode = "main"; // main = 본문 추정, full = 페이지 전체
    private String lastText = "";
    private boolean loadFailed = false;
    private boolean showingReader = true;
    private String extractJs = null;

    private final Runnable stage1 = new Runnable() {
        @Override public void run() { runStage1(); }
    };
    private final Runnable stage2 = new Runnable() {
        @Override public void run() { runStage2(); }
    };

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        DebugLog.d(S, "onCreate sdk=" + android.os.Build.VERSION.SDK_INT + " model=" + android.os.Build.MODEL);
        buildUi();
        setupWebView();
        if (saved == null) handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        DebugLog.d(S, "onNewIntent");
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    public void onBackPressed() {
        if (!showingReader) {
            showReader(true);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        if (web != null) {
            web.stopLoading();
            web.destroy();
        }
        super.onDestroy();
    }

    // ---------------------------------------------------------------- UI

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private Button btn(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(2), 0, dp(2), 0);
        b.setOnClickListener(l);
        return b;
    }

    private void buildUi() {
        final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
        final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout row1 = new LinearLayout(this);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        urlBox = new EditText(this);
        urlBox.setSingleLine(true);
        urlBox.setHint("주소를 붙여넣고 가져오기");
        urlBox.setTextSize(14);
        urlBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlBox.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlBox.setOnEditorActionListener((v, actionId, ev) -> {
            loadFromBox();
            return true;
        });
        row1.addView(urlBox, new LinearLayout.LayoutParams(0, WRAP, 1f));
        row1.addView(btn("가져오기", v -> loadFromBox()), new LinearLayout.LayoutParams(WRAP, WRAP));
        root.addView(row1, new LinearLayout.LayoutParams(MATCH, WRAP));

        status = new TextView(this);
        status.setTextSize(12);
        status.setTextColor(0xFF555555);
        status.setPadding(dp(10), dp(2), dp(10), dp(2));
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        root.addView(status, new LinearLayout.LayoutParams(MATCH, WRAP));

        FrameLayout frame = new FrameLayout(this);
        web = new WebView(this);
        frame.addView(web, new FrameLayout.LayoutParams(MATCH, MATCH));

        readerScroll = new ScrollView(this);
        readerScroll.setBackgroundColor(Color.WHITE);
        reader = new TextView(this);
        reader.setTextIsSelectable(true);
        reader.setTextSize(16);
        reader.setTextColor(0xFF111111);
        reader.setLineSpacing(0, 1.25f);
        reader.setPadding(dp(14), dp(10), dp(14), dp(24));
        reader.setText("삼성 인터넷에서 공유 → '페이지 본문' 을 선택하거나, 위 칸에 주소를 붙여넣어 주세요.");
        readerScroll.addView(reader, new ViewGroup.LayoutParams(MATCH, WRAP));
        frame.addView(readerScroll, new FrameLayout.LayoutParams(MATCH, MATCH));
        root.addView(frame, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        LinearLayout row3 = new LinearLayout(this);
        btnScope = btn("", v -> toggleScope());
        btnView = btn("원본", v -> showReader(!showingReader));
        row3.addView(btnScope, new LinearLayout.LayoutParams(0, WRAP, 1.3f));
        row3.addView(btn("전체선택", v -> selectAll()), new LinearLayout.LayoutParams(0, WRAP, 1.2f));
        row3.addView(btn("전체복사", v -> copyAll()), new LinearLayout.LayoutParams(0, WRAP, 1.2f));
        row3.addView(btnView, new LinearLayout.LayoutParams(0, WRAP, 1f));
        row3.addView(btn("다시", v -> reExtract()), new LinearLayout.LayoutParams(0, WRAP, 1f));
        row3.addView(btn("로그", v -> showLog()), new LinearLayout.LayoutParams(0, WRAP, 1f));
        root.addView(row3, new LinearLayout.LayoutParams(MATCH, WRAP));

        updateScopeLabel();
        setContentView(root);
    }

    private void updateScopeLabel() {
        btnScope.setText("범위:" + ("main".equals(mode) ? "본문" : "전체"));
    }

    private void setStatus(String s) {
        status.setText(s);
    }

    private void showReader(boolean r) {
        showingReader = r;
        readerScroll.setVisibility(r ? View.VISIBLE : View.GONE);
        btnView.setText(r ? "원본" : "본문");
        DebugLog.d(S, "showReader=" + r);
    }

    // ---------------------------------------------------------------- WebView

    private void setupWebView() {
        WebView.setWebContentsDebuggingEnabled(true);
        WebSettings st = web.getSettings();
        st.setJavaScriptEnabled(true);
        st.setDomStorageEnabled(true);
        st.setLoadWithOverviewMode(true);
        st.setUseWideViewPort(true);
        st.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        DebugLog.d("WEB", "UA=" + st.getUserAgentString());

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView v, String url, Bitmap favicon) {
                DebugLog.d("WEB", "started " + url);
                setStatus("불러오는 중… " + url);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                DebugLog.d("WEB", "finished " + url + " progress=" + v.getProgress() + " failed=" + loadFailed);
                ui.removeCallbacks(stage1);
                ui.removeCallbacks(stage2);
                ui.postDelayed(stage1, 1000);
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                String msg = e.getErrorCode() + " " + e.getDescription() + " url=" + r.getUrl();
                if (r.isForMainFrame()) {
                    DebugLog.d("WEB", "MAIN-FRAME error " + msg);
                    showLoadError(e.getErrorCode() + " " + e.getDescription());
                } else {
                    DebugLog.d("WEB", "sub-resource error " + msg);
                }
            }

            @Override
            public void onReceivedHttpError(WebView v, WebResourceRequest r, WebResourceResponse resp) {
                DebugLog.d("WEB", "http " + resp.getStatusCode() + " " + r.getUrl() + (r.isForMainFrame() ? " (main)" : ""));
            }

            @Override
            public void onReceivedSslError(WebView v, SslErrorHandler h, SslError e) {
                DebugLog.d("WEB", "ssl error " + e);
                h.cancel();
                showLoadError("SSL 오류: " + e.getPrimaryError());
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage m) {
                if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    DebugLog.d("JS", trunc(m.message(), 200) + " @" + m.sourceId() + ":" + m.lineNumber());
                }
                return true;
            }

            @Override
            public void onProgressChanged(WebView v, int p) {
                if (p < 100 && currentUrl != null && !loadFailed) setStatus("불러오는 중 " + p + "%");
            }
        });
    }

    // ---------------------------------------------------------------- intent / load

    private void handleIntent(Intent in) {
        if (in == null) return;
        String action = in.getAction();
        String extra = in.getStringExtra(Intent.EXTRA_TEXT);
        DebugLog.d("INTENT", "action=" + action + " type=" + in.getType()
                + " text=" + (extra == null ? "null" : trunc(extra, 300)));
        if (Intent.ACTION_SEND.equals(action) && extra != null) {
            String url = findUrl(extra);
            if (url == null) {
                DebugLog.d("INTENT", "no URL found in shared text");
                urlBox.setText(extra);
                Toast.makeText(this, "공유된 내용에서 주소를 찾지 못했어요", Toast.LENGTH_LONG).show();
                return;
            }
            DebugLog.d("INTENT", "url parsed=" + url);
            load(url);
        } else {
            setStatus("공유로 보내거나 주소를 붙여넣어 주세요");
        }
    }

    private static String findUrl(String text) {
        Matcher m = URL_RE.matcher(text);
        if (!m.find()) return null;
        String u = m.group();
        while (u.length() > 0 && ")]}>\"',.".indexOf(u.charAt(u.length() - 1)) >= 0) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    private void loadFromBox() {
        String raw = urlBox.getText().toString().trim();
        if (raw.isEmpty()) return;
        String url = findUrl(raw);
        if (url == null) url = raw.startsWith("http") ? raw : "https://" + raw;
        urlBox.clearFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(urlBox.getWindowToken(), 0);
        load(url);
    }

    private void load(String url) {
        currentUrl = url;
        lastText = "";
        loadFailed = false;
        ui.removeCallbacks(stage1);
        ui.removeCallbacks(stage2);
        urlBox.setText(url);
        showReader(true);
        reader.setText("불러오는 중…\n" + url);
        setStatus("불러오는 중…");
        DebugLog.d("LOAD", "url=" + url);
        web.stopLoading();
        web.loadUrl(url);
    }

    private void showLoadError(String msg) {
        loadFailed = true;
        showReader(true);
        reader.setText("페이지를 불러오지 못했어요\n\n" + msg + "\n\n" + currentUrl
                + "\n\n('원본'으로 확인하거나 '로그'를 봐주세요)");
        setStatus("불러오기 실패: " + msg);
    }

    // ---------------------------------------------------------------- extraction

    private void runStage1() {
        if (loadFailed) {
            DebugLog.d("EXTRACT", "S1 skipped (load failed)");
            return;
        }
        extract("S1", () -> {
            web.evaluateJavascript(SCROLL_JS, null);
            DebugLog.d("EXTRACT", "scrolled to bottom to trigger lazy content");
            ui.postDelayed(stage2, 1500);
        });
    }

    private void runStage2() {
        if (loadFailed) return;
        extract("S2", null);
    }

    private void reExtract() {
        if (currentUrl == null) {
            Toast.makeText(this, "먼저 페이지를 가져와 주세요", Toast.LENGTH_SHORT).show();
            return;
        }
        DebugLog.d(S, "manual re-extract");
        ui.removeCallbacks(stage1);
        ui.removeCallbacks(stage2);
        ui.post(stage1);
    }

    private void toggleScope() {
        mode = "main".equals(mode) ? "full" : "main";
        updateScopeLabel();
        DebugLog.d(S, "scope -> " + mode);
        if (currentUrl != null && !loadFailed) extract("M", null);
    }

    private String loadExtractJs() throws IOException {
        if (extractJs == null) {
            try (InputStream in = getAssets().open("extract.js");
                 ByteArrayOutputStream bo = new ByteArrayOutputStream()) {
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) > 0) bo.write(b, 0, n);
                extractJs = bo.toString("UTF-8");
            }
        }
        return extractJs;
    }

    private void extract(final String stage, final Runnable after) {
        final String js;
        try {
            js = loadExtractJs().replace("__MODE__", mode);
        } catch (Throwable t) {
            DebugLog.e("EXTRACT", stage + " asset load failed", t);
            showError("추출 스크립트를 읽지 못했어요", t);
            return;
        }
        DebugLog.d("EXTRACT", stage + " start mode=" + mode);
        web.evaluateJavascript(js, value -> {
            try {
                if (value == null || value.equals("null")) {
                    DebugLog.d("EXTRACT", stage + " got null result");
                    return;
                }
                Object o = new JSONTokener(value).nextValue();
                JSONObject r = new JSONObject(o instanceof String ? (String) o : String.valueOf(o));
                if (r.has("error")) DebugLog.d("EXTRACT", stage + " JS error: " + r.optString("error"));
                String text = r.optString("text", "");
                DebugLog.d("EXTRACT", stage + " ok src=" + r.optString("src") + " len=" + text.length()
                        + " fullLen=" + r.optInt("fullLen") + " cand=" + r.optInt("candidates", -1)
                        + " scrollH=" + r.optInt("scrollHeight"));
                applyText(text, r.optString("title"), r.optString("src"), stage);
            } catch (Throwable t) {
                DebugLog.e("EXTRACT", stage + " parse failed", t);
                showError("본문 추출 실패", t);
            } finally {
                if (after != null) after.run();
            }
        });
    }

    private void applyText(String text, String title, String src, String stage) {
        if (text.isEmpty()) {
            if (lastText.isEmpty()) {
                reader.setText("(추출된 텍스트가 없어요)\n\n'원본'으로 페이지를 확인하거나 '로그'를 봐주세요.");
                setStatus("텍스트 없음 · " + src);
            }
            DebugLog.d("APPLY", stage + " empty text");
            return;
        }
        if (text.equals(lastText)) {
            DebugLog.d("APPLY", stage + " same text, skip");
            return;
        }
        if ("S2".equals(stage) && !lastText.isEmpty() && text.length() <= lastText.length() * 1.02) {
            DebugLog.d("APPLY", "S2 not meaningfully longer (" + text.length() + " vs " + lastText.length() + "), keep S1");
            return;
        }
        boolean resetScroll = lastText.isEmpty() || "M".equals(stage);
        lastText = text;
        reader.setText(text, TextView.BufferType.SPANNABLE);
        if (resetScroll) readerScroll.scrollTo(0, 0);
        setStatus((title.isEmpty() ? "" : title + " · ") + text.length() + "자 · " + src);
        DebugLog.d("APPLY", stage + " applied len=" + text.length());
    }

    // ---------------------------------------------------------------- actions

    private void selectAll() {
        if (lastText.isEmpty()) {
            Toast.makeText(this, "선택할 본문이 없어요", Toast.LENGTH_SHORT).show();
            return;
        }
        showReader(true);
        reader.requestFocus();
        CharSequence t = reader.getText();
        if (t instanceof Spannable) Selection.selectAll((Spannable) t);
        DebugLog.d("SELECT", "selectAll len=" + t.length());
        Toast.makeText(this, "전체 선택됨 (복사는 '전체복사')", Toast.LENGTH_SHORT).show();
    }

    private void copyAll() {
        if (lastText.isEmpty()) {
            Toast.makeText(this, "복사할 본문이 없어요", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("page text", lastText));
            DebugLog.d("COPY", "len=" + lastText.length());
            Toast.makeText(this, lastText.length() + "자 복사됨", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            DebugLog.e("COPY", "failed", t);
            showError("복사 실패", t);
        }
    }

    private void copyText(String label, String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText(label, text));
            Toast.makeText(this, "복사됨", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "복사 실패: " + t, Toast.LENGTH_LONG).show();
        }
    }

    // ---------------------------------------------------------------- dialogs

    private void showError(String title, Throwable t) {
        final String body = DebugLog.chain(t);
        TextView tv = new TextView(this);
        tv.setText(trunc(body, 6000));
        tv.setTextIsSelectable(true);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextSize(11);
        tv.setPadding(dp(14), dp(8), dp(14), dp(8));
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(sv)
                .setPositiveButton("복사", (d, w) -> copyText("error", title + "\n" + body))
                .setNegativeButton("닫기", null)
                .show();
    }

    private void showLog() {
        final String log = DebugLog.tail(40000);
        TextView tv = new TextView(this);
        tv.setText(log);
        tv.setTextIsSelectable(true);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextSize(10);
        tv.setPadding(dp(10), dp(8), dp(10), dp(8));
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this)
                .setTitle("디버그 로그")
                .setView(sv)
                .setPositiveButton("복사", (d, w) -> copyText("log", log))
                .setNegativeButton("닫기", null)
                .show();
    }

    private static String trunc(String s, int n) {
        if (s == null) return "null";
        return s.length() <= n ? s : s.substring(0, n) + "…(" + s.length() + "자)";
    }
}
