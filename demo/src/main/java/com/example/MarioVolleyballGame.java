package com.example;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.scene.input.KeyCode;
import java.util.HashSet;
import java.util.Set;
import javafx.geometry.Rectangle2D;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.media.AudioClip;
import java.net.URL;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;
import javafx.application.Platform;
// import java.text.DecimalFormat; // 導入 DecimalFormat 用於格式化浮點數 - 未使用
import java.util.concurrent.ConcurrentLinkedQueue; // 導入 ConcurrentLinkedQueue 處理網路訊息佇列
import java.util.ArrayList; // 導入 ArrayList
import java.util.List; // 導入 List 介面
import java.util.Iterator; // 導入 Iterator
// import java.util.Map; // 導入 Map - 未使用
// import java.util.HashMap; // 導入 HashMap - 未使用
import javafx.scene.control.ListView; // 導入 ListView
import javafx.scene.control.SelectionMode; // 導入 SelectionMode
import javafx.scene.layout.HBox; // 導入 HBox
import java.io.IOException; // 導入 IOException
import javafx.scene.control.TextArea; // 導入 TextArea
import javafx.scene.control.ScrollPane; // 導入 ScrollPane
import java.io.PrintWriter; // 導入 PrintWriter
import java.io.BufferedReader; // 導入 BufferedReader
import java.io.InputStreamReader; // 導入 InputStreamReader
import java.net.Socket; // 導入 Socket
// import java.util.Random; // 導入 Random for predicted hit - 未使用

// 遊戲客戶端應用程式類別
public class MarioVolleyballGame extends Application {

    // 遊戲視窗寬高
    private static final double WIDTH = 800;
    private static final double HEIGHT = 600;

    // Canvas 用於繪製遊戲內容 (仍然保留 Canvas 用於背景和一些簡單繪製)
    private Canvas gameCanvas;
    private GraphicsContext gc;

    // 遊戲迴圈計時器
    private AnimationTimer gameLoop;

    // 遊戲元素 (客戶端維護本地狀態，並與伺服器同步)
    private Player player1; // 玩家 1
    private Player player2; // 玩家 2
    private Ball ball; // 球

    // 物理參數 (客戶端用於本地預測，應與伺服器一致)
    private static final double GRAVITY = 800; // 重力加速度 (像素/秒^2)
    private static final double PLAYER_MOVE_SPEED = 250; // 玩家水平移動速度 (像素/秒)
    private static final double PLAYER_JUMP_STRENGTH = -450; // 玩家跳躍初始垂直速度 (像素/秒，負值表示向上)
    private static final double GROUND_FRICTION = 0.9; // 地面摩擦力 (影響水平速度的反彈)
    private static final double BOUNCE_FACTOR = 0.7; // 碰撞反彈係數 (影響垂直速度的反彈)
    private static final double HIT_STRENGTH = 350; // 擊球力量係數
    private static final double PLAYER_HIT_COOLDOWN = 0.3; // 玩家擊球冷卻時間 (秒)
    private static final double BALL_PLAYER_BOUNCE_FACTOR = 0.8; // 球與玩家碰撞後的速度影響因子
    private static final double BALL_NET_BOUNCE_FACTOR = 0.7; // 球與網碰撞後的反彈係數
    private static final double BALL_NET_HORIZONTAL_DECAY = 0.4; // 球與網水平碰撞後的水平速度衰減

    // 遊戲區域邊界和網的碰撞體
    // private final Rectangle2D groundBounds = new Rectangle2D(0, HEIGHT, WIDTH,
    // 1); // 地面 (高度為 1 像素，用於檢測是否落地) - 未使用
    // private final Rectangle2D leftWallBounds = new Rectangle2D(-1, 0, 1, HEIGHT);
    // // 左牆 (寬度為 1 像素) - 未使用
    // private final Rectangle2D rightWallBounds = new Rectangle2D(WIDTH, 0, 1,
    // HEIGHT); // 右牆 (寬度為 1 像素) - 未使用
    private final Rectangle2D netBounds = new Rectangle2D(WIDTH / 2 - 5, 0, 10, HEIGHT); // 球網

    // 處理鍵盤輸入
    private Set<KeyCode> activeKeys = new HashSet<>(); // 儲存當前按下的鍵
    private Set<KeyCode> previousKeys = new HashSet<>(); // 儲存上一幀按下的鍵 (用於檢測按鍵事件)

    // 比分 (從伺服器同步)
    private int scorePlayer1 = 0; // 玩家 1 比分
    private int scorePlayer2 = 0; // 玩家 2 比分
    // private static final int MAX_SCORE = 5; // 遊戲結束的比分 - 未使用

    // 遊戲狀態
    private enum GameState {
        SETUP, // 網路設置階段
        LOBBY, // 遊戲大廳階段
        IN_ROOM, // 在房間中等待或準備階段
        GAME, // 遊戲進行階段
        GAME_OVER // 遊戲結束階段
    }

    private GameState currentState = GameState.SETUP; // 當前遊戲狀態

    private boolean gameStarted = false; // 遊戲是否開始 (指實際對戰)
    private boolean gameOver = false;
    private String gameOverMessage = "";
    private boolean isConnected = false; // 標記網路是否連線成功
    private int myPlayerNumber = 0; // 當前玩家的編號 (1 或 2)
    private String myPlayerName = "玩家"; // 當前玩家的名稱 (可修改)
    private String opponentPlayerName = "對手"; // 對手玩家的名稱

    // 圖片資源 (使用 placeholder 圖片 URL 作為範例，需要替換為實際的精靈圖)
    // 假設精靈圖是橫向排列的動畫幀
    // TODO: 替換為實際的馬力歐風格圖片 URL 或文件路徑
    private final Image player1SpriteSheet = new Image(
            "https://placehold.co/300x80/ff0000/white?text=P1+Idle+Run+Jump"); // 假設有 3 個狀態，每個狀態 2-3 幀
    private final Image player2SpriteSheet = new Image(
            "https://placehold.co/300x80/0000ff/white?text=P2+Idle+Run+Jump"); // 假設有 3 個狀態，每個狀態 2-3 幀
    private final Image ballSpriteSheet = new Image("https://placehold.co/60x30/ffff00/black?text=Ball+Sprite"); // 假設有
                                                                                                                 // 2
                                                                                                                 // 幀，每幀
                                                                                                                 // 30x30
    private final Image backgroundImage = new Image("https://placehold.co/800x600/87CEEB/white?text=Sky+Background"); // 簡單的天空背景

    // 精靈圖幀大小
    private static final double PLAYER_FRAME_WIDTH = 50;
    private static final double PLAYER_FRAME_HEIGHT = 80;
    private static final double BALL_FRAME_SIZE = 30; // 球是正方形幀

    // 精靈圖動畫速度 (幀/秒)
    private static final double PLAYER_ANIMATION_SPEED = 10;
    private static final double BALL_ANIMATION_SPEED = 15;

    // 音效資源 (使用 placeholder URL 作為範例，需要替換為實際的音效文件路徑)
    // TODO: 替換為實際的音效文件 URL 或文件路徑 (WAV, MP3 等格式)
    private AudioClip jumpSound;
    private AudioClip hitSound;
    private AudioClip scoreSound;

    // 玩家動畫狀態
    private enum PlayerState {
        IDLE(0), RUNNING(1), JUMPING(2);

        private final int value;

        PlayerState(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static PlayerState fromValue(int value) {
            for (PlayerState state : values()) {
                if (state.value == value) {
                    return state;
                }
            }
            return IDLE; // 默認返回 IDLE
        }
    }

    // 網路連線相關
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    // 網路訊息佇列，用於在單獨執行緒中接收訊息，然後在 JavaFX UI 執行緒中處理
    private ConcurrentLinkedQueue<String> messageQueue = new ConcurrentLinkedQueue<>();

    // 客戶端預測相關
    // 儲存本地玩家的輸入歷史 (Client side only)
    private List<InputState> inputHistory = new ArrayList<>();
    // 客戶端發送的輸入序列號 (Client side only)
    private int clientInputSequence = 0;
    // 客戶端：伺服器最後處理的本地輸入序列號 (Client side only)
    private int lastProcessedInputSequenceClient = 0;

    // 房間狀態 (從伺服器同步)
    private String activeRoomName = null;
    // private boolean player1Ready = false; // 玩家 1 準備狀態 - 移除
    // private boolean player2Ready = false; // 玩家 2 準備狀態 - 移除

    // 遊戲開始倒數計時 (從伺服器同步)
    private static final double START_COUNTDOWN_SECONDS = 3.0; // 倒數時間
    private double startCountdownTimer = START_COUNTDOWN_SECONDS;
    private boolean isCountingDown = false;

    // 插值相關
    private static final double INTERPOLATION_BUFFER_TIME = 0.1; // 插值緩衝時間 (秒)
    // private double lastStateReceiveTime = 0; // 上次收到 STATE 訊息的時間 (納秒) - Not
    // directly used for interpolation factor calculation in this simple model

    // 網路設置 UI 元素
    private VBox networkSetupPane;
    private TextField ipAddressField;
    private TextField portField;
    private Label statusLabel;

    // 遊戲大廳 UI 元素
    private VBox lobbyPane;
    private ListView<String> roomListView;
    private TextField roomNameField;
    private Button createRoomButton;
    private Button joinRoomButton;
    private Label lobbyStatusLabel;
    private Button refreshRoomsButton; // 新增刷新房間列表按鈕
    private TextField playerNameField; // 新增玩家名稱輸入框
    private Label playerNameLabel; // 新增玩家名稱標籤

    // 房間內 UI 元素
    private VBox inRoomPane; // 新增房間內面板
    private Label roomInfoLabel; // 顯示房間名稱和玩家信息
    private Label player1StatusLabel; // 玩家 1 狀態
    private Label player2StatusLabel; // 玩家 2 狀態
    private Button leaveRoomButton; // 新增離開房間按鈕
    // private Button readyButton; // 新增準備按鈕 - 移除
    private Label countdownLabel; // 新增倒數計時標籤

