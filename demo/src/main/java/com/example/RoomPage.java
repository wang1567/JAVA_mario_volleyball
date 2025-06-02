package com.example;

import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.geometry.Pos;
import javafx.scene.paint.Color;
import java.io.*;
import java.net.*;

public class RoomPage {
    private Stage primaryStage;
    private TextField roomNameField;
    private TextField serverIPField;
    private Label statusLabel;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private static final int PORT = 12345;

    public RoomPage() {
    }

    public VBox createRoomPage(Stage primaryStage) {
        this.primaryStage = primaryStage;
        VBox layout = new VBox(10);
        layout.setAlignment(Pos.CENTER);
        layout.setStyle("-fx-background-color: #34495E; -fx-padding: 20;");

        // 伺服器 IP 輸入
        Label serverIPLabel = new Label("伺服器 IP 地址:");
        serverIPLabel.setTextFill(Color.WHITE);
        serverIPField = new TextField();
        serverIPField.setPromptText("輸入伺服器 IP 地址");
        serverIPField.setStyle("-fx-background-color: white;");

        // 房間名稱輸入
        Label roomNameLabel = new Label("房間名稱:");
        roomNameLabel.setTextFill(Color.WHITE);
        roomNameField = new TextField();
        roomNameField.setPromptText("輸入房間名稱");
        roomNameField.setStyle("-fx-background-color: white;");

        // 按鈕
        Button createRoomButton = new Button("創建房間");
        Button joinRoomButton = new Button("加入房間");
        createRoomButton.setStyle("-fx-background-color: #27AE60; -fx-text-fill: white;");
        joinRoomButton.setStyle("-fx-background-color: #2980B9; -fx-text-fill: white;");

        // 狀態標籤
        statusLabel = new Label("");
        statusLabel.setTextFill(Color.WHITE);

        // 設置按鈕事件
        createRoomButton.setOnAction(e -> createRoom());
        joinRoomButton.setOnAction(e -> joinRoom());

        // 添加所有元素到佈局
        layout.getChildren().addAll(
                serverIPLabel,
                serverIPField,
                roomNameLabel,
                roomNameField,
                createRoomButton,
                joinRoomButton,
                statusLabel);

        return layout;
    }

    private void connectToServer() {
        try {
            String serverIP = serverIPField.getText().trim();
            if (serverIP.isEmpty()) {
                serverIP = "localhost";
            }
            socket = new Socket(serverIP, PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // 啟動接收消息的線程
            new Thread(this::receiveMessages).start();

            statusLabel.setText("已連接到伺服器");
            statusLabel.setTextFill(Color.GREEN);
        } catch (IOException e) {
            statusLabel.setText("無法連接到伺服器: " + e.getMessage());
            statusLabel.setTextFill(Color.RED);
        }
    }

    private void createRoom() {
        if (socket == null || !socket.isConnected()) {
            connectToServer();
        }

        String roomName = roomNameField.getText().trim();
        if (roomName.isEmpty()) {
            statusLabel.setText("請輸入房間名稱");
            statusLabel.setTextFill(Color.RED);
            return;
        }

        out.println("CREATE_ROOM:" + roomName);
    }

    private void joinRoom() {
        if (socket == null || !socket.isConnected()) {
            connectToServer();
        }

        String roomName = roomNameField.getText().trim();
        if (roomName.isEmpty()) {
            statusLabel.setText("請輸入房間名稱");
            statusLabel.setTextFill(Color.RED);
            return;
        }

        out.println("JOIN_ROOM:" + roomName);
    }

    private void receiveMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                final String finalMessage = message;
                javafx.application.Platform.runLater(() -> {
                    if (finalMessage.startsWith("ERROR:")) {
                        statusLabel.setText(finalMessage.substring(6));
                        statusLabel.setTextFill(Color.RED);
                    } else if (finalMessage.startsWith("ROOM_CREATED:")) {
                        statusLabel.setText("房間創建成功，等待對手加入...");
                        statusLabel.setTextFill(Color.GREEN);
                    } else if (finalMessage.startsWith("ROOM_JOINED:")) {
                        statusLabel.setText("成功加入房間，等待遊戲開始...");
                        statusLabel.setTextFill(Color.GREEN);
                    } else if (finalMessage.startsWith("OPPONENT_JOINED:")) {
                        statusLabel.setText("對手已加入，遊戲即將開始！");
                        statusLabel.setTextFill(Color.GREEN);
                        out.println("GAME_READY:" + finalMessage.substring(16));
                    } else if (finalMessage.equals("GAME_START")) {
                        startGame();
                    } else if (finalMessage.equals("OPPONENT_DISCONNECTED")) {
                        statusLabel.setText("對手已斷開連線");
                        statusLabel.setTextFill(Color.RED);
                    }
                });
            }
        } catch (IOException e) {
            javafx.application.Platform.runLater(() -> {
                statusLabel.setText("與伺服器的連線已斷開");
                statusLabel.setTextFill(Color.RED);
            });
        }
    }

    private void startGame() {
        // 啟動遊戲邏輯
        GamePage gamePage = new GamePage(true, true, socket, primaryStage);
        primaryStage.setScene(gamePage.createGameScene());
    }
}
