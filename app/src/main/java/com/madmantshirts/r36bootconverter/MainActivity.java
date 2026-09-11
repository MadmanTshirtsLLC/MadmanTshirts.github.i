package com.madmantshirts.r36bootconverter;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.VideoView;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegKitConfig;
import com.arthenica.ffmpegkit.ReturnCode;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int PICK_VIDEO = 1001;
    private static final int SAVE_VIDEO = 1002;

    private Uri inputUri;
    private File outputFile;
    private long sourceDurationMs = 0L;

    private TextView fileInfo;
    private TextView status;
    private VideoView preview;
    private Spinner presetSpinner;
    private Spinner fitSpinner;
    private EditText startEdit;
    private EditText endEdit;
    private Switch audioSwitch;
    private Button convertButton;
    private Button saveButton;
    private Button cancelButton;
    private ProgressBar progress;

    private final int green = Color.rgb(80, 255, 110);
    private final int dimGreen = Color.rgb(150, 220, 160);
    private final int panel = Color.rgb(6, 16, 9);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(30));
        scroll.addView(root);

        TextView eyebrow = text("ARKOS4CLONE UTILITY // R36 BOOT VIDEO", 11, dimGreen);
        root.addView(eyebrow);

        TextView title = text("BOOT VIDEO CONVERTER", 27, green);
        title.setPadding(0, dp(4), 0, dp(4));
        root.addView(title);

        TextView intro = text("Convert any phone video locally into a boot-safe logo.mp4. No upload, no account, no browser encoder.", 14, Color.rgb(200, 255, 210));
        intro.setPadding(0, 0, 0, dp(16));
        root.addView(intro);

        Button pick = button("SELECT VIDEO");
        pick.setOnClickListener(v -> chooseVideo());
        root.addView(pick, full(dp(52)));

        fileInfo = text("No video selected.", 13, dimGreen);
        fileInfo.setPadding(0, dp(10), 0, dp(10));
        root.addView(fileInfo);

        preview = new VideoView(this);
        preview.setBackgroundColor(Color.BLACK);
        LinearLayout.LayoutParams videoLp = new LinearLayout.LayoutParams(-1, dp(280));
        videoLp.setMargins(0, dp(4), 0, dp(18));
        root.addView(preview, videoLp);

        root.addView(label("DEVICE PRESET"));
        presetSpinner = spinner(new String[]{
                "R36T MAX — 720 × 720",
                "Classic R36S — 640 × 480"
        });
        root.addView(presetSpinner, full(dp(52)));

        root.addView(label("VIDEO FIT"));
        fitSpinner = spinner(new String[]{
                "Center crop (recommended)",
                "Fit with black bars",
                "Stretch to exact size"
        });
        root.addView(fitSpinner, full(dp(52)));

        LinearLayout trimRow = new LinearLayout(this);
        trimRow.setOrientation(LinearLayout.HORIZONTAL);
        trimRow.setPadding(0, dp(12), 0, 0);

        LinearLayout startBox = new LinearLayout(this);
        startBox.setOrientation(LinearLayout.VERTICAL);
        startBox.addView(label("START (SECONDS)"));
        startEdit = edit("0");
        startBox.addView(startEdit, full(dp(50)));
        trimRow.addView(startBox, weighted());

        View spacer = new View(this);
        trimRow.addView(spacer, new LinearLayout.LayoutParams(dp(10), 1));

        LinearLayout endBox = new LinearLayout(this);
        endBox.setOrientation(LinearLayout.VERTICAL);
        endBox.addView(label("END (SECONDS)"));
        endEdit = edit("");
        endEdit.setHint("full video");
        endEdit.setHintTextColor(Color.rgb(80, 130, 90));
        endBox.addView(endEdit, full(dp(50)));
        trimRow.addView(endBox, weighted());
        root.addView(trimRow);

        audioSwitch = new Switch(this);
        audioSwitch.setText("Keep audio as AAC");
        audioSwitch.setTextColor(Color.rgb(205, 255, 215));
        audioSwitch.setTextSize(14);
        audioSwitch.setChecked(true);
        audioSwitch.setPadding(dp(8), dp(14), dp(8), dp(14));
        root.addView(audioSwitch);

        TextView spec = text("LOCKED COMPATIBILITY OUTPUT\n30 FPS • H.264/AVC • Baseline • Level 3.1 • yuv420p\n~2.5 Mbps video • AAC 44.1 kHz / 128 kbps • MP4 • filename logo.mp4", 12, dimGreen);
        spec.setBackgroundColor(panel);
        spec.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(spec, full(-2));

        convertButton = button("CONVERT TO logo.mp4");
        convertButton.setEnabled(false);
        convertButton.setOnClickListener(v -> convert());
        LinearLayout.LayoutParams convertLp = full(dp(56));
        convertLp.setMargins(0, dp(16), 0, 0);
        root.addView(convertButton, convertLp);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgress(0);
        LinearLayout.LayoutParams progLp = full(dp(12));
        progLp.setMargins(0, dp(12), 0, dp(6));
        root.addView(progress, progLp);

        status = text("Choose a video to begin.", 13, Color.rgb(190, 230, 195));
        root.addView(status);

        cancelButton = button("CANCEL ENCODE");
        cancelButton.setVisibility(View.GONE);
        cancelButton.setOnClickListener(v -> {
            FFmpegKit.cancel();
            setWorking(false);
            status.setText("Encoding cancelled.");
        });
        LinearLayout.LayoutParams cancelLp = full(dp(48));
        cancelLp.setMargins(0, dp(10), 0, 0);
        root.addView(cancelButton, cancelLp);

        saveButton = button("SAVE logo.mp4");
        saveButton.setVisibility(View.GONE);
        saveButton.setOnClickListener(v -> chooseSaveLocation());
        LinearLayout.LayoutParams saveLp = full(dp(54));
        saveLp.setMargins(0, dp(10), 0, 0);
        root.addView(saveButton, saveLp);

        TextView steps = text("INSTALL ON HANDHELD\n1. Save the converted file as logo.mp4.\n2. Put logo.mp4 in the root of the BOOT partition.\n3. Keep the filename lowercase and make sure it is not logo.mp4.mp4.", 13, Color.rgb(205, 255, 215));
        steps.setPadding(0, dp(20), 0, 0);
        root.addView(steps);

        setContentView(scroll);
    }

    private void chooseVideo() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        startActivityForResult(intent, PICK_VIDEO);
    }

    private void chooseSaveLocation() {
        if (outputFile == null || !outputFile.exists()) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/mp4");
        intent.putExtra(Intent.EXTRA_TITLE, "logo.mp4");
        startActivityForResult(intent, SAVE_VIDEO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        if (requestCode == PICK_VIDEO) {
            inputUri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(inputUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            loadSelectedVideo(inputUri);
        } else if (requestCode == SAVE_VIDEO) {
            saveOutputTo(data.getData());
        }
    }

    private void loadSelectedVideo(Uri uri) {
        sourceDurationMs = 0L;
        String displayName = queryDisplayName(uri);
        int width = 0;
        int height = 0;

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, uri);
            String d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            String w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            if (d != null) sourceDurationMs = Long.parseLong(d);
            if (w != null) width = Integer.parseInt(w);
            if (h != null) height = Integer.parseInt(h);
        } catch (Exception ignored) {
        } finally {
            try { retriever.release(); } catch (Exception ignored) {}
        }

        double seconds = sourceDurationMs / 1000.0;
        fileInfo.setText(String.format(Locale.US, "%s\nSource: %d × %d   •   Duration: %.1f s", displayName, width, height, seconds));
        if (sourceDurationMs > 0) endEdit.setHint(String.format(Locale.US, "%.1f", seconds));

        preview.setVideoURI(uri);
        preview.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            preview.start();
        });

        convertButton.setEnabled(true);
        saveButton.setVisibility(View.GONE);
        progress.setProgress(0);
        status.setText("Video loaded. Choose a preset and convert.");
    }

    private void convert() {
        if (inputUri == null) return;

        double startSec = parseSeconds(startEdit.getText().toString(), 0.0);
        double endSec = parseSeconds(endEdit.getText().toString(), sourceDurationMs > 0 ? sourceDurationMs / 1000.0 : 0.0);
        if (startSec < 0) startSec = 0;
        if (endSec > 0 && endSec <= startSec) {
            status.setText("End time must be greater than start time.");
            return;
        }

        int width = presetSpinner.getSelectedItemPosition() == 0 ? 720 : 640;
        int height = presetSpinner.getSelectedItemPosition() == 0 ? 720 : 480;
        int fitMode = fitSpinner.getSelectedItemPosition();

        String filter;
        if (fitMode == 1) {
            filter = "scale=" + width + ":" + height + ":force_original_aspect_ratio=decrease,pad=" + width + ":" + height + ":(ow-iw)/2:(oh-ih)/2:black";
        } else if (fitMode == 2) {
            filter = "scale=" + width + ":" + height;
        } else {
            filter = "scale=" + width + ":" + height + ":force_original_aspect_ratio=increase,crop=" + width + ":" + height;
        }

        outputFile = new File(getCacheDir(), "logo.mp4");
        if (outputFile.exists()) outputFile.delete();

        String inputPath = FFmpegKitConfig.getSafParameterForRead(this, inputUri);
        double expectedSec = endSec > startSec ? (endSec - startSec) : (sourceDurationMs > 0 ? sourceDurationMs / 1000.0 - startSec : 1.0);
        if (expectedSec <= 0) expectedSec = 1.0;
        final double expected = expectedSec;

        StringBuilder cmd = new StringBuilder();
        cmd.append("-hide_banner -y ");
        if (startSec > 0) cmd.append("-ss ").append(String.format(Locale.US, "%.3f", startSec)).append(' ');
        cmd.append("-i ").append(inputPath).append(' ');
        if (endSec > startSec) cmd.append("-t ").append(String.format(Locale.US, "%.3f", endSec - startSec)).append(' ');
        cmd.append("-vf \"").append(filter).append("\" ");
        cmd.append("-r 30 -c:v libx264 -profile:v baseline -level:v 3.1 -pix_fmt yuv420p ");
        cmd.append("-preset ultrafast -b:v 2500k -maxrate 3000k -bufsize 6000k -g 30 -keyint_min 30 -sc_threshold 0 ");
        if (audioSwitch.isChecked()) {
            cmd.append("-c:a aac -b:a 128k -ar 44100 -ac 2 ");
        } else {
            cmd.append("-an ");
        }
        cmd.append("-sn -map_metadata -1 -movflags +faststart -f mp4 ").append(outputFile.getAbsolutePath());

        setWorking(true);
        progress.setProgress(1);
        status.setText("Encoding locally…");

        FFmpegKit.executeAsync(
                cmd.toString(),
                session -> runOnUiThread(() -> {
                    if (ReturnCode.isSuccess(session.getReturnCode()) && outputFile.exists() && outputFile.length() > 0) {
                        progress.setProgress(1000);
                        status.setText("Conversion complete. Save the finished logo.mp4.");
                        saveButton.setVisibility(View.VISIBLE);
                    } else if (ReturnCode.isCancel(session.getReturnCode())) {
                        status.setText("Encoding cancelled.");
                    } else {
                        String fail = session.getFailStackTrace();
                        status.setText("Conversion failed." + (fail == null || fail.isEmpty() ? "" : " Check encoder compatibility on this phone."));
                    }
                    setWorking(false);
                }),
                log -> { },
                statistics -> {
                    long timeMs = statistics.getTime();
                    int value = (int)Math.max(1, Math.min(999, (timeMs / (expected * 1000.0)) * 1000.0));
                    runOnUiThread(() -> {
                        progress.setProgress(value);
                        status.setText(String.format(Locale.US, "Encoding locally… %d%%", value / 10));
                    });
                }
        );
    }

    private void saveOutputTo(Uri destination) {
        if (outputFile == null || !outputFile.exists()) return;
        status.setText("Saving logo.mp4…");
        new Thread(() -> {
            try (InputStream in = new FileInputStream(outputFile);
                 OutputStream out = getContentResolver().openOutputStream(destination, "w")) {
                if (out == null) throw new IllegalStateException("Could not open destination");
                byte[] buffer = new byte[1024 * 128];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                out.flush();
                runOnUiThread(() -> status.setText("Saved successfully as logo.mp4."));
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Save failed: " + e.getMessage()));
            }
        }).start();
    }

    private void setWorking(boolean working) {
        convertButton.setEnabled(!working && inputUri != null);
        cancelButton.setVisibility(working ? View.VISIBLE : View.GONE);
        if (working) saveButton.setVisibility(View.GONE);
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {}
        return "Selected video";
    }

    private double parseSeconds(String s, double fallback) {
        try {
            if (s == null || s.trim().isEmpty()) return fallback;
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private TextView text(String value, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private TextView label(String value) {
        TextView t = text(value, 11, dimGreen);
        t.setPadding(0, dp(12), 0, dp(5));
        return t;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(Color.BLACK);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setBackgroundColor(green);
        return b;
    }

    private Spinner spinner(String[] items) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, items) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(Color.WHITE);
                    ((TextView) v).setTextSize(14);
                    v.setBackgroundColor(panel);
                    v.setPadding(dp(10), 0, dp(10), 0);
                }
                return v;
            }
        };
        s.setAdapter(adapter);
        s.setBackgroundColor(panel);
        return s;
    }

    private EditText edit(String value) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setTextColor(Color.WHITE);
        e.setTextSize(14);
        e.setSingleLine(true);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setBackgroundColor(panel);
        e.setPadding(dp(10), 0, dp(10), 0);
        return e;
    }

    private LinearLayout.LayoutParams full(int height) {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height);
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { preview.stopPlayback(); } catch (Exception ignored) {}
    }
}
