package com.example.test_model;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioRecord;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.tensorflow.lite.support.audio.TensorAudio;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.audio.classifier.AudioClassifier;
import org.tensorflow.lite.task.audio.classifier.Classifications;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private static final String MODEL_PATH = "pinyin_model(initial).tflite";
    private static final float PROBABILITY_THRESHOLD = 0.3f;
    private static final int REQUEST_RECORD_AUDIO = 2033;

    private AudioClassifier classifier;
    private TensorAudio tensor;
    private AudioRecord record;
    private TimerTask timerTask;
    private Timer timer;

    private TextView textViewSpec;
    private TextView textViewOutput;
    private Button startRecordingButton;
    private Button stopRecordingButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textViewSpec = findViewById(R.id.textViewSpec);
        textViewOutput = findViewById(R.id.textViewOutput);
        startRecordingButton = findViewById(R.id.buttonStartRecording);
        stopRecordingButton = findViewById(R.id.buttonStopRecording);

        stopRecordingButton.setEnabled(false);

        // Check for audio record permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
        }
    }

    public void onStartRecording(View view) {
        // Check permission again in case it was not granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            textViewOutput.setText("Audio recording permission is required.");
            return;
        }

        startRecordingButton.setEnabled(false);
        stopRecordingButton.setEnabled(true);

        try {
            classifier = AudioClassifier.createFromFile(this, MODEL_PATH);
            tensor = classifier.createInputTensorAudio();
            TensorAudio.TensorAudioFormat format = classifier.getRequiredTensorAudioFormat();
            String specs = "Number of channels: " + format.getChannels() + "\n" + "Sample Rate: " + format.getSampleRate();
            textViewSpec.setText(specs);

            record = classifier.createAudioRecord();
            record.startRecording();
        } catch (IOException e) {
            e.printStackTrace();
            textViewOutput.setText("Error loading model.");
            startRecordingButton.setEnabled(true);
            stopRecordingButton.setEnabled(false);
            return;
        }

        timerTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    tensor.load(record);

                    List<Classifications> output = classifier.classify(tensor);

                    List<Category> finalOutput = new ArrayList<>();
                    for (Classifications classifications : output) {
                        for (Category category : classifications.getCategories()) {
                            if (category.getScore() > PROBABILITY_THRESHOLD) {
                                finalOutput.add(category);
                            }
                        }
                    }

                    Collections.sort(finalOutput, (o1, o2) -> Float.compare(o2.getScore(), o1.getScore()));

                    StringBuilder outputStr = new StringBuilder();
                    for (Category category : finalOutput) {
                        outputStr.append(category.getLabel())
                                .append(": ").append(category.getScore()).append("\n");
                    }

                    runOnUiThread(() -> {
                        if (finalOutput.isEmpty()) {
                            textViewOutput.setText("Could not classify");
                        } else {
                            textViewOutput.setText(outputStr.toString());
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> textViewOutput.setText("Error processing audio."));
                }
            }
        };

        timer = new Timer();
        timer.schedule(timerTask, 0, 500);
    }

    public void onStopRecording(View view) {
        startRecordingButton.setEnabled(true);
        stopRecordingButton.setEnabled(false);

        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        if (timer != null) {
            timer.cancel();
            timer = null;
        }

        if (record != null) {
            record.stop();
            record.release();
            record = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            } else {
                textViewOutput.setText("Audio recording permission denied.");
                startRecordingButton.setEnabled(false);
            }
        }
    }
}
