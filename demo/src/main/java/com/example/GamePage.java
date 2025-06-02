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

// 角色基類
class Character extends Pane {
    public static final double CHARACTER_SIZE = 64;
    private Pane characterBody;
    private double velocityY = 0;
    private boolean isJumping = false;
    private static final double GRAVITY = 0.7;
    private static final double JUMP_POWER = -12;
    private double targetX; // 新增：目標位置
    private static final double MOVE_SPEED = 15.0; // 新增：移動速度
    private static final double INTERPOLATION_FACTOR = 0.5; // 新增：插值因子

    public Character(Color hatColor, Color overallsColor) {
        characterBody = new Pane();
        characterBody.setPrefSize(CHARACTER_SIZE, CHARACTER_SIZE);

        double pixelUnit = CHARACTER_SIZE / 16.0;

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

    public void jump() {
        if (!isJumping) {
            velocityY = JUMP_POWER;
            isJumping = true;
        }
    }

    public void update() {
        setTranslateY(getTranslateY() + velocityY);
        velocityY += GRAVITY;

        // 新增：平滑移動到目標位置
        if (Math.abs(getTranslateX() - targetX) > 1) {
            double newX = getTranslateX() + (targetX - getTranslateX()) * INTERPOLATION_FACTOR;
            setTranslateX(newX);
        }

        // 地面判斷（假設地面在600-CHARACTER_SIZE）
        if (getTranslateY() >= 600 - CHARACTER_SIZE) {
            setTranslateY(600 - CHARACTER_SIZE);
            velocityY = 0;
            isJumping = false;
        }
        // 修正角色不會超出左右邊界
        if (getTranslateX() < 0)
            setTranslateX(0);
        if (getTranslateX() > 800 - CHARACTER_SIZE)
            setTranslateX(800 - CHARACTER_SIZE);
    }

    public void setTargetX(double x) {
        this.targetX = x;
    }
}

public class GamePage {
    private int player1Score = 0;
    private int player2Score = 0;
    private Character player1;
    private Character player2;
    private ImageView ball;
    private Text scoreText;
    private double ballSpeedY = 3;
    private double ballSpeedX = 2;
    private Text timerText;
    private long startTime;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean isLeftPlayer;
    private boolean isOnline;
    private static final double NET_X = 400;
    private static final double NET_WIDTH = 4;
    private static final double NET_TOP_Y = 400;
    private static final double NET_BOTTOM_Y = 550;
    private static final double MOVE_DISTANCE = 12.0;
    private long lastMoveTime = 0;
    private static final long MOVE_COOLDOWN = 5;
    private static final int WINNING_SCORE = 11;
    private static final long BALL_SYNC_INTERVAL = 16;
    private long lastBallSyncTime = 0;
    private boolean isBallReset = false;
    private boolean isMaster = false;
    private static final double BALL_SIZE = 60; // 排球圖片大小
    private boolean isGameOver = false; // 新增：遊戲結束標記
    private boolean isWaitingForRematch = false;
    private long rematchRequestTime = 0;
    private static final long REMATCH_TIMEOUT = 5000; // 5秒超時
    private Text rematchText = null;
    private AnimationTimer rematchTimer = null;
    private Stage primaryStage; // 新增

