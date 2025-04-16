package com.example.speechrecognitionapp;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class MainActivity extends AppCompatActivity {

    private static final int SAMPLE_RATE = 16000;
    private static final int REQUEST_RECORD_AUDIO = 101;

    private AudioRecord recorder;
    private MediaRecorder mediaRecorder;
    private MediaPlayer mediaPlayer;
    private WebSocket webSocket;
    private boolean isStreaming = false;

    private File audioFile;
    private final StringBuilder fullTranscript = new StringBuilder();

    private TextView tvTranscription;
    private ScrollView scrollView;
    private Button btnStart, btnStop, btnPlay, btnStopAudio, btnClear, btnPrevious;
    private Handler uiHandler = new Handler();

    private static final String WS_URL = "ws://youripaddress:8000/ws/speech";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvTranscription = findViewById(R.id.tv_transcription);
        scrollView = findViewById(R.id.scrollView);
        btnStart = findViewById(R.id.btn_start);
        btnStop = findViewById(R.id.btn_stop);
        btnPlay = findViewById(R.id.btn_play);
        btnStopAudio = findViewById(R.id.btn_stop_audio);
        btnClear = findViewById(R.id.btn_clear);
        btnPrevious = findViewById(R.id.btn_previous);

        btnPlay.setEnabled(false);
        btnStopAudio.setEnabled(false);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
        }

        btnStart.setOnClickListener(v -> startStreaming());
        btnStop.setOnClickListener(v -> stopStreaming());
        btnPlay.setOnClickListener(v -> playAudio());
        btnStopAudio.setOnClickListener(v -> stopAudio());
        btnClear.setOnClickListener(v -> {
            fullTranscript.setLength(0);
            tvTranscription.setText("");
        });

        btnPrevious.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, PreviousActivity.class)));
    }

    private void startStreaming() {
        isStreaming = true;
        btnStart.setEnabled(false);
        btnStop.setEnabled(true);
        fullTranscript.setLength(0);
        tvTranscription.setText("Listening...\n");

        try {
            audioFile = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "temp_recording.3gp");
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setOutputFile(audioFile.getAbsolutePath());
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.prepare();
            mediaRecorder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(WS_URL).build();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket socket, Response response) {
                startRecordingAndSending(socket);
            }

            @Override
            public void onMessage(WebSocket socket, String text) {
                try {
                    JSONObject obj = new JSONObject(text);
                    String type = obj.getString("type");
                    String msg = obj.getString("text");

                    if (!msg.isEmpty()) {
                        uiHandler.post(() -> {
                            if (type.equals("partial")) {
                                tvTranscription.setText(fullTranscript.toString() + msg);
                            } else if (type.equals("final")) {
                                fullTranscript.append(msg).append(" ");
                                tvTranscription.setText(fullTranscript.toString());
                            }
                            scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void startRecordingAndSending(WebSocket socket) {
        int bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        recorder = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
        );

        recorder.startRecording();

        new Thread(() -> {
            byte[] buffer = new byte[bufferSize];
            while (isStreaming) {
                int read = recorder.read(buffer, 0, buffer.length);
                if (read > 0) {
                    socket.send(ByteString.of(buffer, 0, read));
                }
            }
        }).start();
    }

    private void stopStreaming() {
        isStreaming = false;
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);

        if (recorder != null) {
            recorder.stop();
            recorder.release();
            recorder = null;
        }

        if (mediaRecorder != null) {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
        }

        if (webSocket != null) {
            webSocket.close(1000, "Stopped by user");
            webSocket = null;
        }

        btnPlay.setEnabled(true);
        btnStopAudio.setEnabled(false);

        askFilenameAfterRecording();
    }

    private void askFilenameAfterRecording() {
        EditText input = new EditText(this);
        input.setHint("e.g. meeting_notes");

        new AlertDialog.Builder(this)
                .setTitle("Save Recording")
                .setMessage("Enter a filename:")
                .setView(input)
                .setCancelable(false)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        saveFilesWithName(name);
                    } else {
                        Toast.makeText(this, "Filename cannot be empty", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveFilesWithName(String baseName) {
        try {
            File newAudio = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), baseName + ".3gp");
            if (audioFile.exists()) {
                boolean renamed = audioFile.renameTo(newAudio);
                if (renamed) {
                    audioFile = newAudio;
                }
            }

            File transcriptFile = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), baseName + ".txt");
            FileWriter writer = new FileWriter(transcriptFile);
            writer.write(fullTranscript.toString().trim());
            writer.close();

            Toast.makeText(this, "Saved as: " + baseName, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void playAudio() {
        if (audioFile != null && audioFile.exists()) {
            mediaPlayer = new MediaPlayer();
            try {
                mediaPlayer.setDataSource(audioFile.getAbsolutePath());
                mediaPlayer.prepare();
                mediaPlayer.start();
                btnPlay.setEnabled(false);
                btnStopAudio.setEnabled(true);

                mediaPlayer.setOnCompletionListener(mp -> {
                    btnPlay.setEnabled(true);
                    btnStopAudio.setEnabled(false);
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            Toast.makeText(this, "Audio file not found!", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopAudio() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
            btnPlay.setEnabled(true);
            btnStopAudio.setEnabled(false);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show();
        }
    }
}