    // 聊天 UI 元素
    private TextArea chatDisplayArea; // 聊天顯示區域
    private TextField chatInput; // 聊天輸入框
    private Button sendChatButton; // 發送聊天按鈕

    // 用於格式化浮點數，減少網路傳輸數據量
    // private DecimalFormat decimalFormat = new DecimalFormat("#.##"); // 未使用

    // 定義輸入狀態類別，用於記錄玩家輸入和對應的時間/序列號
    private static class InputState {
        int sequenceNumber; // 輸入序列號
        Set<KeyCode> activeKeys = new HashSet<>(); // 按下的鍵
        // 可以添加其他輸入相關信息，例如擊球狀態等

        public InputState(int sequenceNumber, Set<KeyCode> activeKeys) {
            this.sequenceNumber = sequenceNumber;
            this.activeKeys.addAll(activeKeys);
        }
    }

    // 玩家類別 (Client side representation)
    private class Player {
        double x, y;
        double width, height;
        double velocityX, velocityY; // 玩家速度
        boolean isJumping; // 是否正在跳躍
        ImageView imageView; // 用於顯示玩家圖片
        int playerNumber; // 玩家編號 (1 或 2)
        String playerName; // 玩家名稱

        // 動畫相關
        Image spriteSheet;
        int frameCount; // 精靈圖總幀數 (這裡不再是總幀數，而是精靈圖的寬度 / 幀寬度)
        double frameWidth; // 每幀寬度
        double frameHeight; // 每幀高度
        int currentFrameIndex = 0; // 當前顯示的幀索引
        double frameTime = 0; // 累計時間，用於控制動畫速度
        PlayerState currentState = PlayerState.IDLE; // 當前動畫狀態

        // 定義每個狀態對應的幀範圍 (起始幀索引, 幀數)
        // TODO: 根據您的精靈圖實際情況調整這些值
        private final int[] IDLE_FRAMES = { 0, 2 }; // 例如：從索引 0 開始，共 2 幀
        private final int[] RUNNING_FRAMES = { 2, 4 }; // 例如：從索引 2 開始，共 4 幀
        private final int[] JUMPING_FRAMES = { 6, 1 }; // 例如：從索引 6 開始，共 1 幀 (跳躍通常只有一幀)

        // 擊球冷卻 (本地預測用)
        double hitCooldownTimer = 0;

        // 插值相關
        double previousX, previousY; // 上一個權威狀態的位置
        double interpolationTimer = 0; // 插值計時器

        public Player(double x, double y, double width, double height, Image spriteSheet, double frameWidth,
                double frameHeight, int playerNumber, String playerName) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.spriteSheet = spriteSheet;
            this.frameWidth = frameWidth;
            this.frameHeight = frameHeight;
            // 假設精靈圖是橫向排列的，總幀數可以根據圖片寬度和幀寬度計算
            this.frameCount = (int) (spriteSheet.getWidth() / frameWidth);
            this.playerNumber = playerNumber;
            this.playerName = playerName;
            this.velocityX = 0;
            this.velocityY = 0;
            this.isJumping = false;

            // Initialize ImageView
            this.imageView = new ImageView(spriteSheet);
            this.imageView.setFitWidth(width);
            this.imageView.setFitHeight(height);

            // Set initial viewport to the first frame
            this.imageView.setViewport(new Rectangle2D(0, 0, frameWidth, frameHeight));

            // Set ImageView position (ImageView position is top-left)
            this.imageView.setLayoutX(x);
            this.imageView.setLayoutY(y);

            // Initialize previous position for interpolation
            this.previousX = x;
            this.previousY = y;
        }

        // Update player state (movement, jump physics, animation, cooldown timer)
        // isLocal parameter is for client-side prediction, local player applies input,
        // remote player updates based on server state
        public void update(double deltaTime, boolean isLocal) {
            if (isLocal) {
                // Local player: Physics simulated by client-side prediction/reconciliation
                // Position and velocity are updated by simulatePhysics and reconciliation
                // Only update animation and cooldown timer here
                updateAnimation(deltaTime);
                // Update hit cooldown timer (本地預測用，伺服器權威)
                if (hitCooldownTimer > 0) {
                    hitCooldownTimer -= deltaTime;
                }
                // Update ImageView position based on predicted/reconciled position
                imageView.setLayoutX(x);
                imageView.setLayoutY(y);

            } else {
                // Remote player: Position and velocity are set by server's STATE message
                // Use interpolation to smooth movement
                interpolationTimer += deltaTime;
                double interpolationFactor = interpolationTimer / INTERPOLATION_BUFFER_TIME; // Interpolate over a fixed
                                                                                             // buffer time
                interpolationFactor = Math.min(interpolationFactor, 1.0); // Clamp factor to 1.0

                // Calculate interpolated position
                double interpolatedX = previousX + (x - previousX) * interpolationFactor;
                double interpolatedY = previousY + (y - previousY) * interpolationFactor;

                // Update ImageView position to the interpolated position
                imageView.setLayoutX(interpolatedX);
                imageView.setLayoutY(interpolatedY);

                // Update animation based on the interpolated velocity (or just the current
                // velocity from server)
                // For simplicity, let's use the current velocity from server for animation
                // state
                updateAnimation(deltaTime);

            }

            // Flip image based on player direction (use current velocity)
            if (velocityX < 0) {
                imageView.setScaleX(-1); // Flip horizontally
            } else if (velocityX > 0) {
                imageView.setScaleX(1); // Restore normal
            }
            // If stationary, keep the direction of the last movement or default direction
        }

        // Update animation state and frame
        private void updateAnimation(double deltaTime) {
            // Animation state is determined by velocity and jumping state
            PlayerState previousState = currentState;

            if (isJumping) {
                currentState = PlayerState.JUMPING;
            } else if (Math.abs(velocityX) > 10) { // Horizontal velocity means running
                currentState = PlayerState.RUNNING;
            } else {
                currentState = PlayerState.IDLE;
            }

            // If state changed, reset animation timer and frame index
            if (currentState != previousState) {
                frameTime = 0;
                currentFrameIndex = getFrameRange(currentState)[0]; // Switch to the first frame of the new state
            }

            // --- Update Animation Frame ---
            frameTime += deltaTime;
            double frameDuration = 1.0 / PLAYER_ANIMATION_SPEED; // Duration per frame

            if (frameTime >= frameDuration) {
                // Switch to the next frame
                int[] frameRange = getFrameRange(currentState);
                int startFrame = frameRange[0];
                int numFrames = frameRange[1];

                currentFrameIndex = startFrame + (currentFrameIndex - startFrame + 1) % numFrames;

                // Reset timer
                frameTime -= frameDuration; // Subtract one frame's time, keep remainder for precision
            }

            // Update ImageView viewport to display the current frame
            imageView.setViewport(new Rectangle2D(currentFrameIndex * frameWidth, 0, frameWidth, frameHeight));
        }

        // Get frame range based on state
        private int[] getFrameRange(PlayerState state) {
            switch (state) {
                case IDLE:
                    return IDLE_FRAMES;
                case RUNNING:
                    return RUNNING_FRAMES;
                case JUMPING:
                    return JUMPING_FRAMES;
                default:
                    return IDLE_FRAMES; // Default state
            }
        }

        // Draw player (now handled by ImageView)
        // public void draw(GraphicsContext gc) { // 未使用
        // // No need to draw here, ImageView handles display
        // }

        // Start jump (Client side prediction)
        public void jump() {
            if (!isJumping) { // Can only jump when on the ground
                velocityY = PLAYER_JUMP_STRENGTH; // Give upward initial velocity
                isJumping = true; // Set to jumping state
                // Play jump sound (triggered locally for prediction)
                if (jumpSound != null) {
                    soundPlay(jumpSound); // Use helper method to play sound on UI thread
                }
            }
        }

        // Attempt to hit the ball (Client side prediction)
        public boolean tryHit() {
            if (hitCooldownTimer <= 0) {
                hitCooldownTimer = PLAYER_HIT_COOLDOWN; // Reset cooldown timer
                return true; // Can hit
            }
            return false; // Still on cooldown
        }

        // Get player's bounds rectangle (for collision detection - client side
        // prediction might use this)
        public Rectangle2D getBounds() {
            return new Rectangle2D(x, y, width, height);
        }

        // Set player state (for network synchronization)
        public void setState(double x, double y, double velocityX, double velocityY, PlayerState state) {
            // Store current state as previous before updating
            this.previousX = this.x;
            this.previousY = this.y;

            this.x = x;
            this.y = y;
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            this.currentState = state;
            this.isJumping = (state == PlayerState.JUMPING); // Set jumping flag based on state

            // Reset interpolation timer when a new state is received
            this.interpolationTimer = 0;
        }

