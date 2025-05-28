package com.example;

import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.text.Text;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import javafx.application.Platform;

public class RoomPage {
    private Text statusText;
    private Button createRoomButton;
    private Button joinRoomButton;
    private TextField roomNameField;
    private boolean isWaitingForOpponent = false;

    public VBox createRoomPage(Stage primaryStage) {
        VBox layout = new VBox(20);

        roomNameField = new TextField("輸入房間名稱");
        createRoomButton = new Button("創建房間");
        joinRoomButton = new Button("加入房間");
        statusText = new Text("請輸入房間名稱並選擇創建或加入房間。");

        createRoomButton.setOnAction(e -> {
            String roomName = roomNameField.getText();
            if (roomName.isEmpty()) {
                statusText.setText("請輸入房間名稱！");
                return;
            }
            createRoom(roomName, primaryStage);
        });

        joinRoomButton.setOnAction(e -> {
            String roomName = roomNameField.getText();
            if (roomName.isEmpty()) {
                statusText.setText("請輸入房間名稱！");
                return;
            }
            joinRoom(roomName, primaryStage);
        });

        layout.getChildren().addAll(statusText, roomNameField, createRoomButton, joinRoomButton);
        return layout;
    }

    private void createRoom(String roomName, Stage primaryStage) {
        try {
            Socket socket = connectToServer("localhost");
            if (socket != null) {
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // 發送創建房間請求
                out.println("CREATE_ROOM:" + roomName);

                // 等待對手加入
                Platform.runLater(() -> statusText.setText("等待對手加入房間..."));
                isWaitingForOpponent = true;

                // 監聽伺服器回應
                new Thread(() -> {
                    try {
                        String response;
                        while ((response = in.readLine()) != null) {
                            String currentResponse = response;
                            if (currentResponse.startsWith("ROOM_CREATED:")) {
                                Platform.runLater(() -> statusText.setText("房間創建成功！等待對手加入..."));
                            } else if (currentResponse.startsWith("OPPONENT_JOINED:")) {
                                Platform.runLater(() -> {
                                    statusText.setText("對手已加入！遊戲開始！");
                                    primaryStage.setScene(new GamePage(true, true, socket).createGameScene());
                                });
                                break;
                            }
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        Platform.runLater(() -> statusText.setText("連接錯誤！"));
                    }
                }).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Platform.runLater(() -> statusText.setText("無法連接到伺服器！"));
        }
    }

    private void joinRoom(String roomName, Stage primaryStage) {
        try {
            Socket socket = connectToServer("localhost");
            if (socket != null) {
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // 發送加入房間請求
                out.println("JOIN_ROOM:" + roomName);

                // 監聽伺服器回應
                new Thread(() -> {
                    try {
                        String response;
                        while ((response = in.readLine()) != null) {
                            String currentResponse = response;
                            if (currentResponse.startsWith("ROOM_JOINED:")) {
                                Platform.runLater(() -> statusText.setText("成功加入房間！等待遊戲開始..."));
                            } else if (currentResponse.startsWith("GAME_START:")) {
                                Platform.runLater(() -> {
                                    statusText.setText("遊戲開始！");
                                    primaryStage.setScene(new GamePage(false, true, socket).createGameScene());
                                });
                                break;
                            } else if (currentResponse.startsWith("ERROR:")) {
                                Platform.runLater(() -> statusText.setText("錯誤：" + currentResponse.substring(6)));
                                break;
                            }
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        Platform.runLater(() -> statusText.setText("連接錯誤！"));
                    }
                }).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Platform.runLater(() -> statusText.setText("無法連接到伺服器！"));
        }
    }

    public Socket connectToServer(String serverAddress) {
        try {
            return new Socket(serverAddress, 12345);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
