package com.example;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.geometry.Pos;

public class OnlinePage {
    private Stage primaryStage;
    private Label statusLabel;
    private Button backButton;

    public OnlinePage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public Scene createOnlineScene() {
        Pane layout = new Pane();

        // 創建狀態標籤
        statusLabel = new Label("等待連線...");
        statusLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        statusLabel.setTextFill(Color.WHITE);
        statusLabel.setLayoutX(20);
        statusLabel.setLayoutY(20);

        // 創建返回按鈕
        backButton = new Button("返回主選單");
        backButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-size: 14px;");
        backButton.setLayoutX(20);
        backButton.setLayoutY(60);
        backButton.setOnAction(e -> {
            MainPage mainPage = new MainPage(primaryStage);
            primaryStage.setScene(mainPage.createMainScene());
        });

        // 創建房間頁面
        RoomPage roomPage = new RoomPage();
        VBox roomLayout = roomPage.createRoomPage(primaryStage);
        roomLayout.setLayoutX(250);
        roomLayout.setLayoutY(150);

        // 添加所有元素到佈局
        layout.getChildren().addAll(statusLabel, backButton, roomLayout);
        layout.setStyle("-fx-background-color: #2C3E50;");

        return new Scene(layout, 800, 600);
    }

    public void updateStatus(String status) {
        statusLabel.setText(status);
    }
}