        // Simulate player physics (for client-side prediction and reconciliation
        // re-simulation)
        public void simulatePhysics(Set<KeyCode> inputKeys, double deltaTime) {
            // Apply vertical velocity (affected by gravity)
            velocityY += GRAVITY * deltaTime; // Use constant GRAVITY
            y += velocityY * deltaTime;

            // Apply horizontal velocity based on input keys
            velocityX = 0;
            if (inputKeys.contains(KeyCode.A)) {
                velocityX = -PLAYER_MOVE_SPEED;
            }
            if (inputKeys.contains(KeyCode.D)) {
                velocityX = PLAYER_MOVE_SPEED;
            }
            x += velocityX * deltaTime;

            // --- Client-side Collision Detection and Resolution for Prediction ---

            // Ground collision
            if (y + height > HEIGHT) { // Use constant HEIGHT
                y = HEIGHT - height;
                velocityY = 0;
                isJumping = false;
            }

            // Left wall collision
            if (x < 0) {
                x = 0;
                velocityX = 0; // Stop horizontal movement on collision
            }

            // Right wall collision
            if (x + width > WIDTH) { // Use constant WIDTH
                x = WIDTH - width;
                velocityX = 0; // Stop horizontal movement on collision
            }

            // Net collision (simplified: just stop horizontal movement if colliding with
            // net)
            // More complex net collision would involve bouncing off the net
            Rectangle2D playerBounds = getBounds();
            if (playerBounds.intersects(netBounds)) { // Use the netBounds defined in the main class
                // Determine which side of the net the player is on
                if (x + width / 2 < WIDTH / 2) { // Player is on the left side of the net
                    if (playerBounds.getMaxX() > netBounds.getMinX()) {
                        x = netBounds.getMinX() - width; // Push player back to the left of the net
                        velocityX = 0; // Stop horizontal movement
                    }
                } else { // Player is on the right side of the net
                    if (playerBounds.getMinX() < netBounds.getMaxX()) {
                        x = netBounds.getMaxX(); // Push player back to the right of the net
                        velocityX = 0; // Stop horizontal movement
                    }
                }
            }

            // Note: This method does NOT handle animation frame updates or cooldown timers.
            // These are handled in the main game loop's update method.
            // It also does NOT handle collisions with the ball, as those are
            // server-authoritative.
            // Player-ball hit prediction is handled separately in checkAndSendInput.
        }
    }

    // Ball class (Client side representation)
    private class Ball {
        double x, y;
        double radius;
        double velocityX, velocityY; // Speed
        ImageView imageView; // For displaying ball image
        // boolean lastTouchedByPlayer1 = false; // Record which player last touched the
        // ball (player 1 is server side) - 未使用

        // Animation related
        Image spriteSheet;
        int frameCount; // Total frames in the sprite sheet
        double frameSize; // Size of each frame (ball is square)
        int currentFrameIndex = 0; // Index of the current frame being displayed
        double frameTime = 0; // Accumulated time for controlling animation speed

        // 插值相關
        double previousX, previousY; // 上一個權威狀態的位置
        double interpolationTimer = 0; // 插值計時器

        // 預測相關 (客戶端僅用於即時視覺反饋，不完全模擬物理)
        private boolean isPredicted = false; // 標記球的狀態是否基於客戶端預測
        private int predictedByInputSequence = -1; // 觸發預測的輸入序列號

        public Ball(double x, double y, double radius, Image spriteSheet, double frameSize, int frameCount) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.spriteSheet = spriteSheet;
            this.frameSize = frameSize;
            this.frameCount = frameCount;
            this.velocityX = 150; // Initial horizontal velocity (example)
            this.velocityY = -200; // Initial vertical velocity (example, upwards)

            // Initialize ImageView
            this.imageView = new ImageView(spriteSheet);
            this.imageView.setFitWidth(radius * 2);
            this.imageView.setFitHeight(radius * 2);

            // Set initial viewport to the first frame
            this.imageView.setViewport(new Rectangle2D(0, 0, frameSize, frameSize));

            // Set ImageView position (ImageView position is top-left)
            // Ball's logic position x, y is the center, ImageView position needs offset
            this.imageView.setLayoutX(x - radius);
            this.imageView.setLayoutY(y - radius);

            // Initialize previous position for interpolation
            this.previousX = x;
            this.previousY = y;
        }

        // Update ball state (animation and interpolation/prediction on client)
        public void update(double deltaTime) {
            // Ball physics is server authoritative, client only updates animation and
            // interpolates/predicts position

            if (isPredicted) {
                // If predicted, simulate physics locally for immediate feedback
                simulatePhysics(deltaTime);
                // Update ImageView position based on predicted position
                imageView.setLayoutX(x - radius);
                imageView.setLayoutY(y - radius);

            } else {
                // If not predicted, use interpolation based on server state
                interpolationTimer += deltaTime;
                double interpolationFactor = interpolationTimer / INTERPOLATION_BUFFER_TIME; // Interpolate over a fixed
                                                                                             // buffer time
                interpolationFactor = Math.min(interpolationFactor, 1.0); // Clamp factor to 1.0

                // Calculate interpolated position
                double interpolatedX = previousX + (x - previousX) * interpolationFactor;
                double interpolatedY = previousY + (y - previousY) * interpolationFactor;

                // Update ImageView position to the interpolated position
                imageView.setLayoutX(interpolatedX - radius); // ImageView position is top-left
                imageView.setLayoutY(interpolatedY - radius); // ImageView position is top-left
            }

            // --- Update Animation Frame ---
            frameTime += deltaTime;
            double frameDuration = 1.0 / BALL_ANIMATION_SPEED; // Duration per frame

            if (frameTime >= frameDuration) {
                // Switch to the next frame
                currentFrameIndex = (currentFrameIndex + 1) % frameCount;
                // Reset timer
                frameTime -= frameDuration; // Subtract one frame's time, keep remainder for precision
            }

            // Update ImageView viewport to display the current frame
            imageView.setViewport(new Rectangle2D(currentFrameIndex * frameSize, 0, frameSize, frameSize));

            // Determine direction based on ball's horizontal velocity (if ball image needs
            // flipping)
            if (velocityX < 0) {
                imageView.setScaleX(-1); // Flip horizontally
            } else if (velocityX > 0) {
                imageView.setScaleX(1); // Restore normal
            }
        }

        // Simulate ball physics (client side prediction only)
        public void simulatePhysics(double deltaTime) {
            // Apply gravity
            velocityY += GRAVITY * deltaTime; // Use constant GRAVITY

            // Update position
            x += velocityX * deltaTime;
            y += velocityY * deltaTime;

            // Simple ground collision detection
            if (y + radius > HEIGHT) { // Use constant HEIGHT
                y = HEIGHT - radius;
                velocityY *= -BOUNCE_FACTOR; // Use constant BOUNCE_FACTOR
                velocityX *= GROUND_FRICTION; // Use constant GROUND_FRICTION
                if (Math.abs(velocityY) < 10)
                    velocityY = 0;
                if (Math.abs(velocityX) < 10)
                    velocityX = 0;
            }

            // Simple left/right wall collision detection
            if (x - radius < 0) {
                x = radius;
                velocityX *= -BOUNCE_FACTOR; // Use constant BOUNCE_FACTOR
                if (Math.abs(velocityX) < 10)
                    velocityX = 0;
            } else if (x + radius > WIDTH) { // Use constant WIDTH
                x = WIDTH - radius;
                velocityX *= -BOUNCE_FACTOR; // Use constant BOUNCE_FACTOR
                if (Math.abs(velocityX) < 10)
                    velocityX = 0;
            }

            // Net collision (simplified for client prediction)
            if (getBounds().intersects(netBounds)) { // Use the netBounds defined in the main class
                // Determine which side the ball hit the net from
                boolean hitFromLeft = getBounds().getMaxX() < netBounds.getMinX();
                boolean hitFromRight = getBounds().getMinX() > netBounds.getMaxX();
                boolean hitFromTop = getBounds().getMaxY() < netBounds.getMinY();
                boolean hitFromBottom = getBounds().getMinY() > netBounds.getMinY(); // Corrected logic: check against
                                                                                     // netBounds.getMinY() for top
                                                                                     // collision

                // Find the closest point to the circle center on the rectangle
                double closestX = Math.max(netBounds.getMinX(), Math.min(x, netBounds.getMaxX()));
                double closestY = Math.max(netBounds.getMinY(), Math.min(y, netBounds.getMaxY()));

                // Calculate the distance between the circle center and the closest point
                double distanceX = x - closestX;
                double distanceY = y - closestY;
                double distance = Math.sqrt((distanceX * distanceX) + (distanceY * distanceY));

                // Calculate overlap depth
                double overlap = radius - distance;

                if (overlap > 0) {
                    // Overlap exists, a collision occurred

                    // Calculate collision normal
                    double normalX = distanceX / distance;
                    double normalY = distanceY / distance;

                    // Push the ball out along the normal to resolve overlap
                    x += normalX * overlap;
                    y += normalY * overlap;

                    // Calculate velocity component along the normal
                    double dotProduct = velocityX * normalX + velocityY * normalY;

                    // Calculate velocity component along the tangent
                    double tangentX = velocityX - dotProduct * normalX;
                    double tangentY = velocityY - dotProduct * normalY;

                    // Bounce velocity along the normal, considering bounce factor
                    double newDotProduct = -dotProduct * BALL_NET_BOUNCE_FACTOR; // Use constant BOUNCE_FACTOR

                    // Update velocity
                    velocityX = newDotProduct * normalX + tangentX;
                    velocityY = newDotProduct * normalY + tangentY;

                    // Consider net's special property: significant horizontal velocity decay
                    if (hitFromLeft || hitFromRight) {
                        velocityX *= BALL_NET_HORIZONTAL_DECAY; // More horizontal velocity decay
                    } else if (hitFromTop || hitFromBottom) {
                        velocityY *= BOUNCE_FACTOR; // Vertical velocity decay
                    }

                    // If velocity is very small, might be stuck on the net, give a slight push
                    if (Math.abs(velocityX) < 20 && Math.abs(velocityY) < 20) {
                        if (hitFromLeft)
                            velocityX = -50;
                        else
                            velocityX = 50;
                        velocityY = -50; // Push upwards
                    }
                }
            }
        }

        // Get ball's bounds rectangle (for collision detection)
        public Rectangle2D getBounds() {
            return new Rectangle2D(x - radius, y - radius, radius * 2, radius * 2);
        }

        // Set ball state (for network synchronization)
        public void setState(double x, double y, double velocityX, double velocityY) {
            // Store current state as previous before updating
            this.previousX = this.x;
            this.previousY = this.y;

            this.x = x;
            this.y = y;
            this.velocityX = velocityX;
            this.velocityY = velocityY;

            // Reset interpolation timer when a new state is received
            this.interpolationTimer = 0;

            // When server state is received, stop predicting
            this.isPredicted = false;
            this.predictedByInputSequence = -1;
        }

