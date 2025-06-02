package com.example;

import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.animation.AnimationTimer;
import javafx.stage.Stage;
import javafx.scene.Scene;
import java.io.*;
import java.net.*;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Line;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.geometry.Bounds;

// 角色基類 (簡化，主要用於顯示)
class Character extends Pane {
    public static final double CHARACTER_WIDTH = 64;
    public static final double CHARACTER_HEIGHT = 64;
    private Pane characterBody;
    // 移除本地物理相關變數

    public Character(Color hatColor, Color overallsColor) {
        characterBody = new Pane();
        characterBody.setPrefSize(CHARACTER_WIDTH, CHARACTER_HEIGHT);

        double pixelUnit = CHARACTER_WIDTH / 16.0; // 使用 CHARACTER_WIDTH 計算 pixelUnit

        // 顏色定義
        Color SKIN = Color.web("#FFCC99");
        Color BROWN = Color.web("#8B4513");
        Color WHITE = Color.web("#FFFFFF");
        Color YELLOW = Color.web("#FFFF00");

        // 繪製帽子
        addPixelRect(characterBody, 4, 0, 8, 2, hatColor, pixelUnit);
        addPixelRect(characterBody, 3, 2, 10, 2, hatColor, pixelUnit);

        // 繪製頭髮
        addPixelRect(characterBody, 4, 4, 2, 2, BROWN, pixelUnit);
        addPixelRect(characterBody, 10, 4, 2, 2, BROWN, pixelUnit);

        // 繪製臉部
        addPixelRect(characterBody, 6, 4, 4, 2, SKIN, pixelUnit);
        addPixelRect(characterBody, 5, 6, 6, 2, SKIN, pixelUnit);

        // 繪製眼睛
        addPixelRect(characterBody, 7, 5, 1, 1, WHITE, pixelUnit);
        addPixelRect(characterBody, 8, 5, 1, 1, WHITE, pixelUnit);

        // 繪製小鬍子
        addPixelRect(characterBody, 6, 7, 4, 1, BROWN, pixelUnit);

        // 繪製襯衫
        addPixelRect(characterBody, 5, 8, 6, 2, hatColor, pixelUnit);

        // 繪製吊帶褲
        addPixelRect(characterBody, 4, 10, 8, 4, overallsColor, pixelUnit);

        // 繪製鈕扣
        addPixelRect(characterBody, 6, 11, 1, 1, YELLOW, pixelUnit);
        addPixelRect(characterBody, 9, 11, 1, 1, YELLOW, pixelUnit);

        // 繪製手臂
        addPixelRect(characterBody, 3, 9, 2, 3, SKIN, pixelUnit);
        addPixelRect(characterBody, 11, 9, 2, 3, SKIN, pixelUnit);

        // 繪製腿
        addPixelRect(characterBody, 5, 14, 2, 2, overallsColor, pixelUnit);
        addPixelRect(characterBody, 9, 14, 2, 2, overallsColor, pixelUnit);

        // 繪製鞋子
        addPixelRect(characterBody, 4, 15, 3, 1, BROWN, pixelUnit);
        addPixelRect(characterBody, 9, 15, 3, 1, BROWN, pixelUnit);

        this.getChildren().add(characterBody);
    }

    private void addPixelRect(Pane parent, int x, int y, int width, int height, Color color, double pixelUnit) {
        Rectangle rect = new Rectangle(x * pixelUnit, y * pixelUnit, width * pixelUnit, height * pixelUnit);
        rect.setFill(color);
        parent.getChildren().add(rect);
    }

    // 移除本地更新和跳躍方法
    // public void jump() { ... }
    // public void update() { ... }
    // public void setTargetX(double x) { ... }
}

public class GamePage {
    private int player1Score = 0;
    private int player2Score = 0;
    private Character player1;
    private Character player2;
    private ImageView ball;
    private Text scoreText;
    // 移除本地球速和計時器相關變數
    private Text timerText;
    private long startTime;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean isLeftPlayer; // 指示當前客戶端控制的是否是左側玩家 (Mario)
    private boolean isOnline;
    private Stage primaryStage;

