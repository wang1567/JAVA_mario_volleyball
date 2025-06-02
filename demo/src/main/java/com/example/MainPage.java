package com.example;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.geometry.Pos;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.effect.DropShadow;

public class MainPage {
    private Stage primaryStage;

    public MainPage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public Scene createMainScene() {
        Pane mainLayout = new Pane();
        mainLayout.setPrefSize(800, 600);

        // --- 背景元素 ---
        // 天空
        Rectangle sky = new Rectangle(0, 0, 800, 600);
        sky.setFill(Color.web("#87CEEB"));
        mainLayout.getChildren().add(sky);

        // 遠處的山脈
        for (int i = 0; i < 4; i++) {
            double x = 50 + i * 200;
            double height = 150 + (i % 2) * 30;
            Polygon mountain = new Polygon(
                    x, 400,
                    x + 100, 400 - height,
                    x + 200, 400);
            mountain.setFill(Color.web("#8B4513"));
            mountain.setOpacity(0.7);
            mainLayout.getChildren().add(mountain);
        }

        // 裝飾性雲朵
        for (int i = 0; i < 6; i++) {
            double x = 30 + i * 150;
            double y = 50 + (i % 3) * 40;
            // 主雲朵
            Ellipse cloud = new Ellipse(x, y, 40, 20);
            cloud.setFill(Color.WHITE);
            cloud.setOpacity(0.9);
            mainLayout.getChildren().add(cloud);
            // 雲朵裝飾
            Ellipse cloud2 = new Ellipse(x + 30, y + 10, 25, 15);
            cloud2.setFill(Color.WHITE);
            cloud2.setOpacity(0.8);
            mainLayout.getChildren().add(cloud2);
            Ellipse cloud3 = new Ellipse(x - 20, y + 5, 20, 12);
            cloud3.setFill(Color.WHITE);
            cloud3.setOpacity(0.7);
            mainLayout.getChildren().add(cloud3);
        }

        // 草地
        Rectangle grass = new Rectangle(0, 400, 800, 200);
        grass.setFill(Color.web("#90EE90"));
        mainLayout.getChildren().add(grass);

        // 裝飾性樹木
        for (int i = 0; i < 5; i++) {
            double x = 80 + i * 180;
            // 樹幹
            Rectangle trunk = new Rectangle(x + 15, 350, 10, 30);
            trunk.setFill(Color.web("#8B4513"));
            mainLayout.getChildren().add(trunk);
            // 樹冠
            Circle leaves = new Circle(x + 20, 330, 25);
            leaves.setFill(Color.web("#228B22"));
            mainLayout.getChildren().add(leaves);
        }

        // 標題
        Text title = new Text("馬力歐排球");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 48));
        title.setFill(Color.WHITE);
        title.setStroke(Color.BLACK);
        title.setStrokeWidth(2);
        title.setX(250);
        title.setY(100);

        // 添加陰影效果
        DropShadow dropShadow = new DropShadow();
        dropShadow.setRadius(5.0);
        dropShadow.setOffsetX(3.0);
        dropShadow.setOffsetY(3.0);
        dropShadow.setColor(Color.color(0, 0, 0, 0.5));
        title.setEffect(dropShadow);

        mainLayout.getChildren().add(title);

        // 按鈕容器
        VBox buttonBox = new VBox(20);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setLayoutX(300);
        buttonBox.setLayoutY(200);

        // 創建按鈕
        Button singlePlayerButton = createStyledButton("單人模式");
        Button multiPlayerButton = createStyledButton("雙人模式");
        Button onlineButton = createStyledButton("線上對戰");

        // 添加按鈕事件
        singlePlayerButton.setOnAction(e -> {
            GamePage gamePage = new GamePage(true, false, null, primaryStage);
            primaryStage.setScene(gamePage.createGameScene());
        });

        multiPlayerButton.setOnAction(e -> {
            GamePage gamePage = new GamePage(true, false, null, primaryStage);
            primaryStage.setScene(gamePage.createGameScene());
        });

        onlineButton.setOnAction(e -> {
            OnlinePage onlinePage = new OnlinePage(primaryStage);
            primaryStage.setScene(onlinePage.createOnlineScene());
        });

        buttonBox.getChildren().addAll(singlePlayerButton, multiPlayerButton, onlineButton);
        mainLayout.getChildren().add(buttonBox);

        return new Scene(mainLayout, 800, 600);
    }

    private Button createStyledButton(String text) {
        Button button = new Button(text);
        button.setPrefWidth(200);
        button.setPrefHeight(50);
        button.setStyle(
                "-fx-background-color: #FF0000;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 20px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 10;" +
                        "-fx-border-color: #8B0000;" +
                        "-fx-border-width: 3;" +
                        "-fx-border-radius: 10;");

        // 添加懸停效果
        button.setOnMouseEntered(e -> button.setStyle(
                "-fx-background-color: #FF3333;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 20px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 10;" +
                        "-fx-border-color: #8B0000;" +
                        "-fx-border-width: 3;" +
                        "-fx-border-radius: 10;"));

        button.setOnMouseExited(e -> button.setStyle(
                "-fx-background-color: #FF0000;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 20px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 10;" +
                        "-fx-border-color: #8B0000;" +
                        "-fx-border-width: 3;" +
                        "-fx-border-radius: 10;"));

        return button;
    }
}