    public GamePage(boolean isLeftPlayer, boolean isOnline, Socket socket, Stage primaryStage) {
        this.isLeftPlayer = isLeftPlayer;
        this.isOnline = isOnline;
        this.isMaster = isLeftPlayer;
        this.primaryStage = primaryStage; // 初始化
        if (isOnline && socket != null) {
            try {
                this.socket = socket;
                this.out = new PrintWriter(socket.getOutputStream(), true);
                this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                listenForMessages();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // 初始化排球圖片
        Image ballImage = new Image(
                getClass().getResourceAsStream("/images/—Pngtree—volleyball ball cartoon hand painted_3843053.png"));
        ball = new ImageView(ballImage);
        ball.setFitWidth(BALL_SIZE);
        ball.setFitHeight(BALL_SIZE);
        ball.setX(400 - BALL_SIZE / 2);
        ball.setY(100 - BALL_SIZE / 2);
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
        startTime = System.currentTimeMillis();
        timerText = new Text("時間: 0");
        timerText.setLayoutX(50);
        timerText.setLayoutY(30);
        timerText.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-fill: white;");
        gameLayout.getChildren().add(timerText);

        // Mario永遠在左，Luigi永遠在右
        player1 = new Character(Color.RED, Color.BLUE); // Mario
        player1.setTranslateX(50);
        player1.setTranslateY(600 - Character.CHARACTER_SIZE);
        player2 = new Character(Color.GREEN, Color.BLUE); // Luigi
        player2.setTranslateX(750);
        player2.setTranslateY(600 - Character.CHARACTER_SIZE);
        player2.setTargetX(750);

        scoreText = new Text("左側玩家: " + player1Score + " - 右側玩家: " + player2Score);
        scoreText.setLayoutX(300);
        scoreText.setLayoutY(50);
        scoreText.setStyle(
                "-fx-font-size: 20; -fx-font-weight: bold; -fx-fill: white; -fx-stroke: black; -fx-stroke-width: 1;");
        showInstructions(gameLayout);
        gameLayout.getChildren().addAll(player1, player2, ball, scoreText);

        // --- AnimationTimer ---
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                player1.update();
                player2.update();

                // 更新球的位置
                if (isOnline) {
                    if (isMaster) {
                        // 主控方計算球的運動
                        if (!isBallReset) {
                            ball.setX(ball.getX() + ballSpeedX);
                            ball.setY(ball.getY() + ballSpeedY);
                            ballSpeedY += 0.3;

                            // 檢查球是否落地
                            if (ball.getY() >= 600 - BALL_SIZE) {
                                if (!isBallReset) {
                                    isBallReset = true;
                                    // 判斷球落在哪一邊
                                    if (ball.getX() < NET_X - BALL_SIZE / 2) {
                                        player2Score++;
                                        sendAction("SCORE:" + player1Score + ":" + player2Score);
                                    } else {
                                        player1Score++;
                                        sendAction("SCORE:" + player1Score + ":" + player2Score);
                                    }
                                    resetBall();
                                    scoreText.setText("左側玩家: " + player1Score + " - 右側玩家: " + player2Score);
                                    checkGameOver(); // 檢查是否遊戲結束
                                }
                            }

                            // 球碰到左右邊界
                            if (ball.getX() <= 0 || ball.getX() >= 800 - BALL_SIZE) {
                                ballSpeedX *= -1;
                            }

                            // 球碰到網子
                            if (ball.getX() + BALL_SIZE / 2 >= NET_X - NET_WIDTH / 2 &&
                                    ball.getX() + BALL_SIZE / 2 <= NET_X + NET_WIDTH / 2 &&
                                    ball.getY() + BALL_SIZE / 2 >= NET_TOP_Y &&
                                    ball.getY() + BALL_SIZE / 2 <= NET_BOTTOM_Y) {
                                ballSpeedX *= -1.1;
                                ballSpeedY *= 0.9;
                            }

                            // 球碰到網子頂端圓
                            double dx = ball.getX() + BALL_SIZE / 2 - NET_X;
                            double dy = ball.getY() + BALL_SIZE / 2 - NET_TOP_Y;
                            if (Math.sqrt(dx * dx + dy * dy) <= BALL_SIZE / 2 + 10) {
                                ballSpeedY = -Math.abs(ballSpeedY) * 1.2;
                                ballSpeedX *= 1.1;
                            }

                            // 球碰到玩家
                            if (!isBallReset && ball.getBoundsInParent().intersects(player1.getBoundsInParent())) {
                                ballSpeedY = -10;
                                ballSpeedX = 4;
                            }
                            if (!isBallReset && ball.getBoundsInParent().intersects(player2.getBoundsInParent())) {
                                ballSpeedY = -10;
                                ballSpeedX = -4;
                            }

                            // 同步球的位置
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - lastBallSyncTime >= BALL_SYNC_INTERVAL) {
                                sendAction(String.format("BALL_POS:%.1f:%.1f:%.1f:%.1f:%b",
                                        ball.getX(), ball.getY(), ballSpeedX, ballSpeedY, isBallReset));
                                lastBallSyncTime = currentTime;
                            }
                        }
                    }
                } else {
                    // 單人模式的球運動邏輯
                    if (!isBallReset) {
                        ball.setX(ball.getX() + ballSpeedX);
                        ball.setY(ball.getY() + ballSpeedY);
                        ballSpeedY += 0.3;

                        // 檢查球是否落地
                        if (ball.getY() >= 600 - BALL_SIZE) {
                            if (!isBallReset) {
                                isBallReset = true;
                                if (ball.getX() < NET_X - BALL_SIZE / 2) {
                                    player2Score++;
                                } else {
                                    player1Score++;
                                }
                                resetBall();
                                scoreText.setText("左側玩家: " + player1Score + " - 右側玩家: " + player2Score);
                                checkGameOver(); // 檢查是否遊戲結束
                            }
                        }

                        // 其他碰撞檢測
                        if (ball.getX() <= 0 || ball.getX() >= 800 - BALL_SIZE) {
                            ballSpeedX *= -1;
                        }

                        if (ball.getX() + BALL_SIZE / 2 >= NET_X - NET_WIDTH / 2 &&
                                ball.getX() + BALL_SIZE / 2 <= NET_X + NET_WIDTH / 2 &&
                                ball.getY() + BALL_SIZE / 2 >= NET_TOP_Y &&
                                ball.getY() + BALL_SIZE / 2 <= NET_BOTTOM_Y) {
                            ballSpeedX *= -1.1;
                            ballSpeedY *= 0.9;
                        }

                        double dx = ball.getX() + BALL_SIZE / 2 - NET_X;
                        double dy = ball.getY() + BALL_SIZE / 2 - NET_TOP_Y;
                        if (Math.sqrt(dx * dx + dy * dy) <= BALL_SIZE / 2 + 10) {
                            ballSpeedY = -Math.abs(ballSpeedY) * 1.2;
                            ballSpeedX *= 1.1;
                        }

                        if (!isBallReset && ball.getBoundsInParent().intersects(player1.getBoundsInParent())) {
                            ballSpeedY = -10;
                            ballSpeedX = 4;
                        }
                        if (!isBallReset && ball.getBoundsInParent().intersects(player2.getBoundsInParent())) {
                            ballSpeedY = -10;
                            ballSpeedX = -4;
                        }
                    }
                }

                long elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
                timerText.setText("時間: " + elapsedTime);
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
                    // 發起重新開始請求
                    isWaitingForRematch = true;
                    rematchRequestTime = System.currentTimeMillis();
                    sendAction("REMATCH_REQUEST");
                    showRematchText("等待對方同意重新開始...");
                    startRematchTimer();
                }
            }
            return;
        }

