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
    private Circle ball;
    private Text scoreText;
    private double ballSpeedY = 5;
    private double ballSpeedX = 4; // 新增為類別成員變數
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
    private static final double MOVE_DISTANCE = 8.0; // 降低移動距離，使移動更精確
    private long lastMoveTime = 0;
    private static final long MOVE_COOLDOWN = 8; // 降低冷卻時間，使移動更靈敏

    public GamePage(boolean isLeftPlayer, boolean isOnline, Socket socket) {
        this.isLeftPlayer = isLeftPlayer;
        this.isOnline = isOnline;
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
    }

    public Scene createGameScene() {
        Pane gameLayout = new Pane();
        // --- 背景 ---
        gameLayout.setStyle("-fx-background-color: #87ceeb;"); // 天空藍
        // --- 沙灘地板 ---
        Rectangle sand = new Rectangle(0, 550, 800, 50);
        sand.setFill(Color.web("#ffe4a1"));
        sand.setStroke(Color.web("#d2b48c"));
        sand.setStrokeWidth(2);
        gameLayout.getChildren().add(sand);
        // --- 場地分界線 ---
        Line leftLine = new Line(0, 550, 800, 550);
        leftLine.setStroke(Color.web("#d2b48c"));
        leftLine.setStrokeWidth(3);
        gameLayout.getChildren().add(leftLine);
        // --- 雲朵 ---
        for (int i = 0; i < 4; i++) {
            double x = 100 + i * 180;
            double y = 60 + (i % 2) * 30;
            javafx.scene.shape.Ellipse cloud = new javafx.scene.shape.Ellipse(x, y, 40, 18);
            cloud.setFill(Color.WHITE);
            cloud.setOpacity(0.8);
            gameLayout.getChildren().add(cloud);
            javafx.scene.shape.Ellipse cloud2 = new javafx.scene.shape.Ellipse(x + 30, y + 10, 25, 12);
            cloud2.setFill(Color.WHITE);
            cloud2.setOpacity(0.7);
            gameLayout.getChildren().add(cloud2);
        }
        // --- 排球網（圓柱+頂端圓） ---
        Rectangle net = new Rectangle(398, 400, 4, 150); // 細長圓柱，從400到550
        net.setFill(Color.SIENNA);
        net.setArcWidth(6);
        net.setArcHeight(6);
        gameLayout.getChildren().add(net);
        Circle netTop = new Circle(400, 400, 10, Color.WHITE); // 頂端圓
        netTop.setStroke(Color.SIENNA);
        netTop.setStrokeWidth(2);
        gameLayout.getChildren().add(netTop);
        // --- 其餘遊戲物件 ---
        startTime = System.currentTimeMillis();
        timerText = new Text("時間: 0");
        timerText.setLayoutX(50);
        timerText.setLayoutY(30);
        gameLayout.getChildren().add(timerText);
        // Mario永遠在左，Luigi永遠在右
        player1 = new Character(Color.RED, Color.BLUE); // Mario
        player1.setTranslateX(50);
        player1.setTranslateY(600 - Character.CHARACTER_SIZE);
        player2 = new Character(Color.GREEN, Color.BLUE); // Luigi
        player2.setTranslateX(750);
        player2.setTranslateY(600 - Character.CHARACTER_SIZE);
        player2.setTargetX(750);
        ball = new Circle(30, Color.GREEN);
        ball.setCenterX(400);
        ball.setCenterY(100);
        scoreText = new Text("左側玩家: " + player1Score + " - 右側玩家: " + player2Score);
        scoreText.setLayoutX(300);
        scoreText.setLayoutY(50);
        scoreText.setStyle("-fx-font-size: 20; -fx-font-weight: bold;");
        showInstructions(gameLayout);
        gameLayout.getChildren().addAll(player1, player2, ball, scoreText);
        // --- AnimationTimer ---
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                player1.update();
                player2.update();

                // 更新球的位置
                ball.setCenterX(ball.getCenterX() + ballSpeedX);
                ball.setCenterY(ball.getCenterY() + ballSpeedY);
                ballSpeedY += 0.5;

                // 檢查球是否落地
                if (ball.getCenterY() >= 600 - ball.getRadius()) {
                    // 判斷球落在哪一邊
                    if (ball.getCenterX() < NET_X) {
                        // 球落在左側，右側玩家得分
                        player2Score++;
                        if (isOnline) {
                            sendAction("SCORE:" + player1Score + ":" + player2Score);
                        }
                    } else {
                        // 球落在右側，左側玩家得分
                        player1Score++;
                        if (isOnline) {
                            sendAction("SCORE:" + player1Score + ":" + player2Score);
                        }
                    }
                    // 重置球的位置
                    resetBall();
                    // 更新計分板
                    scoreText.setText("左側玩家: " + player1Score + " - 右側玩家: " + player2Score);
                }

                // 球碰到左右邊界
                if (ball.getCenterX() <= ball.getRadius() || ball.getCenterX() >= 800 - ball.getRadius()) {
                    ballSpeedX *= -1;
                }

                // 球碰到網子
                if (ball.getCenterX() >= NET_X - NET_WIDTH / 2 &&
                        ball.getCenterX() <= NET_X + NET_WIDTH / 2 &&
                        ball.getCenterY() >= NET_TOP_Y &&
                        ball.getCenterY() <= NET_BOTTOM_Y) {
                    // 反彈效果
                    ballSpeedX *= -1.1; // 反彈且加強彈力
                    ballSpeedY *= 0.9; // 稍微減弱垂直速度
                }

                // 球碰到網子頂端圓
                double dx = ball.getCenterX() - NET_X;
                double dy = ball.getCenterY() - NET_TOP_Y;
                if (Math.sqrt(dx * dx + dy * dy) <= ball.getRadius() + 10) {
                    // 強力反彈
                    ballSpeedY = -Math.abs(ballSpeedY) * 1.2;
                    ballSpeedX *= 1.1;
                }

                // 球碰到玩家
                if (ball.getBoundsInParent().intersects(player1.getBoundsInParent())) {
                    ballSpeedY = -16;
                    ballSpeedX = 7;
                }
                if (ball.getBoundsInParent().intersects(player2.getBoundsInParent())) {
                    ballSpeedY = -16;
                    ballSpeedX = -7;
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
                    if (msg.startsWith("SCORE:")) {
                        String[] scores = msg.substring(6).split(":");
                        Platform.runLater(() -> {
                            player1Score = Integer.parseInt(scores[0]);
                            player2Score = Integer.parseInt(scores[1]);
                            scoreText.setText("左側玩家: " + player1Score + " - 右側玩家: " + player2Score);
                        });
                    } else if (msg.startsWith("BALL_DIRECTION:")) {
                        String direction = msg.substring(14);
                        Platform.runLater(() -> {
                            ballSpeedX = 4 * (direction.equals("RIGHT") ? 1 : -1);
                            ballSpeedY = -10;
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
        ball.setCenterX(400);
        ball.setCenterY(100);
        if (isOnline) {
            // 在線上模式中，向伺服器請求發球方向
            sendAction("RESET_BALL:");
        } else {
            // 在本地模式中，使用本地隨機數
            ballSpeedX = 4 * (Math.random() > 0.5 ? 1 : -1);
            ballSpeedY = -10;
        }
    }
}
