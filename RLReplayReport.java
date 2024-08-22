package org.example;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import okhttp3.*;

public class ReplayReport {
    public static void main(String[] args) {
        JFrame frame = new JFrame("Replay Report");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 400);

        JPanel panel = new JPanel();
        frame.add(panel);
        placeComponents(panel);

        frame.setVisible(true);
    }

    private static void placeComponents(JPanel panel) {
        panel.setLayout(new BorderLayout());

        // Add a file chooser
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fileChooser.setFileFilter(new FileNameExtensionFilter("JSON Files", "json"));
        panel.add(fileChooser, BorderLayout.CENTER);

        JButton submitButton = new JButton("Submit");
        panel.add(submitButton, BorderLayout.SOUTH);

        submitButton.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                File selectedFile = fileChooser.getSelectedFile();
                if (selectedFile != null) {
                    if (selectedFile.isFile()) {
                        try {
                            JsonObject jsonData = new Gson().fromJson(new FileReader(selectedFile), JsonObject.class);
                            submitData(jsonData);
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    } else if (selectedFile.isDirectory()) {
                        File[] files = selectedFile.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
                        if (files != null) {
                            for (File file : files) {
                                try {
                                    JsonObject jsonData = new Gson().fromJson(new FileReader(file), JsonObject.class);
                                    submitData(jsonData);
                                } catch (IOException ex) {
                                    ex.printStackTrace();
                                }
                            }
                        }
                    }
                } else {
                    JOptionPane.showMessageDialog(panel, "Please select a JSON file or a folder containing JSON files.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }
    private static void moveFileToSubmittedFolder(File file) {
        File submittedFolder = new File(file.getParentFile(), "submitted");
        if (!submittedFolder.exists()) {
            submittedFolder.mkdir();
        }

        File newFile = new File(submittedFolder, file.getName());
        try {
            Files.move(file.toPath(), newFile.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private static void submitData(JsonObject jsonData) {
        String url = "https://rl.XXXXXXXXXXXXXXX.com/replay_report.php";
        JsonObject properties = jsonData.getAsJsonObject("Properties");
        JsonArray playerStats = properties.getAsJsonArray("PlayerStats");

        JsonObject postData = new JsonObject();

        int team1Score = properties.has("Team0Score") ? properties.get("Team0Score").getAsInt() : 0;
        int team2Score = properties.has("Team1Score") ? properties.get("Team1Score").getAsInt() : 0;

        postData.addProperty("team1_score", team1Score);
        postData.addProperty("team2_score", team2Score);

        if (properties.has("Date")) {
            String fullDate = properties.get("Date").getAsString();
            String dateOnly = fullDate.split(" ")[0];
            postData.addProperty("date", dateOnly);
        } else {
            // Handle the case where "Date" is missing, if necessary
        }

        int t1PlayerCount = 1;
        int t2PlayerCount = 1;
        for (JsonElement playerStatElement : playerStats) {
            JsonObject playerStat = playerStatElement.getAsJsonObject();
            int team = playerStat.get("Team").getAsInt();
            if (team == 0) {
                postData.addProperty("team1_player" + t1PlayerCount, playerStat.get("Name").getAsString());
                postData.addProperty("t1p" + t1PlayerCount + "_score", playerStat.get("Score").getAsInt());
                t1PlayerCount++;
            } else {
                postData.addProperty("team2_player" + t2PlayerCount, playerStat.get("Name").getAsString());
                postData.addProperty("t2p" + t2PlayerCount + "_score", playerStat.get("Score").getAsInt());
                t2PlayerCount++;
            }
        }

        // Assign default values for missing players and their scores
        for (int i = t1PlayerCount; i <= 3; i++) {
            postData.addProperty("team1_player" + i, ""); // Changed to empty string
            postData.addProperty("t1p" + i + "_score", "");
        }
        for (int i = t2PlayerCount; i <= 3; i++) {
            postData.addProperty("team2_player" + i, ""); // Changed to empty string
            postData.addProperty("t2p" + i + "_score", "");
        }

        OkHttpClient client = new OkHttpClient();
        RequestBody requestBody = RequestBody.create(postData.toString(), MediaType.parse("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }


            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    JsonObject jsonResponse = new Gson().fromJson(responseBody, JsonObject.class);
                    String status = jsonResponse.get("status").getAsString();
                    String message = jsonResponse.get("message").getAsString();
                    System.out.println("Status: " + status + " - " + message);
                } else {
                    System.out.println("Error submitting data: " + response.message());
                }
            }
        });
    }
}