        System.out.println("按下的鍵: " + event.getCode());
        if (isLeftPlayer) {
            // 只控制Mario
            switch (event.getCode()) {
                case A:
                    movePlayer(player1, -MOVE_DISTANCE);
                    sendAction("LEFT");
                    break;
                case D:
                    movePlayer(player1, MOVE_DISTANCE);
                    sendAction("RIGHT");
                    break;
                case W:
                    player1.jump();
                    sendAction("JUMP");
                    break;
            }
        } else {
            // 只控制Luigi
            switch (event.getCode()) {
                case LEFT:
                    movePlayer(player2, -MOVE_DISTANCE);
                    sendAction("LEFT");
                    break;
                case RIGHT:
                    movePlayer(player2, MOVE_DISTANCE);
                    sendAction("RIGHT");
                    break;
                case UP:
                    player2.jump();
                    sendAction("JUMP");
                    break;
            }
        }
    }

    private void movePlayer(Character player, double dx) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastMoveTime < MOVE_COOLDOWN) {
            return; // 如果冷卻時間未到，不執行移動
        }
        lastMoveTime = currentTime;

        double newX = player.getTranslateX() + dx;
        if (player == player1 && newX >= 0 && newX <= 400 - Character.CHARACTER_SIZE) {
            player.setTargetX(newX);
            if (isOnline) {
                sendAction("MOVE:" + newX);
            }
        }
        if (player == player2 && newX >= 400 && newX <= 800 - Character.CHARACTER_SIZE) {
            player.setTargetX(newX);
            if (isOnline) {
                sendAction("MOVE:" + newX);
            }
        }
    }

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
                    if (msg.equals("REMATCH_REQUEST")) {
                        Platform.runLater(() -> {
                            showRematchText("對方請求重新開始\n按R鍵同意");
                            isWaitingForRematch = true;
                            rematchRequestTime = System.currentTimeMillis();
                            startRematchTimer();
                        });
                    } else if (msg.equals("REMATCH_ACCEPT")) {
                        Platform.runLater(() -> {
                            if (rematchText != null) {
                                ((Pane) ball.getParent()).getChildren().remove(rematchText);
                            }
                            if (rematchTimer != null) {
                                rematchTimer.stop();
                            }
                            resetGame();
                        });
                    } else if (msg.equals("RESET_GAME")) {
                        Platform.runLater(() -> {
                            if (rematchText != null) {
                                ((Pane) ball.getParent()).getChildren().remove(rematchText);
                            }
                            if (rematchTimer != null) {
                                rematchTimer.stop();
                            }
                            resetGame();
                        });
                    } else if (msg.startsWith("SCORE:")) {
                        String[] scores = msg.substring(6).split(":");
                        Platform.runLater(() -> {
                            player1Score = Integer.parseInt(scores[0]);
                            player2Score = Integer.parseInt(scores[1]);
                            scoreText.setText("左側玩家: " + player1Score + " - 右側玩家: " + player2Score);
                            checkGameOver(); // 檢查是否遊戲結束
                        });
                    } else if (msg.startsWith("GAME_OVER:")) {
                        String[] gameOverData = msg.substring(10).split(":");
                        Platform.runLater(() -> {
                            player1Score = Integer.parseInt(gameOverData[0]);
                            player2Score = Integer.parseInt(gameOverData[1]);
                            String winner = gameOverData[2];
                            scoreText.setText("左側玩家: " + player1Score + " - 右側玩家: " + player2Score);

                            isGameOver = true;
                            Text gameOverText = new Text(winner + "獲勝！\n按R鍵重新開始");
                            gameOverText.setLayoutX(300);
                            gameOverText.setLayoutY(300);
                            gameOverText.setStyle(
                                    "-fx-font-size: 30; -fx-font-weight: bold; -fx-fill: white; -fx-stroke: black; -fx-stroke-width: 2;");
                            ((Pane) ball.getParent()).getChildren().add(gameOverText);

                            isBallReset = true;
                            ball.setVisible(false);
                        });
                    } else if (msg.startsWith("BALL_POS:")) {
                        String[] ballData = msg.substring(9).split(":");
                        Platform.runLater(() -> {
                            if (!isMaster) { // 只有非主控方更新球的位置
                                double newX = Double.parseDouble(ballData[0]);
                                double newY = Double.parseDouble(ballData[1]);
                                double newSpeedX = Double.parseDouble(ballData[2]);
                                double newSpeedY = Double.parseDouble(ballData[3]);
                                boolean newIsBallReset = Boolean.parseBoolean(ballData[4]);

                                ball.setX(newX - BALL_SIZE / 2);
                                ball.setY(newY - BALL_SIZE / 2);
                                ballSpeedX = newSpeedX;
                                ballSpeedY = newSpeedY;
                                isBallReset = newIsBallReset;
                            }
                        });
                    } else if (msg.startsWith("BALL_DIRECTION:")) {
                        String direction = msg.substring(14);
                        Platform.runLater(() -> {
                            ballSpeedX = 2 * (direction.equals("RIGHT") ? 1 : -1);
                            ballSpeedY = -6;
                        });
                    } else if (msg.startsWith("MOVE:")) {
                        double newX = Double.parseDouble(msg.substring(5));
                        Platform.runLater(() -> {
                            if (isLeftPlayer) {
                                // 只允許Luigi在右半場
                                if (newX >= 400 && newX <= 800 - Character.CHARACTER_SIZE) {
                                    player2.setTargetX(newX);
                                }
                            } else {
                                // 只允許Mario在左半場
                                if (newX >= 0 && newX <= 400 - Character.CHARACTER_SIZE) {
                                    player1.setTargetX(newX);
                                }
                            }
                        });
                    } else if (msg.equals("JUMP")) {
                        Platform.runLater(() -> {
                            if (isLeftPlayer) {
                                player2.jump(); // 第一玩家同步Luigi跳
                            } else {
                                player1.jump(); // 第二玩家同步Mario跳
                            }
                        });
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void showInstructions(Pane gameLayout) {
        Text instructions = new Text("控制玩家1: A (左), D (右), W (跳) | 控制玩家2: ← (左), → (右), ↑ (跳)");
        instructions.setLayoutX(50);
        instructions.setLayoutY(30);
        instructions.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");
        gameLayout.getChildren().add(instructions);
    }

    private void resetBall() {
        ball.setX(400 - BALL_SIZE / 2);
        ball.setY(100 - BALL_SIZE / 2);
        if (isOnline) {
            sendAction("RESET_BALL:");
            sendAction(String.format("BALL_POS:%.1f:%.1f:%.1f:%.1f:%b",
                    ball.getX(), ball.getY(), ballSpeedX, ballSpeedY, isBallReset));
        } else {
            ballSpeedX = 2 * (Math.random() > 0.5 ? 1 : -1);
            ballSpeedY = -6;
        }
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                isBallReset = false;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void checkGameOver() {
        if (player1Score >= WINNING_SCORE || player2Score >= WINNING_SCORE) {
            isGameOver = true;
            String winner = player1Score >= WINNING_SCORE ? "左側玩家" : "右側玩家";

            // 發送遊戲結束訊息給對手
            if (isOnline) {
                sendAction("GAME_OVER:" + player1Score + ":" + player2Score + ":" + winner);
            }

            Text gameOverText = new Text(winner + "獲勝！\n按R鍵重新開始");
            gameOverText.setLayoutX(300);
            gameOverText.setLayoutY(300);
            gameOverText.setStyle(
                    "-fx-font-size: 30; -fx-font-weight: bold; -fx-fill: white; -fx-stroke: black; -fx-stroke-width: 2;");
            ((Pane) ball.getParent()).getChildren().add(gameOverText);

            // 停止球的移動
            isBallReset = true;
            ball.setVisible(false);
        }
    }

    private void resetGame() {
        player1Score = 0;
        player2Score = 0;
        scoreText.setText("左側玩家: " + player1Score + " - 右側玩家: " + player2Score);
        isGameOver = false;
        isWaitingForRematch = false;
        ball.setVisible(true);
        resetBall();
        // 移除遊戲結束文字
        ((Pane) ball.getParent()).getChildren().removeIf(node -> node instanceof Text &&
                ((Text) node).getText().contains("獲勝"));

        // 通知對手遊戲重置
        if (isOnline) {
            sendAction("RESET_GAME");
        }
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
                        ModeSelectionPage modeSelectionPage = new ModeSelectionPage(primaryStage);
                        primaryStage.setScene(modeSelectionPage.createModeSelectionScene());
                    });
                    rematchTimer.stop();
                }
            }
        };
        rematchTimer.start();
    }
}
