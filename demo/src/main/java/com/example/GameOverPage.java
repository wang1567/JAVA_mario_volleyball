package com.example;

import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import java.net.Socket;

public class GameOverPage {
    private boolean isLeftPlayer;
    private boolean isOnline;
    private Socket socket;

    public GameOverPage(boolean isLeftPlayer, boolean isOnline, Socket socket) {
        this.isLeftPlayer = isLeftPlayer;
        this.isOnline = isOnline;
        this.socket = socket;
    }

    public VBox createGameOverPage(String winner, int player1Score, int player2Score, Stage primaryStage) {
        VBox layout = new VBox(20);
        layout.setStyle("-fx-background-color: lightgray; -fx-padding: 20; -fx-alignment: center;");

        Text resultText = new Text(winner + " 勝利！\n玩家1: " + player1Score + " - 玩家2: " + player2Score);
        resultText.setStyle("-fx-font-size: 24; -fx-font-weight: bold;");

        // 顯示排行榜
        Text leaderboardText = new Text("排行榜:\n1. 玩家1: 10分\n2. 玩家2: 8分"); // 示例數據
        leaderboardText.setStyle("-fx-font-size: 18;");

        Button restartButton = new Button("再玩一局");
        restartButton.setStyle("-fx-font-size: 18; -fx-padding: 10 20;");
        Button mainMenuButton = new Button("返回主菜單");
        mainMenuButton.setStyle("-fx-font-size: 18; -fx-padding: 10 20;");

        restartButton.setOnAction(e -> {
            GamePage newGame = new GamePage(isLeftPlayer, isOnline, socket, primaryStage);
            StackPane root = new StackPane(newGame.createGameScene().getRoot());
            primaryStage.setScene(new Scene(root, 800, 600));
        });

        mainMenuButton.setOnAction(e -> {
            ModeSelectionPage modeSelectionPage = new ModeSelectionPage(primaryStage);
            primaryStage.setScene(modeSelectionPage.createModeSelectionScene());
        });

        layout.getChildren().addAll(resultText, leaderboardText, restartButton, mainMenuButton);
        return layout;
    }
}
