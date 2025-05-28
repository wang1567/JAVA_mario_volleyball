package com.example;

import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import javafx.scene.Scene;

public class GameOverPage {
    public VBox createGameOverPage(String winner, int player1Score, int player2Score, Stage primaryStage) {
        VBox layout = new VBox(20);
        layout.setStyle("-fx-background-color: lightgray; -fx-padding: 20;");
        Text resultText = new Text(winner + " 勝利！\n玩家1: " + player1Score + " - 玩家2: " + player2Score);
        resultText.setStyle("-fx-font-size: 24; -fx-font-weight: bold;");

        // 顯示排行榜
        Text leaderboardText = new Text("排行榜:\n1. 玩家1: 10分\n2. 玩家2: 8分"); // 示例數據
        leaderboardText.setStyle("-fx-font-size: 18;");

        Button restartButton = new Button("重新開始");
        Button mainMenuButton = new Button("返回主菜單");

        restartButton.setOnAction(e -> {
            primaryStage.setScene(new GamePage(true, false, null).createGameScene());
        });

        mainMenuButton.setOnAction(e -> {
            primaryStage.setScene(new Scene(new ModeSelectionPage().createModeSelection(primaryStage), 800, 600));
        });

        layout.getChildren().addAll(resultText, leaderboardText, restartButton, mainMenuButton);
        return layout;
    }
}