    private static final double BALL_SIZE = 60; // 排球圖片大小
    private static final double CHARACTER_WIDTH = 64;
    private static final double CHARACTER_HEIGHT = 64;

    private boolean isGameOver = false;
    private boolean isWaitingForRematch = false;
    private long rematchRequestTime = 0;
    private static final long REMATCH_TIMEOUT = 5000; // 5秒超時
    private Text rematchText = null;
    private AnimationTimer rematchTimer = null;

    // 新增用於狀態插值的變數
    private double currentBallX, currentBallY, prevBallX, prevBallY;
    private double currentP1X, currentP1Y, prevP1X, prevP1Y;
    private double currentP2X, currentP2Y, prevP2X, prevP2Y;
    private long lastStateUpdateTime; // 記錄上次收到狀態的時間

    public GamePage(boolean isLeftPlayer, boolean isOnline, Socket socket, Stage primaryStage) {
        this.isLeftPlayer = isLeftPlayer;
        this.isOnline = isOnline;
        this.primaryStage = primaryStage; // 初始化

        if (isOnline && socket != null) {
            try {
                this.socket = socket;
                this.out = new PrintWriter(socket.getOutputStream(), true);
                this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                listenForMessages(); // 啟動接收伺服器狀態的執行緒
                startPingSender(); // 啟動發送心跳包的執行緒
            } catch (IOException e) {
                e.printStackTrace();
                // 顯示連線錯誤訊息並返回主選單
                Platform.runLater(() -> {
                    // 可以在這裡添加錯誤提示
                    ModeSelectionPage modeSelectionPage = new ModeSelectionPage(primaryStage);
                    primaryStage.setScene(modeSelectionPage.createModeSelectionScene());
                });
            }
        }

        // 初始化排球圖片
        Image ballImage = new Image(
                getClass().getResourceAsStream("/images/—Pngtree—volleyball ball cartoon hand painted_3843053.png"));
        ball = new ImageView(ballImage);
        ball.setFitWidth(BALL_SIZE);
        ball.setFitHeight(BALL_SIZE);
        // 球的初始位置將由伺服器決定

        // 初始化玩家角色 (位置將由伺服器決定)
        player1 = new Character(Color.RED, Color.BLUE); // Mario
        player2 = new Character(Color.GREEN, Color.BLUE); // Luigi
    }

