package com.example.speechrecognitionapp;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.*;

public class PreviousActivity extends AppCompatActivity {

    private LinearLayout layoutFiles;
    private File audioDir, textDir;
    private MediaPlayer mediaPlayer;
    private Button currentlyPlayingStopBtn = null;
    private List<File> allFiles = new ArrayList<>();
    private EditText searchBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_previous);

        layoutFiles = findViewById(R.id.layout_files);
        Button btnBack = findViewById(R.id.btn_back);
        searchBar = findViewById(R.id.search_bar);

        btnBack.setOnClickListener(v -> finish());

        audioDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        textDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);

        collectAllFiles();
        displayFilteredFiles("");

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                displayFilteredFiles(s.toString());
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });
    }

    private void collectAllFiles() {
        allFiles.clear();
        if (audioDir != null) {
            for (File file : audioDir.listFiles()) {
                if (file.getName().endsWith(".3gp")) {
                    allFiles.add(file);
                }
            }
        }
        if (textDir != null) {
            for (File file : textDir.listFiles()) {
                if (file.getName().endsWith(".txt")) {
                    allFiles.add(file);
                }
            }
        }

        allFiles.sort((f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
    }

    private void displayFilteredFiles(String query) {
        layoutFiles.removeAllViews();
        String lastGroup = "";

        for (File file : allFiles) {
            if (!file.getName().toLowerCase().contains(query.toLowerCase())) continue;

            String group = getGroupLabel(file.lastModified());
            if (!group.equals(lastGroup)) {
                lastGroup = group;
                addDateLabel(group);
            }

            addFileCard(file);
        }
    }

    private void addDateLabel(String labelText) {
        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextSize(18);
        label.setTypeface(null, Typeface.BOLD);
        label.setTextColor(Color.BLACK);
        label.setPadding(8, 24, 8, 8);
        layoutFiles.addView(label);
    }

    private void addFileCard(File file) {
        boolean isAudio = file.getName().endsWith(".3gp");

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(24, 24, 24, 24);
        card.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 24);
        card.setLayoutParams(params);

        TextView name = new TextView(this);
        name.setText((isAudio ? "🔊 " : "📄 ") + file.getName());
        name.setTextSize(16f);
        name.setTypeface(null, Typeface.BOLD);

        TextView time = new TextView(this);
        time.setText(getFormattedTime(file.lastModified()));
        time.setTextSize(14f);
        time.setTextColor(Color.DKGRAY);

        card.addView(name);
        card.addView(time);

        if (isAudio) {
            LinearLayout controls = new LinearLayout(this);
            controls.setOrientation(LinearLayout.HORIZONTAL);
            controls.setPadding(0, 16, 0, 0);

            Button btnPlay = new Button(this);
            btnPlay.setText("▶ Play");
            Button btnStop = new Button(this);
            btnStop.setText("⏹ Stop");
            btnStop.setEnabled(false);
            Button btnDelete = new Button(this);
            btnDelete.setText("🗑 Delete");

            btnPlay.setOnClickListener(v -> playAudioFile(file, btnPlay, btnStop));
            btnStop.setOnClickListener(v -> stopAudio(btnPlay, btnStop));
            btnDelete.setOnClickListener(v -> deleteFile(file, card));

            controls.addView(btnPlay);
            controls.addView(btnStop);
            controls.addView(btnDelete);
            card.addView(controls);
        } else {
            Button btnDelete = new Button(this);
            btnDelete.setText("🗑 Delete");
            btnDelete.setOnClickListener(v -> deleteFile(file, card));
            card.setOnClickListener(v -> showTranscript(file));
            card.addView(btnDelete);
        }

        layoutFiles.addView(card);
    }

    private void deleteFile(File file, LinearLayout card) {
        if (file.delete()) {
            layoutFiles.removeView(card);
            allFiles.remove(file);
            Toast.makeText(this, "Deleted: " + file.getName(), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Failed to delete file", Toast.LENGTH_SHORT).show();
        }
    }

    private void playAudioFile(File file, Button btnPlay, Button btnStop) {
        stopCurrentAudio();
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(file.getAbsolutePath());
            mediaPlayer.prepare();
            mediaPlayer.start();
            btnPlay.setEnabled(false);
            btnStop.setEnabled(true);
            currentlyPlayingStopBtn = btnStop;

            mediaPlayer.setOnCompletionListener(mp -> {
                btnPlay.setEnabled(true);
                btnStop.setEnabled(false);
                mediaPlayer.release();
                mediaPlayer = null;
                currentlyPlayingStopBtn = null;
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopAudio(Button btnPlay, Button btnStop) {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        btnPlay.setEnabled(true);
        btnStop.setEnabled(false);
        currentlyPlayingStopBtn = null;
    }

    private void stopCurrentAudio() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (currentlyPlayingStopBtn != null) {
            Button play = (Button) ((ViewGroup) currentlyPlayingStopBtn.getParent()).getChildAt(0);
            play.setEnabled(true);
            currentlyPlayingStopBtn.setEnabled(false);
            currentlyPlayingStopBtn = null;
        }
    }

    private void showTranscript(File file) {
        StringBuilder content = new StringBuilder();
        try (Scanner scanner = new Scanner(file)) {
            while (scanner.hasNextLine()) {
                content.append(scanner.nextLine()).append("\n");
            }
        } catch (Exception e) {
            content.append("Unable to read file.");
            e.printStackTrace();
        }

        new AlertDialog.Builder(this)
                .setTitle(file.getName())
                .setMessage(content.toString())
                .setPositiveButton("Close", null)
                .show();
    }

    private String getFormattedTime(long millis) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault());
        return sdf.format(new Date(millis));
    }

    private String getGroupLabel(long millis) {
        Calendar fileCal = Calendar.getInstance();
        fileCal.setTimeInMillis(millis);

        Calendar today = Calendar.getInstance();
        Calendar yesterday = (Calendar) today.clone();
        yesterday.add(Calendar.DATE, -1);

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

        if (isSameDay(fileCal, today)) return "Today";
        else if (isSameDay(fileCal, yesterday)) return "Yesterday";
        else return dateFormat.format(fileCal.getTime());
    }

    private boolean isSameDay(Calendar c1, Calendar c2) {
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)
                && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCurrentAudio();
    }
}