        // Apply predicted hit effect (Client side prediction only)
        public void applyPredictedHit(Player hittingPlayer, int sequenceNumber) {
            // Calculate vector from player center to ball center
            double playerCenterX = hittingPlayer.x + hittingPlayer.width / 2;
            double playerCenterY = hittingPlayer.y + hittingPlayer.height / 2;
            double ballCenterX = this.x;
            double ballCenterY = this.y;

            double deltaX = ballCenterX - playerCenterX;
            double deltaY = ballCenterY - playerCenterY;

            // Calculate predicted ball velocity after hit
            double newVelocityX = deltaX * 0.5 + hittingPlayer.velocityX * BALL_PLAYER_BOUNCE_FACTOR
                    + (Math.random() - 0.5) * 50;
            double newVelocityY = deltaY * 0.5 + hittingPlayer.velocityY * BALL_PLAYER_BOUNCE_FACTOR - HIT_STRENGTH
                    + (Math.random() - 0.5) * 50; // Use constant HIT_STRENGTH

            double maxBallSpeed = 750;
            double currentBallSpeed = Math.sqrt(newVelocityX * newVelocityX + newVelocityY * newVelocityY);
            if (currentBallSpeed > maxBallSpeed) {
                double scaleFactor = maxBallSpeed / currentBallSpeed;
                newVelocityX *= scaleFactor;
                newVelocityY *= scaleFactor;
            }

            this.velocityX = newVelocityX;
            this.velocityY = newVelocityY;

            // Set ball to predicted state
            this.isPredicted = true;
            this.predictedByInputSequence = sequenceNumber;

            // Play hit sound locally for immediate feedback
            if (hitSound != null) {
                soundPlay(hitSound);
            }
        }
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("馬力歐排球客戶端");

        // Create Canvas and GraphicsContext (for background and net)
        gameCanvas = new Canvas(WIDTH, HEIGHT);
        gc = gameCanvas.getGraphicsContext2D();

        // Load sounds (use try-catch to prevent crashes if files are missing)
        try {
            // TODO: Replace with actual sound file paths or URLs
            URL jumpSoundUrl = getClass().getResource("/sounds/jump.wav"); // Assuming sound file is in
                                                                           // src/main/resources/sounds/jump.wav
            if (jumpSoundUrl != null)
                jumpSound = new AudioClip(jumpSoundUrl.toExternalForm());

            URL hitSoundUrl = getClass().getResource("/sounds/hit.wav"); // Assuming sound file is in
                                                                         // src/main/resources/sounds/hit.wav
            if (hitSoundUrl != null)
                hitSound = new AudioClip(hitSoundUrl.toExternalForm());

            URL scoreSoundUrl = getClass().getResource("/sounds/score.wav"); // Assuming sound file is in
                                                                             // src/main/resources/sounds/score.wav
            if (scoreSoundUrl != null)
                scoreSound = new AudioClip(scoreSoundUrl.toExternalForm());

        } catch (Exception e) {
            System.err.println("Failed to load sound files: " + e.getMessage());
            // Game can still run even if sounds fail to load
        }

        // Create background ImageView
        ImageView backgroundImageView = new ImageView(backgroundImage);
        backgroundImageView.setFitWidth(WIDTH);
        backgroundImageView.setFitHeight(HEIGHT);

        // Initialize game elements (using images and frame counts)
        // Create two players and a ball here, their behavior and control will be
        // determined by network mode
        player1 = new Player(50, HEIGHT - 80, PLAYER_FRAME_WIDTH, PLAYER_FRAME_HEIGHT, player1SpriteSheet,
                PLAYER_FRAME_WIDTH, PLAYER_FRAME_HEIGHT, 1, "玩家 1"); // Player 1
        player2 = new Player(WIDTH - 100, HEIGHT - 80, PLAYER_FRAME_WIDTH, PLAYER_FRAME_HEIGHT, player2SpriteSheet,
                PLAYER_FRAME_WIDTH, PLAYER_FRAME_HEIGHT, 2, "玩家 2"); // Player 2
        ball = new Ball(WIDTH / 4, HEIGHT / 2, BALL_FRAME_SIZE / 2, ballSpriteSheet, BALL_FRAME_SIZE, 2); // Ball

        // --- Create Network Setup UI ---
        networkSetupPane = new VBox(10); // Vertical layout with 10px spacing
        networkSetupPane.setAlignment(Pos.CENTER); // Center alignment

        Label modeLabel = new Label("連線到伺服器:");
        Button clientButton = new Button("連線");

        Label ipLabel = new Label("伺服器 IP:");
        ipAddressField = new TextField("localhost"); // Default IP
        ipAddressField.setMaxWidth(200);

        Label portLabel = new Label("端口號:");
        portField = new TextField("12345"); // Default port
        portField.setMaxWidth(100);

        statusLabel = new Label("狀態: 未連線");

        // Client button event
        clientButton.setOnAction(e -> {
            String ip = ipAddressField.getText();
            int port = Integer.parseInt(portField.getText());
            statusLabel.setText("狀態: 嘗試連線到 " + ip + ":" + port + "...");
            // Start client connection in a new thread to avoid blocking JavaFX UI thread
            new Thread(this::startClient).start(); // No need to pass ip/port, use fields
            // Hide setup pane, show connection message
            networkSetupPane.setVisible(false);
            showWaitingMessage("嘗試連線到伺服器...");
        });

        networkSetupPane.getChildren().addAll(modeLabel, clientButton, ipLabel, ipAddressField, portLabel, portField,
                statusLabel);

        // --- Create Lobby UI ---
        lobbyPane = new VBox(10);
        lobbyPane.setAlignment(Pos.CENTER);
        lobbyPane.setVisible(false); // Initially hidden

        Label lobbyTitleLabel = new Label("遊戲大廳");
        lobbyTitleLabel.setFont(new Font("Arial", 30));

        playerNameLabel = new Label("你的名稱:");
        playerNameField = new TextField("玩家"); // Default player name
        playerNameField.setMaxWidth(200);