    public Scene createGameScene() {
        Pane gameLayout = new Pane();

        // --- 背景 ---
        // 天空漸層
        Rectangle sky = new Rectangle(0, 0, 800, 600);
        sky.setFill(Color.web("#87CEEB"));
        gameLayout.getChildren().add(sky);

        // 遠處的山脈
        for (int i = 0; i < 3; i++) {
            double x = 100 + i * 250;
            double height = 150 + (i % 2) * 30;
            javafx.scene.shape.Polygon mountain = new javafx.scene.shape.Polygon(
                    x, 400,
                    x + 100, 400 - height,
                    x + 200, 400);
            mountain.setFill(Color.web("#8B4513"));
            mountain.setOpacity(0.7);
            gameLayout.getChildren().add(mountain);
        }

        // 裝飾性雲朵
        for (int i = 0; i < 5; i++) {
            double x = 50 + i * 180;
            double y = 50 + (i % 2) * 40;
            // 主雲朵
            javafx.scene.shape.Ellipse cloud = new javafx.scene.shape.Ellipse(x, y, 40, 20);
            cloud.setFill(Color.WHITE);
            cloud.setOpacity(0.9);
            gameLayout.getChildren().add(cloud);
            // 雲朵裝飾
            javafx.scene.shape.Ellipse cloud2 = new javafx.scene.shape.Ellipse(x + 30, y + 10, 25, 15);
            cloud2.setFill(Color.WHITE);
            cloud2.setOpacity(0.8);
            gameLayout.getChildren().add(cloud2);
            javafx.scene.shape.Ellipse cloud3 = new javafx.scene.shape.Ellipse(x - 20, y + 5, 20, 12);
            cloud3.setFill(Color.WHITE);
            cloud3.setOpacity(0.7);
            gameLayout.getChildren().add(cloud3);
        }

        // 草地
        Rectangle grass = new Rectangle(0, 400, 800, 200);
        grass.setFill(Color.web("#90EE90"));
        gameLayout.getChildren().add(grass);

        // 裝飾性樹木
        for (int i = 0; i < 4; i++) {
            double x = 100 + i * 200;
            // 樹幹
            Rectangle trunk = new Rectangle(x + 15, 350, 10, 30);
            trunk.setFill(Color.web("#8B4513"));
            gameLayout.getChildren().add(trunk);
            // 樹冠
            javafx.scene.shape.Circle leaves = new javafx.scene.shape.Circle(x + 20, 330, 25);
            leaves.setFill(Color.web("#228B22"));
            gameLayout.getChildren().add(leaves);
        }

        // 沙灘地板
        Rectangle sand = new Rectangle(0, 550, 800, 50);
        sand.setFill(Color.web("#F4A460"));
        sand.setStroke(Color.web("#D2B48C"));
        sand.setStrokeWidth(2);
        gameLayout.getChildren().add(sand);

        // 場地分界線
        Line leftLine = new Line(0, 550, 800, 550);
        leftLine.setStroke(Color.web("#D2B48C"));
        leftLine.setStrokeWidth(3);
        gameLayout.getChildren().add(leftLine);

        // --- 排球網（圓柱+頂端圓） ---
        Rectangle net = new Rectangle(398, 400, 4, 150);
        net.setFill(Color.SIENNA);
        net.setArcWidth(6);
        net.setArcHeight(6);
        gameLayout.getChildren().add(net);
        Circle netTop = new Circle(400, 400, 10, Color.WHITE);
        netTop.setStroke(Color.SIENNA);
        netTop.setStrokeWidth(2);
        gameLayout.getChildren().add(netTop);

        // --- 其餘遊戲物件 ---
        startTime = System.currentTimeMillis(); // 計時器由客戶端控制顯示，時間本身不是關鍵同步狀態
        timerText = new Text("時間: 0");
        timerText.setLayoutX(50);
        timerText.setLayoutY(30);
        timerText.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-fill: white;");
        gameLayout.getChildren().add(timerText);

        scoreText = new Text("左側玩家: " + player1Score + " - 右側玩家: " + player2Score);
        scoreText.setLayoutX(300);
        scoreText.setLayoutY(50);
        scoreText.setStyle(
                "-fx-font-size: 20; -fx-font-weight: bold; -fx-fill: white; -fx-stroke: black; -fx-stroke-width: 1;");
        // 遊戲說明文字將由伺服器狀態決定是否顯示
        // showInstructions(gameLayout);
        gameLayout.getChildren().addAll(player1, player2, ball, scoreText);

        // 移除 AnimationTimer 中的遊戲邏輯更新
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                // 移除本地玩家和球的 update() 調用
                // player1.update();
                // player2.update();

                // 移除本地球的運動和碰撞檢測邏輯
                // if (isOnline) { ... } else { ... }

                // 更新計時器顯示 (可以保留客戶端顯示)
                long elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
                timerText.setText("時間: " + elapsedTime);

                // --- 狀態插值 --- (僅在線上模式下進行)
                // 只有在接收到至少一個伺服器狀態後才進行更新
                if (isOnline && lastStateUpdateTime > 0) {
                    long currentTime = System.currentTimeMillis();
                    // 計算插值因子 (0 到 1)
                    // 理想情況下，elapsedTimeBetweenStates 應該接近 GAME_TICK_INTERVAL
                    double interpolationFactor = (double) (currentTime - lastStateUpdateTime)
                            / GameServer.GAME_TICK_INTERVAL;
                    // Clamp 插值因子在 0 到 1 之間，防止過度插值
                    interpolationFactor = Math.max(0, Math.min(1, interpolationFactor));

                    // 插值計算球的位置
                    double interpolatedBallX = prevBallX + (currentBallX - prevBallX) * interpolationFactor;
                    double interpolatedBallY = prevBallY + (currentBallY - prevBallY) * interpolationFactor;
                    ball.setX(interpolatedBallX);
                    ball.setY(interpolatedBallY);

                    // 插值計算玩家位置
                    double interpolatedP1X = prevP1X + (currentP1X - prevP1X) * interpolationFactor;
                    double interpolatedP1Y = prevP1Y + (currentP1Y - prevP1Y) * interpolationFactor;
                    player1.setTranslateX(interpolatedP1X);
                    player1.setTranslateY(interpolatedP1Y);

                    double interpolatedP2X = prevP2X + (currentP2X - prevP2X) * interpolationFactor;
                    double interpolatedP2Y = prevP2Y + (currentP2Y - prevP2Y) * interpolationFactor;
                    player2.setTranslateX(interpolatedP2X);
                    player2.setTranslateY(interpolatedP2Y);
                } else if (isOnline && lastStateUpdateTime == 0) { // 如果是第一次收到狀態，直接設置位置
                    ball.setX(currentBallX);
                    ball.setY(currentBallY);
                    player1.setTranslateX(currentP1X);
                    player1.setTranslateY(currentP1Y);
                    player2.setTranslateX(currentP2X);
                    player2.setTranslateY(currentP2Y);
                }
                // ----------------
            }
        };
        timer.start();
        Scene scene = new Scene(gameLayout, 800, 600);
        scene.setOnKeyPressed(this::handleKeyPress);
        return scene;
    }

    private void handleKeyPress(KeyEvent event) {
        if (isGameOver) {
            if (event.getCode() == KeyCode.R) {
                if (!isWaitingForRematch) {
                    // 發起重新開始請求給伺服器
                    isWaitingForRematch = true;
                    rematchRequestTime = System.currentTimeMillis();
                    sendAction("REMATCH_REQUEST");
                    showRematchText("等待對方同意重新開始...");
                    startRematchTimer();
                }
            }
            // 如果收到重新開始請求時按 R 鍵，同意請求
            if (isWaitingForRematch && event.getCode() == KeyCode.R) {
                acceptRematch();
            }
            return;
        }

        // 將玩家鍵盤輸入轉換為操作指令發送給伺服器
        if (isOnline) {
            if (isLeftPlayer) { // 控制玩家1 (Mario)
                switch (event.getCode()) {
                    case A:
                        sendAction("ACTION:LEFT");
                        break;
                    case D:
                        sendAction("ACTION:RIGHT");
                        break;
                    case W:
                        sendAction("ACTION:JUMP");
                        break;
                }
            } else { // 控制玩家2 (Luigi)
                switch (event.getCode()) {
                    case LEFT:
                        sendAction("ACTION:LEFT"); // 客戶端發送LEFT/RIGHT/JUMP，伺服器判斷是哪個玩家並處理
                        break;
                    case RIGHT:
                        sendAction("ACTION:RIGHT");
                        break;
                    case UP:
                        sendAction("ACTION:JUMP");
                        break;
                }
            }
        }
        // 單人模式的控制邏輯已移除
    }

    // 移除本地的 movePlayer 方法
    // private void movePlayer(Character player, double dx) { ... }

    private void sendAction(String action) {
        if (isOnline && out != null) {
            out.println(action);
        }
    }

    private void listenForMessages() {
        new Thread(() -> {
            try {
                String msg;
                while ((msg = in.readLine()) != null) {
                    final String finalMessage = msg;
                    System.out.println("GamePage 收到消息: " + finalMessage); // 新增日誌
                    Platform.runLater(() -> {
                        if (finalMessage.startsWith("STATE:")) {
                            // 處理伺服器廣播的遊戲狀態
                            System.out.println("GamePage 處理 STATE 消息..."); // 新增日誌
                            updateGameState(finalMessage.substring(6));
                        } else if (finalMessage.startsWith("SCORE:")) {
                            // 伺服器發送分數更新 (冗餘，狀態訊息已包含分數，但保留以兼容舊邏輯)
                            String[] scores = finalMessage.substring(6).split(":");
                            player1Score = Integer.parseInt(scores[0]);
                            player2Score = Integer.parseInt(scores[1]);
                            scoreText.setText("左側玩家: " + player1Score + " - 右側玩家: " + player2Score);
                        } else if (finalMessage.equals("REMATCH_REQUEST")) {
                            handleRematchRequest(); // 處理重新開始請求
                        } else if (finalMessage.equals("RESET_GAME")) {
                            // 伺服器通知重置遊戲
                            resetGame();
                        } else if (finalMessage.equals("OPPONENT_DISCONNECTED")) {
                            // 處理對手斷線
                            showGameOverText("對手已斷開連線");
                        } else if (finalMessage.startsWith("GAME_OVER:")) {
                            // 伺服器通知遊戲結束
                            String[] gameOverData = finalMessage.substring(10).split(":");
                            player1Score = Integer.parseInt(gameOverData[0]);
                            player2Score = Integer.parseInt(gameOverData[1]);
                            String winner = gameOverData[2];
                            showGameOverText(winner + "獲勝！");
                        }
                        // 移除 BALL_POS 和 BALL_DIRECTION 處理，這些信息包含在 STATE 訊息中
                        // } else if (finalMessage.startsWith("BALL_POS:")) { ... }
                        // } else if (finalMessage.startsWith("BALL_DIRECTION:")) { ... }
                        // 移除 MOVE 和 JUMP 處理，這些是客戶端發送給伺服器的動作指令
                        // } else if (finalMessage.startsWith("MOVE:")) { ... }
                        // } else if (finalMessage.equals("JUMP")) { ... }
                    });
                }
            } catch (IOException e) {
                // 與伺服器的連線斷開
                e.printStackTrace();
                Platform.runLater(() -> showGameOverText("與伺服器的連線已斷開"));
            }
        }).start();
    }

    // 新增方法：根據伺服器狀態更新遊戲畫面
    private void updateGameState(String stateMessage) {
        String[] stateData = stateMessage.split(":");
        // STATE:ballX:ballY:ballSpeedX:ballSpeedY:player1X:player1Y:player1VelocityY:player1IsJumping:player2X:player2Y:player2VelocityY:player2IsJumping:player1Score:player2Score:isBallReset:isGameOver
        if (stateData.length >= 16) { // 確保數據長度正確 (根據 GameServer 發送的 STATE 格式調整)
            try {
                // 如果是第一次收到狀態，則初始化 prev 和 current
                if (lastStateUpdateTime == 0) {
                    currentBallX = prevBallX = Double.parseDouble(stateData[0]);
                    currentBallY = prevBallY = Double.parseDouble(stateData[1]);
                    currentP1X = prevP1X = Double.parseDouble(stateData[4]);
                    currentP1Y = prevP1Y = Double.parseDouble(stateData[5]);
                    currentP2X = prevP2X = Double.parseDouble(stateData[8]);
                    currentP2Y = prevP2Y = Double.parseDouble(stateData[9]);
                } else {
                    // 將當前狀態保存為前一個狀態
                    prevBallX = currentBallX;
                    prevBallY = currentBallY;
                    prevP1X = currentP1X;
                    prevP1Y = currentP1Y;
                    prevP2X = currentP2X;
                    prevP2Y = currentP2Y;

                    // 解析伺服器發送的新的當前狀態
                    currentBallX = Double.parseDouble(stateData[0]);
                    currentBallY = Double.parseDouble(stateData[1]);
                    // double serverBallSpeedX = Double.parseDouble(stateData[2]); // 客戶端不需要球速
                    // double serverBallSpeedY = Double.parseDouble(stateData[3]); // 客戶端不需要球速
                    currentP1X = Double.parseDouble(stateData[4]);
                    currentP1Y = Double.parseDouble(stateData[5]);
                    // double serverPlayer1VelocityY = Double.parseDouble(stateData[6]); // 客戶端不需要
                    // boolean serverPlayer1IsJumping = Boolean.parseBoolean(stateData[7]); //
                    // 客戶端不需要
                    currentP2X = Double.parseDouble(stateData[8]);
                    currentP2Y = Double.parseDouble(stateData[9]);
                    // double serverPlayer2VelocityY = Double.parseDouble(stateData[10]); // 客戶端不需要
                    // boolean serverPlayer2IsJumping = Boolean.parseBoolean(stateData[11]); //
                    // 客戶端不需要
                }

                int serverPlayer1Score = Integer.parseInt(stateData[12]);
                int serverPlayer2Score = Integer.parseInt(stateData[13]);
                boolean serverIsBallReset = Boolean.parseBoolean(stateData[14]);
                boolean serverIsGameOver = Boolean.parseBoolean(stateData[15]);

                // 更新分數
                player1Score = serverPlayer1Score;
                player2Score = serverPlayer2Score;
                scoreText.setText("左側玩家: " + player1Score + " - 右側玩家: " + player2Score);

                // 處理遊戲結束和重置狀態
                if (serverIsGameOver && !isGameOver) { // 遊戲剛剛結束
                    Platform.runLater(() -> showGameOverText(
                            (serverPlayer1Score > serverPlayer2Score ? "左側玩家" : "右側玩家") + "獲勝！"));
                }
                isGameOver = serverIsGameOver;

                // 根據 isBallReset 狀態顯示/隱藏球 (伺服器控制發球延遲)
                ball.setVisible(!serverIsBallReset);

                // 更新上次收到狀態的時間
                lastStateUpdateTime = System.currentTimeMillis();

            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                System.err.println("解析遊戲狀態數據錯誤: " + stateMessage);
                e.printStackTrace();
            }
        } else {
            System.err.println("接收到無效的遊戲狀態訊息格式: " + stateMessage);
        }
    }

    // 移除 showInstructions 方法
    // private void showInstructions(Pane gameLayout) { ... }

    private void resetBall() {
        // 球的重置由伺服器控制，客戶端只根據狀態更新顯示
        // ball.setX(400 - BALL_SIZE / 2);
        // ball.setY(100 - BALL_SIZE / 2);
        // if (isOnline) { sendAction("RESET_BALL:"); } else { ... }
        // new Thread(() -> { ... }).start();
    }

    // 移除本地的碰撞檢測和遊戲結束判斷
    // private void checkGameOver() { ... }

    private void resetGame() {
        // 遊戲重置由伺服器發起和控制
        player1Score = 0;
        player2Score = 0;
        scoreText.setText("左側玩家: " + player1Score + " - 右側玩家: " + player2Score);
        isGameOver = false;
        isWaitingForRematch = false;
        ball.setVisible(true); // 顯示球
        // 移除遊戲結束文字和重新開始提示
        ((Pane) ball.getParent()).getChildren().removeIf(node -> node instanceof Text &&
                (((Text) node).getText().contains("獲勝") || ((Text) node).getText().contains("重新開始")));

        // 玩家位置重置 (可選，如果STATE訊息包含玩家初始位置則不需要)
        // player1.setTranslateX(50);
        // player1.setTranslateY(600 - CHARACTER_SIZE);
        // player2.setTranslateX(750);
        // player2.setTranslateY(600 - CHARACTER_SIZE);
        // player2.setTargetX(750);

        // 如果重新開始計時器在運行，停止它
        if (rematchTimer != null) {
            rematchTimer.stop();
        }
        // 重置插值狀態變數
        currentBallX = prevBallX = 0;
        currentBallY = prevBallY = 0;
        currentP1X = prevP1X = 0;
        currentP1Y = prevP1Y = 0;
        currentP2X = prevP2X = 0;
        currentP2Y = prevP2Y = 0;
        lastStateUpdateTime = 0;

    }

    private void showRematchText(String message) {
        Platform.runLater(() -> {
            if (rematchText != null) {
                ((Pane) ball.getParent()).getChildren().remove(rematchText);
            }
            rematchText = new Text(message);
            rematchText.setLayoutX(250);
            rematchText.setLayoutY(350);
            rematchText.setStyle(
                    "-fx-font-size: 24; -fx-font-weight: bold; -fx-fill: white; -fx-stroke: black; -fx-stroke-width: 2;");
            ((Pane) ball.getParent()).getChildren().add(rematchText);
        });
    }

    private void startRematchTimer() {
        if (rematchTimer != null) {
            rematchTimer.stop();
        }

        rematchTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (System.currentTimeMillis() - rematchRequestTime >= REMATCH_TIMEOUT) {
                    // 超時，返回主頁面
                    Platform.runLater(() -> {
                        if (rematchText != null) {
                            ((Pane) ball.getParent()).getChildren().remove(rematchText);
                        }
                        // 清理 Socket 連線
                        try {
                            if (socket != null && !socket.isClosed()) {
                                socket.close();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        ModeSelectionPage modeSelectionPage = new ModeSelectionPage(primaryStage);
                        primaryStage.setScene(modeSelectionPage.createModeSelectionScene());
                    });
                    rematchTimer.stop();
                }
            }
        };
        rematchTimer.start();
    }

    // 新增方法：處理遊戲結束顯示
    private void showGameOverText(String message) {
        isGameOver = true;
        ball.setVisible(false); // 隱藏球
        // 移除可能的重新開始提示
        if (rematchText != null) {
            ((Pane) ball.getParent()).getChildren().remove(rematchText);
        }

        Text gameOverText = new Text(message + "\n按R鍵重新開始");
        gameOverText.setLayoutX(200);
        gameOverText.setLayoutY(300);
        gameOverText.setStyle(
                "-fx-font-size: 30; -fx-font-weight: bold; -fx-fill: white; -fx-stroke: black; -fx-stroke-width: 2;");
        ((Pane) ball.getParent()).getChildren().add(gameOverText);

        // 如果重新開始計時器在運行，停止它
        if (rematchTimer != null) {
            rematchTimer.stop();
        }
    }

    // 新增方法：客戶端發送心跳包給伺服器
    private void startPingSender() {
        new Thread(() -> {
            while (isOnline && socket != null && !socket.isClosed()) {
                try {
                    out.println("PING");
                    Thread.sleep(2000); // 每2秒發送一次心跳
                } catch (Exception e) { // 捕獲 Exception 以解決 Linter 錯誤
                    System.out.println("心跳發送失敗或執行緒中斷: " + e.getMessage());
                    break;
                }
            }
        }).start();
    }

    // 處理收到對手重新開始請求
    private void handleRematchRequest() {
        showRematchText("對方請求重新開始\n按R鍵同意");
        isWaitingForRematch = true;
        rematchRequestTime = System.currentTimeMillis();
        startRematchTimer();
    }

    // 客戶端發送重新開始請求給伺服器
    private void requestRematch() {
        if (!isWaitingForRematch && isGameOver) { // 只有在遊戲結束且未發送請求時才能發送
            isWaitingForRematch = true;
            rematchRequestTime = System.currentTimeMillis();
            sendAction("REMATCH_REQUEST");
            showRematchText("等待對方同意重新開始...");
            startRematchTimer();
        }
    }

    // 客戶端同意重新開始請求
    private void acceptRematch() {
        if (isWaitingForRematch && isGameOver) { // 只有在等待對手同意且遊戲結束時才能同意
            sendAction("REMATCH_ACCEPT");
            // 客戶端本地先進行重置畫面的操作，實際遊戲重置由伺服器控制
            resetGame();
            // 移除重新開始提示文字
            if (rematchText != null) {
                ((Pane) ball.getParent()).getChildren().remove(rematchText);
            }
            isWaitingForRematch = false; // 重置等待狀態
            if (rematchTimer != null) {
                rematchTimer.stop();
            }
        }
    }
}
