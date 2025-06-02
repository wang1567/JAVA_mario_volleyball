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
import javafx.application.Platform;

public class RoomPage {
    private Stage primaryStage;
    private TextField roomNameField;
    private TextField serverIPField;
    private Label statusLabel;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private static final int PORT = 12345;

    private Button createRoomButton;
    private Button joinRoomButton;
    private Button readyButton;

    private boolean isHost = false;

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
        createRoomButton = new Button("創建房間");
        joinRoomButton = new Button("加入房間");
        readyButton = new Button("準備");
        createRoomButton.setStyle("-fx-background-color: #27AE60; -fx-text-fill: white;");
        joinRoomButton.setStyle("-fx-background-color: #2980B9; -fx-text-fill: white;");
        readyButton.setStyle("-fx-background-color: #F39C12; -fx-text-fill: white;");

        // 狀態標籤
        statusLabel = new Label("");
        statusLabel.setTextFill(Color.WHITE);

        // 設置按鈕事件
        createRoomButton.setOnAction(e -> createRoom());
        joinRoomButton.setOnAction(e -> joinRoom());
        readyButton.setOnAction(e -> sendReady());

        // 初始狀態：準備按鈕不可見或禁用
        readyButton.setVisible(false);
        readyButton.setDisable(true);

        // 添加所有元素到佈局
        layout.getChildren().addAll(
                serverIPLabel,
                serverIPField,
                roomNameLabel,
                roomNameField,
                createRoomButton,
                joinRoomButton,
                readyButton,
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

            Platform.runLater(() -> {
                statusLabel.setText("已連接到伺服器");
                statusLabel.setTextFill(Color.GREEN);
            });

        } catch (IOException e) {
            Platform.runLater(() -> {
                statusLabel.setText("無法連接到伺服器: " + e.getMessage());
                statusLabel.setTextFill(Color.RED);
            });
        }
    }

    private void createRoom() {
        if (socket == null || !socket.isConnected()) {
            connectToServer();
        }

        String roomName = roomNameField.getText().trim();
        if (roomName.isEmpty()) {
            Platform.runLater(() -> {
                statusLabel.setText("請輸入房間名稱");
                statusLabel.setTextFill(Color.RED);
            });
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
            Platform.runLater(() -> {
                statusLabel.setText("請輸入房間名稱");
                statusLabel.setTextFill(Color.RED);
            });
            return;
        }

        out.println("JOIN_ROOM:" + roomName);
    }

    private void sendReady() {
        if (socket != null && out != null) {
            out.println("READY");
            Platform.runLater(() -> {
                readyButton.setDisable(true);
                statusLabel.setText("已準備，等待對手...");
                statusLabel.setTextFill(Color.ORANGE);
            });
        }
    }

    private void receiveMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                final String finalMessage = message;
                Platform.runLater(() -> {
                    if (finalMessage.startsWith("ERROR:")) {
                        statusLabel.setText(finalMessage.substring(6));
                        statusLabel.setTextFill(Color.RED);
                    } else if (finalMessage.startsWith("ROOM_CREATED:")) {
                        statusLabel.setText("房間創建成功，等待對手加入...");
                        statusLabel.setTextFill(Color.GREEN);
                        // 房間創建成功後顯示準備按鈕
                        readyButton.setVisible(true);
                        readyButton.setDisable(false);
                        // 禁用創建和加入按鈕
                        createRoomButton.setDisable(true);
                        joinRoomButton.setDisable(true);
                        serverIPField.setDisable(true);
                        roomNameField.setDisable(true);
                        isHost = true; // 設置為主機
                    } else if (finalMessage.startsWith("ROOM_JOINED:")) {
                        statusLabel.setText("成功加入房間，等待遊戲開始...");
                        statusLabel.setTextFill(Color.GREEN);
                        // 成功加入房間後顯示準備按鈕
                        readyButton.setVisible(true);
                        readyButton.setDisable(false);
                        // 禁用創建和加入按鈕
                        createRoomButton.setDisable(true);
                        joinRoomButton.setDisable(true);
                        serverIPField.setDisable(true);
                        roomNameField.setDisable(true);
                        isHost = false; // 設置為客機
                    } else if (finalMessage.equals("OPPONENT_JOINED")) {
                        statusLabel.setText("對手已加入，請準備！");
                        statusLabel.setTextFill(Color.ORANGE);
                    } else if (finalMessage.equals("HOST_READY")) {
                        if (!readyButton.isDisable()) {
                            statusLabel.setText("對手已準備，請點擊準備按鈕！");
                            statusLabel.setTextFill(Color.ORANGE);
                        }
                    } else if (finalMessage.equals("GUEST_READY")) {
                        if (!readyButton.isDisable()) {
                            statusLabel.setText("對手已準備，請點擊準備按鈕！");
                            statusLabel.setTextFill(Color.ORANGE);
                        }
                    } else if (finalMessage.equals("GAME_START")) {
                        startGame();
                    } else if (finalMessage.equals("OPPONENT_DISCONNECTED")) {
                        statusLabel.setText("對手已斷開連線");
                        statusLabel.setTextFill(Color.RED);
                        readyButton.setVisible(false);
                        readyButton.setDisable(true);
                        serverIPField.setDisable(false);
                        roomNameField.setDisable(false);
                    }
                });
            }
        } catch (IOException e) {
            Platform.runLater(() -> {
                statusLabel.setText("與伺服器的連線已斷開");
                statusLabel.setTextFill(Color.RED);
                readyButton.setVisible(false);
                readyButton.setDisable(true);
                serverIPField.setDisable(false);
                roomNameField.setDisable(false);
            });
        }
    }

    private void startGame() {
        // 啟動遊戲邏輯
        // 在伺服器權威模式下，GamePage 會根據伺服器狀態自動更新
        // 這裡只需要切換到遊戲場景
        GamePage gamePage = new GamePage(isHost, true, socket, primaryStage); // 傳遞 isHost, true, socket, primaryStage
        primaryStage.setScene(gamePage.createGameScene());
    }
}
