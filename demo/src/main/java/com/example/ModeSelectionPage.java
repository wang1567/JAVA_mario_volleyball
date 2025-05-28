package com.example;

import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ModeSelectionPage {
    public VBox createModeSelection(Stage primaryStage) {
        VBox layout = new VBox(20);

        Button localButton = new Button("本地對戰");
        Button onlineButton = new Button("線上對戰");

        localButton.setOnAction(e -> {
            primaryStage.setScene(new GamePage(true, false, null).createGameScene());
        });

        onlineButton.setOnAction(e -> {
            primaryStage.setScene(new Scene(new RoomPage().createRoomPage(primaryStage), 800, 600));
        });

        layout.getChildren().addAll(localButton, onlineButton);
        return layout;
    }
}