        roomListView = new ListView<>();
        roomListView.setPrefSize(300, 200); // Set preferred size
        roomListView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE); // Allow only single selection

        Label roomNameLabel = new Label("房間名稱:");
        roomNameField = new TextField();
        roomNameField.setMaxWidth(200);

        createRoomButton = new Button("建立房間");
        joinRoomButton = new Button("加入房間");
        refreshRoomsButton = new Button("刷新房間列表"); // 新增刷新房間列表按鈕
        lobbyStatusLabel = new Label("狀態: 在大廳");

        // Create Room button event
        createRoomButton.setOnAction(e -> {
            String roomName = roomNameField.getText().trim();
            myPlayerName = playerNameField.getText().trim(); // Get player name
            if (myPlayerName.isEmpty())
                myPlayerName = "玩家"; // Default if empty

            if (!roomName.isEmpty() && isConnected) {
                sendMessage("CREATE_ROOM:" + roomName + ":" + myPlayerName); // Send player name with create request
                System.out.println("客戶端發送: CREATE_ROOM:" + roomName + ":" + myPlayerName); // Debugging
                lobbyStatusLabel.setText("狀態: 建立房間 " + roomName + "...");
            } else {
                lobbyStatusLabel.setText("錯誤: 房間名稱不能為空或未連線");
            }
        });

        // Join Room button event
        joinRoomButton.setOnAction(e -> {
            String selectedRoomItem = roomListView.getSelectionModel().getSelectedItem();
            myPlayerName = playerNameField.getText().trim(); // Get player name
            if (myPlayerName.isEmpty())
                myPlayerName = "玩家"; // Default if empty

            if (selectedRoomItem != null && isConnected) {
                // Extract room name from the list item (which now includes player count and
                // names)
                String roomNameToJoin = selectedRoomItem.split("\\(")[0].trim(); // Split by '(' and take the first part
                sendMessage("JOIN_ROOM:" + roomNameToJoin + ":" + myPlayerName); // Send player name with join request
                System.out.println("客戶端發送: JOIN_ROOM:" + roomNameToJoin + ":" + myPlayerName); // Debugging
                lobbyStatusLabel.setText("狀態: 加入房間 " + roomNameToJoin + "...");
            } else {
                lobbyStatusLabel.setText("錯誤: 請選擇一個房間或未連線");
            }
        });

        // Refresh Rooms button event
        refreshRoomsButton.setOnAction(e -> {
            if (isConnected) {
                sendMessage("LIST_ROOMS");
                System.out.println("客戶端發送: LIST_ROOMS"); // Debugging
                lobbyStatusLabel.setText("狀態: 刷新房間列表...");
            }
        });

        lobbyPane.getChildren().addAll(lobbyTitleLabel, playerNameLabel, playerNameField, roomListView, roomNameLabel,
                roomNameField, createRoomButton, joinRoomButton, refreshRoomsButton, lobbyStatusLabel);

        // --- Create In Room UI ---
        inRoomPane = new VBox(10);
        inRoomPane.setAlignment(Pos.CENTER);
        inRoomPane.setVisible(false); // Initially hidden

        roomInfoLabel = new Label("房間信息"); // Placeholder
        roomInfoLabel.setFont(new Font("Arial", 20));

        player1StatusLabel = new Label("玩家 1: "); // 玩家 1 狀態
        player2StatusLabel = new Label("玩家 2: "); // 玩家 2 狀態

        leaveRoomButton = new Button("離開房間");
        leaveRoomButton.setOnAction(e -> {
            if (isConnected) {
                sendMessage("LEAVE_ROOM");
                System.out.println("客戶端發送: LEAVE_ROOM"); // Debugging
                // Client transitions back to lobby immediately (server will confirm)
                transitionToLobby();
            }
        });

        countdownLabel = new Label(""); // Initialize countdown label
        countdownLabel.setFont(new Font("Arial", 40));
        countdownLabel.setTextFill(Color.RED);

        // Chat UI
        chatDisplayArea = new TextArea();
        chatDisplayArea.setEditable(false); // Make it read-only
        chatDisplayArea.setPrefHeight(150); // Set preferred height
        chatDisplayArea.setWrapText(true); // Wrap long lines

        ScrollPane chatScrollPane = new ScrollPane(chatDisplayArea);
        chatScrollPane.setFitToWidth(true); // Make scroll pane fit width of content
        chatScrollPane.setPrefHeight(150); // Set preferred height for scroll pane

        chatInput = new TextField();
        chatInput.setPromptText("輸入訊息..."); // Placeholder text
        chatInput.setOnAction(e -> sendChatMessage()); // Send message on Enter key

        sendChatButton = new Button("發送");
        sendChatButton.setOnAction(e -> sendChatMessage());

        HBox chatInputArea = new HBox(5, chatInput, sendChatButton); // Horizontal layout for input and button
        chatInputArea.setAlignment(Pos.CENTER_LEFT); // Align to left

        // Layout for player status and buttons
        VBox playerStatuses = new VBox(5, player1StatusLabel, player2StatusLabel);
        playerStatuses.setAlignment(Pos.CENTER_LEFT);

        VBox chatPane = new VBox(5, new Label("聊天:"), chatScrollPane, chatInputArea);
        chatPane.setAlignment(Pos.CENTER);

        inRoomPane.getChildren().addAll(roomInfoLabel, playerStatuses, countdownLabel,
                new HBox(10, leaveRoomButton), chatPane); // Add chat pane

        // Add Canvas and all ImageViews to the layout container
        StackPane root = new StackPane();
        // Order of ImageViews matters, later added are on top
        root.getChildren().addAll(backgroundImageView, gameCanvas, player1.imageView, player2.imageView, ball.imageView,
                networkSetupPane, lobbyPane, inRoomPane); // Add inRoom pane

        Scene scene = new Scene(root, WIDTH, HEIGHT);

        // --- Handle Keyboard Input ---
        scene.setOnKeyPressed(event -> handleKeyPress(event.getCode()));
        scene.setOnKeyReleased(event -> handleKeyRelease(event.getCode()));

        // Set up the game loop
        gameLoop = new AnimationTimer() {
            private long lastUpdateTime = 0; // For calculating deltaTime

            @Override
            public void handle(long now) {
                // Process network messages queue
                processNetworkMessages();

                // Calculate deltaTime in seconds
                if (lastUpdateTime == 0) {
                    lastUpdateTime = now;
                    return;
                }
                double deltaTime = (now - lastUpdateTime) / 1_000_000_000.0; // Nanoseconds to seconds
                lastUpdateTime = now;

                // --- Update Game State ---

                if (currentState == GameState.GAME) {
                    // Client: Process local player input, send to server, receive server state and
                    // update local display

                    // --- Send Local Player Input and Predict ---
                    // Detect key events and send messages
                    checkAndSendInputAndPredict(); // Uses input sequence number and predicts local player/ball hit

                    // --- Client-side Prediction & Interpolation ---
                    // Only apply prediction if player number is assigned
                    if (myPlayerNumber != 0) {
                        Player localPlayer = (myPlayerNumber == 1) ? player1 : player2;
                        localPlayer.velocityX = 0; // Reset horizontal velocity each frame
                        if (activeKeys.contains(KeyCode.A))
                            localPlayer.velocityX = -PLAYER_MOVE_SPEED;
                        if (activeKeys.contains(KeyCode.D))
                            localPlayer.velocityX = PLAYER_MOVE_SPEED;

                        // Simulate local player physics for this frame based on current active keys
                        // (includes environmental collisions)
                        localPlayer.simulatePhysics(activeKeys, deltaTime); // Apply prediction physics

                        // Update local player animation and cooldown
                        localPlayer.update(deltaTime, true);
                    }

                    // Update remote player and ball animation and interpolate/predict their
                    // positions
                    // Determine which player is remote
                    if (myPlayerNumber == 1) { // I am player 1, player 2 is remote
                        player2.update(deltaTime, false); // Update remote player animation and interpolate
                    } else if (myPlayerNumber == 2) { // I am player 2, player 1 is remote
                        player1.update(deltaTime, false); // Update remote player animation and interpolate
                    } else { // Before player number is assigned, update both as remote (no prediction)
                        player1.update(deltaTime, false);
                        player2.update(deltaTime, false);
                    }

                    // Ball updates animation and interpolates/predicts based on its internal state
                    ball.update(deltaTime);

                    // Ensure ImageView positions are updated (client needs to manually update as
                    // position is set by STATE message and prediction/interpolation)
                    // ImageView positions are now updated inside the update methods of Player and
                    // Ball
                    // based on whether they are local (prediction/reconciliation) or remote
                    // (interpolation/prediction).

                } else if (currentState == GameState.IN_ROOM && isCountingDown) {
                    // Update countdown timer (client side display based on server START_COUNTDOWN
                    // message)
                    startCountdownTimer -= deltaTime;
                    if (startCountdownTimer <= 0) {
                        startCountdownTimer = 0;
                        isCountingDown = false;
                        // Client waits for START_ROOM_GAME message from server to actually start
                    }
                    // Update countdown label text
                    Platform.runLater(() -> countdownLabel.setText(String.format("%.1f", startCountdownTimer)));
                }

                // --- Draw Game Screen (Canvas content) ---
                draw(gc); // Only draw Canvas content (net, UI) and UI based on state

                // Update previous key state
                previousKeys.clear();
                previousKeys.addAll(activeKeys);
            }
        };

        // Start the game loop
        gameLoop.start();

        // Set up and show the primary stage
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // Establish client connection
    private void startClient() {
        String ip = ipAddressField.getText();
        int port = Integer.parseInt(portField.getText());
        try {
            clientSocket = new Socket(ip, port);
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            isConnected = true;
            Platform.runLater(() -> setConnectionStatus(true)); // Update status on UI thread

            // Start a new thread to continuously receive messages from the server
            new Thread(this::receiveMessages).start();

        } catch (IOException e) {
            System.err.println("連線到伺服器失敗: " + e.getMessage());
            Platform.runLater(() -> setConnectionStatus(false)); // Update status on UI thread
        }
    }

    // Receive messages from the server (runs in a separate thread)
    private void receiveMessages() {
        try {
            String msg;
            while ((msg = in.readLine()) != null) {
                // Add received message to the queue for processing in the JavaFX UI thread
                messageQueue.offer(msg);
            }
        } catch (IOException e) {
            System.out
                    .println("與伺服器連線中斷: " + clientSocket.getInetAddress().getHostAddress() + " - " + e.getMessage());
            Platform.runLater(() -> setConnectionStatus(false)); // Update connection status
        } finally {
            closeConnection(); // Ensure connection is closed
        }
    }

    // Send message to the server
    private void sendMessage(String message) {
        if (out != null && isConnected) {
            out.println(message);
        } else {
            System.err.println("無法發送訊息，未連線: " + message);
        }
    }

    // Close client connection
    private void closeConnection() {
        try {
            if (in != null)
                in.close();
            if (out != null)
                out.close();
            if (clientSocket != null && !clientSocket.isClosed())
                clientSocket.close();
            isConnected = false;
        } catch (IOException e) {
            System.err.println("關閉客戶端連線錯誤: " + e.getMessage());
        }
    }

    // Check key events, send input messages, and apply local prediction (Client
    // side)
    private void checkAndSendInputAndPredict() {
        if (!isConnected || currentState != GameState.GAME || myPlayerNumber == 0)
            return; // Only send input if connected, in GAME state, and player number is assigned

        // Determine local player
        Player localPlayer = (myPlayerNumber == 1) ? player1 : player2;

        // Increment sequence number for each input bundle sent
        clientInputSequence++;

        // Record current input state (including sequence number)
        InputState currentInput = new InputState(clientInputSequence, activeKeys);
        inputHistory.add(currentInput); // Add to input history

        // Send input message (including sequence number)
        // Format: INPUT:<playerNumber>:<action>:<sequenceNumber>
        // We send the full active key state with each input sequence for simplicity
        StringBuilder inputMsg = new StringBuilder("INPUT:");
        inputMsg.append(myPlayerNumber).append(":"); // Use myPlayerNumber to indicate which player's input this is
        // Append active keys as a comma-separated string
        boolean firstKey = true;
        for (KeyCode code : activeKeys) {
            if (!firstKey)
                inputMsg.append(",");
            inputMsg.append(code.toString());
            firstKey = false;
        }
        inputMsg.append(":").append(clientInputSequence);

        sendMessage(inputMsg.toString());
        // System.out.println("客戶端發送: " + inputMsg.toString()); // Debugging input
        // messages

        // We also need to send discrete events like jump and hit on key press
        // These are sent as separate messages with the same sequence number as the full
        // key state

        // Predict Jump
        if (activeKeys.contains(KeyCode.SPACE) && !previousKeys.contains(KeyCode.SPACE)) {
            // Check if player can jump before sending the jump input
            if (!localPlayer.isJumping) { // Client checks local player's jump state for prediction
                sendMessage("INPUT:" + myPlayerNumber + ":JUMP_PRESSED:" + clientInputSequence);
                System.out.println("客戶端發送: INPUT:" + myPlayerNumber + ":JUMP_PRESSED:" + clientInputSequence); // Debugging
                // Apply jump locally for prediction
                localPlayer.jump(); // Apply jump locally immediately
            }
        }

        // Predict Hit
        if (activeKeys.contains(KeyCode.K) && !previousKeys.contains(KeyCode.K)) {
            // Check if player can hit before sending the hit input
            if (localPlayer.tryHit()) { // Client checks local player's hit cooldown for prediction
                // Check for collision with the ball for hit prediction
                if (localPlayer.getBounds().intersects(ball.getBounds())) {
                    // Apply predicted hit effect to the ball
                    ball.applyPredictedHit(localPlayer, clientInputSequence);
                    sendMessage("INPUT:" + myPlayerNumber + ":HIT_PRESSED:" + clientInputSequence);
                    System.out.println("客戶端發送: INPUT:" + myPlayerNumber + ":HIT_PRESSED:" + clientInputSequence); // Debugging
                    // Hit sound is played inside applyPredictedHit
                } else {
                    // If player tried to hit but didn't collide with the ball, still send the input
                    sendMessage("INPUT:" + myPlayerNumber + ":HIT_PRESSED:" + clientInputSequence);
                    System.out.println("客戶端發送: INPUT:" + myPlayerNumber + ":HIT_PRESSED:" + clientInputSequence
                            + " (No ball collision)"); // Debugging
                }
            }
        }
    }

    // Send a chat message (Client side)
    private void sendChatMessage() {
        if (isConnected && (currentState == GameState.IN_ROOM || currentState == GameState.GAME)) {
            String message = chatInput.getText().trim();
            if (!message.isEmpty()) {
                // Format: CHAT:<senderName>:<messageContent>
                sendMessage("CHAT:" + myPlayerName + ":" + message);
                System.out.println("客戶端發送: CHAT:" + myPlayerName + ":" + message); // Debugging
                chatInput.clear(); // Clear input field after sending
            }
        } else {
            // Optionally display a message to the user that chat is not available
            System.out.println("Chat is not available at this time.");
        }
    }

    // Process network messages queue in the JavaFX UI thread
    private void processNetworkMessages() {
        String msg;
        while ((msg = messageQueue.poll()) != null) {
            handleNetworkMessage(msg);
        }
    }

    // Handle received network messages (Client side)
    private void handleNetworkMessage(String msg) {
        String[] parts = msg.split(":", 2); // Split only once to preserve colons in message content
        if (parts.length < 1)
            return;

        String messageType = parts[0];
        String messageContent = parts.length > 1 ? parts[1] : "";

        System.out.println("客戶端收到訊息: " + msg); // Debugging received messages

        switch (messageType) {
            case "ROOM_LIST":
                if (currentState == GameState.LOBBY && messageContent.length() > 0) { // Client receives room list in
                                                                                      // LOBBY state
                    String[] rooms = messageContent.split(",");
                    Platform.runLater(() -> {
                        roomListView.getItems().clear();
                        for (String roomEntry : rooms) {
                            if (!roomEntry.isEmpty()) {
                                // Room entry format: RoomName(Player1Name,Player2Name)
                                String[] roomParts = roomEntry.split("\\(");
                                if (roomParts.length == 2) {
                                    String roomName = roomParts[0];
                                    String playerInfo = roomParts[1].replace(")", ""); // Remove closing parenthesis
                                    String[] playerNames = playerInfo.split(",");
                                    String displayString = roomName + " (";
                                    if (playerNames.length == 2) {
                                        displayString += (playerNames[0].equals("Empty") ? "空位" : playerNames[0]);
                                        displayString += ", ";
                                        displayString += (playerNames[1].equals("Empty") ? "空位" : playerNames[1]);
                                    } else {
                                        displayString += "無玩家"; // Should not happen with 1v1
                                    }
                                    displayString += ")";
                                    roomListView.getItems().add(displayString);
                                } else {
                                    // Handle unexpected format
                                    roomListView.getItems().add(roomEntry);
                                }
                            }
                        }
                    });
                }
                break;
            case "ROOM_CREATED":
                if (currentState == GameState.LOBBY && parts.length == 2) { // Client receives room created confirmation
                    String roomName = parts[1];
                    Platform.runLater(() -> lobbyStatusLabel.setText("狀態: 房間 '" + roomName + "' 已建立. 等待客戶端連線..."));
                }
                break;
            case "ROOM_JOINED":
                if (currentState == GameState.LOBBY && parts.length == 5) { // Client receives room joined confirmation
                                                                            // with names
                    String roomName = parts[1];
                    int playerNum = Integer.parseInt(parts[2]);
                    String p1Name = parts[3]; // Player 1's name in the room
                    String p2Name = parts[4]; // Player 2's name in the room

                    activeRoomName = roomName; // Set active room name
                    myPlayerNumber = playerNum; // Set my player number

                    if (myPlayerNumber == 1) {
                        myPlayerName = p1Name; // My name is player 1's name
                        opponentPlayerName = p2Name; // Opponent is player 2's name
                    } else { // myPlayerNumber == 2
                        myPlayerName = p2Name; // My name is player 2's name
                        opponentPlayerName = p1Name; // Opponent is player 1's name
                    }

                    Platform.runLater(() -> {
                        roomInfoLabel.setText("房間: " + activeRoomName);
                        updateRoomStatusLabels(); // Update initial status
                        System.out.println("DEBUG: Calling transitionToInRoom() from ROOM_JOINED handler."); // Debugging
                        transitionToInRoom(); // Client transitions to IN_ROOM state
                    });
                }
                break;
            case "ROOM_ERROR":
                if (currentState == GameState.LOBBY && messageContent.length() > 0) { // Client receives room error
                    String errorMessage = messageContent;
                    Platform.runLater(() -> lobbyStatusLabel.setText("錯誤: " + errorMessage));
                }
                break;
            case "PLAYER_STATUS":
                // 處理玩家狀態更新 (客戶端仍然需要知道對手的連線狀態和名稱，但不處理準備狀態)
                if (currentState == GameState.IN_ROOM && parts.length == 5) {
                    try {
                        String roomName = parts[1];
                        int playerNum = Integer.parseInt(parts[2]);
                        String status = parts[3]; // "READY", "NOT_READY", "NOT_CONNECTED"
                        String playerName = parts[4];

                        if (roomName.equals(activeRoomName)) {
                            if (playerNum == 1) {
                                // Update player 1's name displayed on client if it's not my player
                                if (myPlayerNumber != 1)
                                    opponentPlayerName = playerName;
                            } else if (playerNum == 2) {
                                // Update player 2's name displayed on client if it's not my player
                                if (myPlayerNumber != 2)
                                    opponentPlayerName = playerName;
                            }
                            Platform.runLater(() -> updateRoomStatusLabels()); // Update labels with names/connection
                                                                               // status
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid number format in PLAYER_STATUS message: " + msg);
                    }
                }
                break;
            case "START_COUNTDOWN":
                if (currentState == GameState.IN_ROOM && parts.length == 2) { // Client receives start countdown message
                    try {
                        double initialCountdown = Double.parseDouble(parts[1]);
                        startCountdownTimer = initialCountdown;
                        isCountingDown = true;
                        Platform.runLater(() -> countdownLabel.setVisible(true)); // Show countdown label
                        System.out.println("客戶端收到: START_COUNTDOWN, 開始倒數: " + initialCountdown); // Debugging
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid number format in START_COUNTDOWN message: " + msg);
                    }
                }
                break;
            case "STOP_COUNTDOWN":
                if (currentState == GameState.IN_ROOM) { // Client receives stop countdown message
                    stopCountdown();
                    System.out.println("客戶端收到: STOP_COUNTDOWN, 停止倒數."); // Debugging
                }
                break;
            case "START_ROOM_GAME":
                if (currentState == GameState.IN_ROOM && parts.length == 2) { // Client receives start room game message
                    String roomName = parts[1];
                    if (roomName.equals(activeRoomName)) {
                        startGame(); // Start the game
                        System.out.println("客戶端收到: START_ROOM_GAME, 遊戲開始!"); // Debugging
                    }
                }
                break;
            case "LEFT_ROOM":
                // Client receives confirmation they left the room (handled by button click)
                // Transition to lobby already happened on button click
                System.out.println("客戶端收到: LEFT_ROOM"); // Debugging
                break;
            case "PLAYER_LEFT":
                if ((currentState == GameState.IN_ROOM || currentState == GameState.GAME
                        || currentState == GameState.GAME_OVER) && activeRoomName != null) { // Client receives player
                                                                                             // left message
                    Platform.runLater(() -> {
                        lobbyStatusLabel.setText("狀態: 對手已離開房間");
                        // Client transitions back to lobby
                        transitionToLobby();
                        System.out.println("客戶端收到: PLAYER_LEFT, 回到大廳."); // Debugging
                    });
                }
                break;
            case "CHAT":
                if ((currentState == GameState.IN_ROOM || currentState == GameState.GAME)
                        && messageContent.length() > 0) { // Receive chat message in room or game
                    // Message content already includes senderName:messageContent due to split(msg,
                    // 2)
                    String[] chatParts = messageContent.split(":", 2);
                    if (chatParts.length == 2) {
                        String senderName = chatParts[0];
                        String chatMessage = chatParts[1];
                        addChatMessage(senderName, chatMessage); // Add message to chat display
                    } else {
                        System.err.println("Invalid CHAT message format: " + msg);
                    }
                }
                break;
            case "STATE":
                // Client handles STATE message (includes input sequence number) only in GAME
                // state
                if (currentState == GameState.GAME && parts.length == 6) {
                    try {
                        int inputSequence = Integer.parseInt(parts[1]); // Server's last processed input sequence number
                        String[] ballData = parts[2].split(",");
                        String[] p1Data = parts[3].split(",");
                        String[] p2Data = parts[4].split(",");
                        String[] scoreData = parts[5].split(",");

                        if (ballData.length == 4 && p1Data.length == 5 && p2Data.length == 5 && scoreData.length == 2) {

                            // --- Ball Reconciliation ---
                            // If the server's state is for a sequence number >= the last predicted hit
                            // sequence,
                            // use the server's state as the new base for the ball.
                            if (ball.isPredicted && inputSequence >= ball.predictedByInputSequence) {
                                // Server has processed the hit that we predicted. Use server's ball state.
                                ball.setState(Double.parseDouble(ballData[0]), Double.parseDouble(ballData[1]),
                                        Double.parseDouble(ballData[2]), Double.parseDouble(ballData[3]));
                                ball.isPredicted = false; // Stop predicting
                                ball.predictedByInputSequence = -1; // Reset predicted sequence
                                System.out.println("客戶端收到 STATE: 球狀態由伺服器協調."); // Debugging
                            } else if (!ball.isPredicted) {
                                // No active prediction, or server hasn't caught up to prediction yet.
                                // Update ball state for interpolation based on server data.
                                ball.setState(Double.parseDouble(ballData[0]), Double.parseDouble(ballData[1]),
                                        Double.parseDouble(ballData[2]), Double.parseDouble(ballData[3]));
                                // System.out.println("客戶端收到 STATE: 球狀態用於插值."); // Debugging
                            }
                            // Note: If ball.isPredicted is true and inputSequence <
                            // ball.predictedByInputSequence,
                            // we continue using the client's predicted ball state for now. This might cause
                            // temporary divergence.

                            // Update remote player state based on myPlayerNumber (sets previous and current
                            // for interpolation)
                            if (myPlayerNumber == 1) { // I am player 1, player 2 is remote
                                player2.setState(Double.parseDouble(p2Data[0]), Double.parseDouble(p2Data[1]),
                                        Double.parseDouble(p2Data[2]), Double.parseDouble(p2Data[3]),
                                        PlayerState.fromValue(Integer.parseInt(p2Data[4])));

                                // Player 1 (local) state is handled by prediction/reconciliation below
                                Player localPlayer = player1;
                                String[] localPlayerData = (myPlayerNumber == 1) ? p1Data : p2Data; // Ensure we get the
                                                                                                    // correct local
                                                                                                    // player data from
                                                                                                    // the state message
                                double serverLocalPlayerX = Double.parseDouble(localPlayerData[0]);
                                double serverLocalPlayerY = Double.parseDouble(localPlayerData[1]);
                                double serverLocalPlayerVX = Double.parseDouble(localPlayerData[2]);
                                double serverLocalPlayerVY = Double.parseDouble(localPlayerData[3]);
                                PlayerState serverLocalPlayerState = PlayerState
                                        .fromValue(Integer.parseInt(localPlayerData[4]));

                                // Set local player state to the authoritative server state (reconciliation
                                // base)
                                localPlayer.setState(serverLocalPlayerX, serverLocalPlayerY, serverLocalPlayerVX,
                                        serverLocalPlayerVY, serverLocalPlayerState);

                            } else if (myPlayerNumber == 2) { // I am player 2, player 1 is remote
                                player1.setState(Double.parseDouble(p1Data[0]), Double.parseDouble(p1Data[1]),
                                        Double.parseDouble(p1Data[2]), Double.parseDouble(p1Data[3]),
                                        PlayerState.fromValue(Integer.parseInt(p1Data[4])));

                                // Player 2 (local) state is handled by prediction/reconciliation below
                                Player localPlayer = player2;
                                String[] localPlayerData = (myPlayerNumber == 1) ? p1Data : p2Data; // Ensure we get the
                                                                                                    // correct local
                                                                                                    // player data from
                                                                                                    // the state message
                                double serverLocalPlayerX = Double.parseDouble(localPlayerData[0]);
                                double serverLocalPlayerY = Double.parseDouble(localPlayerData[1]);
                                double serverLocalPlayerVX = Double.parseDouble(localPlayerData[2]);
                                double serverLocalPlayerVY = Double.parseDouble(localPlayerData[3]);
                                PlayerState serverLocalPlayerState = PlayerState
                                        .fromValue(Integer.parseInt(localPlayerData[4]));

                                // Set local player state to the authoritative server state (reconciliation
                                // base)
                                localPlayer.setState(serverLocalPlayerX, serverLocalPlayerY, serverLocalPlayerVX,
                                        serverLocalPlayerVY, serverLocalPlayerState);
                            }

                            // Update score
                            scorePlayer1 = Integer.parseInt(scoreData[0]);
                            scorePlayer2 = Integer.parseInt(scoreData[1]);

                            // --- Client-side Prediction Reconciliation ---
                            // Record server's last processed input sequence number
                            lastProcessedInputSequenceClient = inputSequence;

                            // Get the correct local player object for reconciliation
                            Player localPlayerForReconciliation = (myPlayerNumber == 1) ? player1 : player2;

                            // Remove input history up to the last processed sequence number
                            Iterator<InputState> iterator = inputHistory.iterator();
                            while (iterator.hasNext()) {
                                InputState input = iterator.next();
                                if (input.sequenceNumber <= lastProcessedInputSequenceClient) {
                                    iterator.remove();
                                } else {
                                    // Input history is sorted by sequence number, can stop removing once we find a
                                    // higher one
                                    break;
                                }
                            }

                            // Re-simulate physics from the server state using remaining input history
                            // Need a fixed time step for re-simulation to be consistent
                            double simulationTimeStep = 1.0 / 60.0; // Assuming 60 FPS simulation
                            for (InputState input : inputHistory) {
                                // Re-simulate player physics for this input
                                localPlayerForReconciliation.simulatePhysics(input.activeKeys, simulationTimeStep);
                                // Note: simulatePhysics updates position, velocity, and isJumping/currentState
                                // based on input
                            }
                            // System.out.println("客戶端收到 STATE: 協調完成，剩餘輸入歷史數量: " + inputHistory.size()); //
                            // Debugging

                            // ImageView positions are updated inside the update methods based on
                            // prediction/reconciliation/interpolation.

                        } else {
                            System.err.println("Invalid STATE data format: " + msg);
                        }

                    } catch (NumberFormatException e) {
                        System.err.println("Invalid number format in STATE message: " + msg);
                    }
                }
                break;
            case "HIT_BALL":
                // Server confirms a hit occurred. Client already predicted and played sound.
                // This message can be used for additional effects or more robust reconciliation
                // if needed.
                // For now, we'll just log it or potentially use it to correct ball state if
                // prediction was off.
                if (currentState == GameState.GAME && parts.length == 2) { // Received hit event only in GAME state
                    try {
                        int hittingPlayerNum = Integer.parseInt(parts[1]);
                        // The sound is played on client prediction in checkAndSendInputAndPredict.
                        // This server message confirms the hit and is the authority.
                        // If client prediction was wrong (e.g., didn't collide), the server STATE will
                        // correct the ball.
                        // We could add a visual effect here based on server confirmation if desired.
                        System.out.println("客戶端收到: HIT_BALL, 伺服器確認玩家 " + hittingPlayerNum + " 擊球."); // Debugging
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid HIT_BALL message format: " + msg);
                    }
                }
                break;
            case "SCORE":
                if (currentState == GameState.GAME && parts.length == 2) { // Received score event only in GAME state
                    try {
                        int scoringPlayerNum = Integer.parseInt(parts[1]);
                        // Play score sound (client plays when receiving score event)
                        if (scoreSound != null) {
                            soundPlay(scoreSound);
                        }
                        System.out.println("客戶端收到: SCORE, 玩家 " + scoringPlayerNum + " 得分."); // Debugging
                        // Score update and ball reset will be synchronized via subsequent STATE or
                        // BALL_RESET messages
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid SCORE message format: " + msg);
                    }
                }
                break;
            case "BALL_RESET":
                if (currentState == GameState.GAME && parts.length == 5) { // Client receives ball reset message only in
                                                                           // GAME state
                    try {
                        // Update ball state (sets previous and current for interpolation)
                        ball.setState(Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
                                Double.parseDouble(parts[3]), Double.parseDouble(parts[4]));
                        System.out.println("客戶端收到: BALL_RESET, 球已重置."); // Debugging

                        // ImageView position is updated inside the update method based on
                        // interpolation.
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid BALL_RESET message format: " + msg);
                    }
                }
                break;
            case "GAME_OVER":
                if (currentState == GameState.GAME && parts.length == 2) { // Received game over message only in GAME
                                                                           // state
                    try {
                        int winner = Integer.parseInt(parts[1]);
                        gameOver = true;
                        // Determine winner name based on myPlayerNumber
                        if (myPlayerNumber == winner) {
                            gameOverMessage = myPlayerName + " 獲勝！";
                        } else {
                            gameOverMessage = opponentPlayerName + " 獲勝！";
                        }
                        System.out.println("客戶端收到: GAME_OVER, 獲勝者: " + winner); // Debugging
                        currentState = GameState.GAME_OVER; // Transition to GAME_OVER state
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid GAME_OVER message format: " + msg);
                    }
                }
                break;

            default:
                System.out.println("客戶端收到未知訊息類型: " + messageType); // Debugging
                break;
        }
    }

    // Helper method to play sound on the JavaFX UI thread
    private void soundPlay(AudioClip clip) {
        Platform.runLater(() -> {
            if (clip != null) {
                clip.play();
            }
        });
    }

    // Update connection status UI and transition to LOBBY
    private void setConnectionStatus(boolean connected) {
        isConnected = connected;
        Platform.runLater(() -> {
            if (connected) {
                statusLabel.setText("狀態: 已連線");
                System.out.println("客戶端狀態: 已連線."); // Debugging
                // After successful connection, transition to LOBBY state
                transitionToLobby();

            } else {
                statusLabel.setText("狀態: 連線失敗或已中斷");
                System.out.println("客戶端狀態: 連線失敗或已中斷."); // Debugging
                // Connection lost, transition back to SETUP and show network setup pane
                currentState = GameState.SETUP;
                networkSetupPane.setVisible(true);
                lobbyPane.setVisible(false);
                inRoomPane.setVisible(false);
                // Reset game state variables
                gameStarted = false;
                gameOver = false;
                activeRoomName = null;
                inputHistory.clear(); // Client side
                clientInputSequence = 0; // Client side
                lastProcessedInputSequenceClient = 0; // Client side
                myPlayerNumber = 0; // Reset player number
                myPlayerName = "玩家"; // Reset player name
                opponentPlayerName = "對手"; // Reset opponent name
            }
        });
    }

    // Show waiting message (used during initial connection)
    private void showWaitingMessage(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }

    // Transition to Lobby state
    private void transitionToLobby() {
        currentState = GameState.LOBBY;
        Platform.runLater(() -> {
            System.out.println("DEBUG: transitionToLobby() called. Hiding inRoomPane, showing lobbyPane."); // Debugging
            inRoomPane.setVisible(false); // Hide in-room pane
            lobbyPane.setVisible(true); // Show lobby pane
            // Hide game elements
            player1.imageView.setVisible(false);
            player2.imageView.setVisible(false);
            ball.imageView.setVisible(false);
            // Hide countdown label
            countdownLabel.setVisible(false);

            // Clear chat on returning to lobby
            chatDisplayArea.clear();

            lobbyStatusLabel.setText("狀態: 在大廳");
            System.out.println("客戶端狀態: 進入大廳."); // Debugging
            // Request room list from server (client side)
            if (isConnected) {
                sendMessage("LIST_ROOMS");
            }
            myPlayerNumber = 0; // Reset player number
            myPlayerName = playerNameField.getText().trim(); // Keep player name from input field
            if (myPlayerName.isEmpty())
                myPlayerName = "玩家";
            opponentPlayerName = "對手"; // Reset opponent name
            stopCountdown(); // Ensure countdown is stopped
        });
    }

    // Transition to In Room state
    private void transitionToInRoom() {
        currentState = GameState.IN_ROOM;
        Platform.runLater(() -> {
            System.out.println("DEBUG: transitionToInRoom() called. Hiding lobbyPane, showing inRoomPane."); // Debugging
            lobbyPane.setVisible(false); // Hide lobby pane
            inRoomPane.setVisible(true); // Show in-room pane
            // Hide game elements (they become visible in startGame)
            player1.imageView.setVisible(false);
            player2.imageView.setVisible(false);
            ball.imageView.setVisible(false);
            // Hide countdown label initially
            countdownLabel.setVisible(false);

            // Clear chat on entering a room
            chatDisplayArea.clear();

            // roomInfoLabel and player status labels are updated in handleNetworkMessage
            // (ROOM_JOINED, PLAYER_STATUS)
            System.out.println("客戶端狀態: 進入房間 '" + activeRoomName + "'. 我的玩家編號: " + myPlayerNumber); // Debugging
        });
    }

    // Add a message to the chat display area
    private void addChatMessage(String sender, String message) {
        Platform.runLater(() -> {
            chatDisplayArea.appendText(sender + ": " + message + "\n");
            // Auto-scroll to the bottom
            chatDisplayArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    // Start game (triggered by server sending START_ROOM_GAME message)
    private void startGame() {
        Platform.runLater(() -> {
            gameStarted = true;
            gameOver = false;
            scorePlayer1 = 0;
            scorePlayer2 = 0;
            currentState = GameState.GAME; // Transition to GAME state

            // Set initial positions and player numbers based on myPlayerNumber
            // These initial positions will be immediately overwritten by the first STATE
            // message from the server,
            // but it's good practice to set them.
            if (myPlayerNumber == 1) {
                // I am player 1 (left)
                player1.x = 50;
                player1.y = HEIGHT - player1.height;
                player1.playerNumber = 1;
                player1.playerName = myPlayerName;
                player2.x = WIDTH - 100;
                player2.y = HEIGHT - player2.height;
                player2.playerNumber = 2;
                player2.playerName = opponentPlayerName;
                // Ensure ImageViews are set correctly
                player1.imageView.setImage(player1SpriteSheet);
                player2.imageView.setImage(player2SpriteSheet);

            } else { // myPlayerNumber == 2 (right)
                // I am player 2 (right)
                player1.x = 50;
                player1.y = HEIGHT - player1.height;
                player1.playerNumber = 1;
                player1.playerName = opponentPlayerName; // Opponent is player 1
                player2.x = WIDTH - 100;
                player2.y = HEIGHT - player2.height;
                player2.playerNumber = 2;
                player2.playerName = myPlayerName; // My name
                // Ensure ImageViews are set correctly
                player1.imageView.setImage(player1SpriteSheet);
                player2.imageView.setImage(player2SpriteSheet);
            }
            // Ensure ImageView positions are updated initially (before interpolation takes
            // over)
            player1.imageView.setLayoutX(player1.x);
            player1.imageView.setLayoutY(player1.y);
            player2.imageView.setLayoutX(player2.x);
            player2.imageView.setLayoutY(player2.y);

            // Reset ball (client waits for BALL_RESET or first STATE message from server)
            // Initial ball state will be sent in the first STATE message
            ball.isPredicted = false; // Reset ball prediction state
            ball.predictedByInputSequence = -1;

            // Hide in-room pane
            inRoomPane.setVisible(false); // This should be false, as we are transitioning TO the game screen
            // Show game elements
            player1.imageView.setVisible(true);
            player2.imageView.setVisible(true);
            ball.imageView.setVisible(true);

            stopCountdown(); // Ensure countdown is stopped
            // Clear input history and sequence numbers for new game
            inputHistory.clear();
            clientInputSequence = 0;
            lastProcessedInputSequenceClient = 0;
            System.out.println("客戶端狀態: 遊戲開始!"); // Debugging
        });
    }

    // Start the game start countdown (Client side display)
    private void startCountdown() {
        // This method is now triggered by the server
        if (!isCountingDown) {
            startCountdownTimer = START_COUNTDOWN_SECONDS;
            isCountingDown = true;
            Platform.runLater(() -> countdownLabel.setVisible(true)); // Show countdown label
            // Client receives START_COUNTDOWN message from server
        }
    }

    // Stop the game start countdown (Client side display)
    private void stopCountdown() {
        isCountingDown = false;
        startCountdownTimer = START_COUNTDOWN_SECONDS; // Reset timer
        Platform.runLater(() -> {
            countdownLabel.setVisible(false); // Hide countdown label
            countdownLabel.setText(""); // Clear text
        });
        // Client receives STOP_COUNTDOWN message from server
    }

    // Update player status labels in the in-room pane (Client side)
    private void updateRoomStatusLabels() {
        if (myPlayerNumber == 1) { // I am player 1
            // 只顯示玩家名稱和連線狀態 (不再顯示準備狀態)
            String p2Status = (player2 != null && player2.playerName != null && !player2.playerName.equals("空位"))
                    ? "已連線"
                    : "等待連線...";
            player1StatusLabel.setText(myPlayerName + ": 已連線");
            player2StatusLabel.setText(opponentPlayerName + ": " + p2Status);
        } else if (myPlayerNumber == 2) { // I am player 2
            // 只顯示玩家名稱和連線狀態 (不再顯示準備狀態)
            String p1Status = (player1 != null && player1.playerName != null && !player1.playerName.equals("等待..."))
                    ? "已連線"
                    : "等待連線...";
            player1StatusLabel.setText(opponentPlayerName + ": " + p1Status);
            player2StatusLabel.setText(myPlayerName + ": 已連線");
        } else { // Before joining a room
            player1StatusLabel.setText("玩家 1: ");
            player2StatusLabel.setText("玩家 2: ");
        }
    }

    // Draw Canvas content (net, UI) (Client side)
    private void draw(GraphicsContext gc) {
        // Clear old drawing on Canvas
        gc.clearRect(0, 0, WIDTH, HEIGHT);

        // Draw net (using Canvas for flexibility)
        gc.setFill(Color.GRAY); // Net color
        gc.fillRect(WIDTH / 2 - 5, 0, 10, HEIGHT); // Simple net

        // Draw score (only in GAME or GAME_OVER state)
        if (currentState == GameState.GAME || currentState == GameState.GAME_OVER) {
            drawScoreboard(gc); // Draw scoreboard on canvas
        }

        // Draw UI based on current state
        if (currentState == GameState.SETUP) {
            // Network setup pane is visible, no extra message needed here
        } else if (currentState == GameState.LOBBY) {
            // Lobby pane is visible, lobby status label shows messages
        } else if (currentState == GameState.GAME_OVER) {
            gc.setFill(Color.BLACK);
            gc.setFont(new Font("Arial", 20));
            gc.setTextAlign(TextAlignment.CENTER);
            String message = gameOverMessage + "\n按 Enter 鍵回到大廳"; // Changed message to return to lobby
            gc.fillText(message, WIDTH / 2, HEIGHT / 2);
        }
        // Note: In IN_ROOM state, UI is handled by inRoomPane, no extra drawing on
        // canvas needed here
    }

    // Draw the scoreboard on the game canvas (Client side)
    private void drawScoreboard(GraphicsContext gc) {
        gc.setFill(Color.BLACK);
        gc.setFont(new Font("Arial", 30));
        gc.setTextAlign(TextAlignment.CENTER);

        // Determine which player is on which side visually
        String player1DisplayName = (myPlayerNumber == 1) ? myPlayerName : opponentPlayerName;
        String player2DisplayName = (myPlayerNumber == 2) ? myPlayerName : opponentPlayerName;

        // Draw player names and scores
        gc.fillText(player1DisplayName + ": " + scorePlayer1, WIDTH / 4, 50); // Player 1 score on left side
        gc.fillText(player2DisplayName + ": " + scorePlayer2, WIDTH * 3 / 4, 50); // Player 2 score on right side
    }

    @Override
    public void stop() throws Exception {
        // Stop network connection when application closes
        closeConnection();
        super.stop();
    }

    // Entry point
    public static void main(String[] args) {
        launch(args); // Launch the JavaFX application
    }

    // 處理鍵盤輸入
    private void handleKeyPress(KeyCode code) {
        activeKeys.add(code);
        // 在 GAME_OVER 狀態下，按 Enter 鍵返回大廳
        if (currentState == GameState.GAME_OVER && code == KeyCode.ENTER) {
            transitionToLobby();
        }
    }

    private void handleKeyRelease(KeyCode code) {
        activeKeys.remove(code);
    }
}
