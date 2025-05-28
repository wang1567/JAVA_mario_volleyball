package com.example;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class MarioVolleyGame extends Application {
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Mario Volley Game");

        ModeSelectionPage modeSelectionPage = new ModeSelectionPage();
        StackPane root = new StackPane(modeSelectionPage.createModeSelection(primaryStage));
        Scene scene = new Scene(root, 800, 600);

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
