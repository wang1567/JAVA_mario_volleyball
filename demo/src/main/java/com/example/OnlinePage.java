package com.example;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class OnlinePage {
    private Stage primaryStage;

    public OnlinePage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public Scene createOnlineScene() {
        Pane layout = new Pane();
        RoomPage roomPage = new RoomPage();
        VBox roomLayout = roomPage.createRoomPage(primaryStage);
        roomLayout.setLayoutX(250);
        roomLayout.setLayoutY(150);
        layout.getChildren().add(roomLayout);
        return new Scene(layout, 800, 600);
    }
}