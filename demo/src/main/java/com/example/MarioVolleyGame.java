package com.example;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MarioVolleyGame extends Application {
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Mario Volley Game");

        ModeSelectionPage modeSelectionPage = new ModeSelectionPage(primaryStage);
        Scene scene = modeSelectionPage.createModeSelectionScene();

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
