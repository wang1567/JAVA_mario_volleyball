package com.example;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.text.DecimalFormat;
import javafx.scene.image.Image; // 伺服器端物理模擬不需要圖片，但 Player 類別結構保留
import javafx.scene.input.KeyCode; // 伺服器端需要 KeyCode 來解析客戶端輸入
import javafx.geometry.Rectangle2D; // 確保導入 Rectangle2D 類

// 伺服器主應用程式
public class MarioVolleyballServer {

    private static final int DEFAULT_PORT = 12345;
    private ServerSocket serverSocket;
    private ExecutorService clientHandlingThreadPool; // 線程池處理客戶端連線

    // 管理所有連線的客戶端
    private Set<ClientHandler> clients = Collections.synchronizedSet(new HashSet<>());

    // 管理遊戲房間 (使用 Map<房間名稱, 房間>)
    private Map<String, GameRoom> gameRooms = new ConcurrentHashMap<>();

    // 用於格式化浮點數
    private DecimalFormat decimalFormat = new DecimalFormat("#.##");

    // 物理參數 (伺服器權威)
    private static final double GRAVITY = 800; // 重力加速度 (像素/秒^2)
    private static final double PLAYER_MOVE_SPEED = 250; // 玩家水平移動速度 (像素/秒)
    private static final double PLAYER_JUMP_STRENGTH = -450; // 玩家跳躍初始垂直速度 (像素/秒，負值表示向上)
    private static final double GROUND_FRICTION = 0.9; // 地面摩擦力 (影響水平速度的反彈)
    private static final double BOUNCE_FACTOR = 0.7; // 碰撞反彈係數 (影響垂直速度的反彈)
    private static final double HIT_STRENGTH = 350; // 擊球力量係數
    private static final double PLAYER_HIT_COOLDOWN = 0.3; // 玩家擊球冷卻時間 (秒)

    // 遊戲視窗寬高 (伺服器端也需要知道以便進行物理模擬)
    private static final double WIDTH = 800;
    private static final double HEIGHT = 600;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("無效的端口號格式，使用默認端口 " + DEFAULT_PORT);
            }
        }
        MarioVolleyballServer server = new MarioVolleyballServer();
        server.start(port);
    }

    public void start(int port) {
        // 初始化線程池，處理客戶端連線
        clientHandlingThreadPool = Executors.newCachedThreadPool();

        try {
            serverSocket = new ServerSocket(port);
            System.out.println("伺服器已啟動，監聽端口 " + port);

            // 啟動一個線程來處理房間遊戲邏輯 (簡化為一個線程處理所有房間)
            new Thread(this::runGameRooms).start();

            // 主循環：接受新的客戶端連線
            while (!serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("新客戶端連線: " + clientSocket.getInetAddress().getHostAddress());

                    // 為每個客戶端創建一個處理器並提交到線程池
                    ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                    clients.add(clientHandler);
                    clientHandlingThreadPool.submit(clientHandler);

                } catch (IOException e) {
                    if (serverSocket.isClosed()) {
                        System.out.println("伺服器套接字已關閉.");
                    } else {
                        System.err.println("接受客戶端連線錯誤: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("無法啟動伺服器: " + e.getMessage());
        } finally {
            stop();
        }
    }

    public void stop() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("關閉伺服器套接字錯誤: " + e.getMessage());
        }
        // 關閉所有客戶端連線
        for (ClientHandler client : clients) {
            client.closeConnection();
        }
        clients.clear();
        // 關閉線程池
        if (clientHandlingThreadPool != null) {
            clientHandlingThreadPool.shutdownNow();
        }
        System.out.println("伺服器已停止.");
    }

    // 處理客戶端斷開連線
    public void removeClient(ClientHandler clientHandler) {
        clients.remove(clientHandler);
        System.out.println("客戶端已斷開連線: " + clientHandler.clientSocket.getInetAddress().getHostAddress());

        // 如果客戶端在房間裡，處理離開房間邏輯
        GameRoom room = clientHandler.getCurrentRoom();
        if (room != null) {
            room.removePlayer(clientHandler);
            // 如果房間變空，移除房間
            if (room.isEmpty()) {
                gameRooms.remove(room.getRoomName());
                System.out.println("房間 '" + room.getRoomName() + "' 已關閉.");
            } else {
                // 通知房間內的另一個玩家對方已離開
                room.broadcastMessage("PLAYER_LEFT");
            }
        }
    }

    // 處理客戶端訊息
    public void handleMessage(ClientHandler clientHandler, String message) {
        String[] parts = message.split(":", 2); // 只分割一次，保留訊息內容中的冒號
        if (parts.length < 1)
            return;

        String messageType = parts[0];
        String messageContent = parts.length > 1 ? parts[1] : "";

        System.out.println("收到訊息 (" + clientHandler.clientSocket.getInetAddress().getHostAddress() + "): " + message);

        switch (messageType) {
            case "LIST_ROOMS":
                sendRoomList(clientHandler);
                break;
            case "CREATE_ROOM":
                handleCreateRoom(clientHandler, messageContent);
                break;
            case "JOIN_ROOM":
                handleJoinRoom(clientHandler, messageContent);
                break;
            case "LEAVE_ROOM":
                handleLeaveRoom(clientHandler);
                break;
            case "READY":
                handleReadyStatus(clientHandler, messageContent);
                break;
            case "INPUT":
                // 將輸入轉發給客戶端所在的房間處理
                GameRoom roomForInput = clientHandler.getCurrentRoom();
                if (roomForInput != null && roomForInput.getState() == RoomState.GAME) {
                    roomForInput.handlePlayerInput(clientHandler, message);
                }
                break;
            case "CHAT":
                // 將聊天訊息轉發給客戶端所在的房間處理
                GameRoom roomForChat = clientHandler.getCurrentRoom();
                if (roomForChat != null && (roomForChat.getState() == RoomState.IN_ROOM
                        || roomForChat.getState() == RoomState.GAME)) {
                    // 重新組裝聊天訊息，避免內容中的冒號被分割
                    roomForChat.broadcastMessage("CHAT:" + messageContent);
                }
                break;

            default:
                System.out.println("未知訊息類型: " + messageType);
                break;
        }
    }

    // 發送房間列表給客戶端
    private void sendRoomList(ClientHandler clientHandler) {
        StringBuilder roomListMsg = new StringBuilder("ROOM_LIST:");
        boolean firstRoom = true;
        // 迭代所有房間
        for (GameRoom room : gameRooms.values()) {
            if (!firstRoom)
                roomListMsg.append(",");
            // 格式: 房間名稱(玩家1名稱,玩家2名稱)
            String p1Name = room.getPlayer1() != null ? room.getPlayer1().getPlayerName() : "Empty";
            String p2Name = room.getPlayer2() != null ? room.getPlayer2().getPlayerName() : "Empty";
            roomListMsg.append(room.getRoomName()).append("(").append(p1Name).append(",").append(p2Name).append(")");
            firstRoom = false;
        }
        clientHandler.sendMessage(roomListMsg.toString());
    }

    // 處理建立房間請求
    private void handleCreateRoom(ClientHandler clientHandler, String messageContent) {
        String[] parts = messageContent.split(":");
        if (parts.length < 2) {
            clientHandler.sendMessage("ROOM_ERROR:無效的建立房間格式");
            return;
        }
        String roomName = parts[0];
        String playerName = parts[1];

        if (roomName.isEmpty()) {
            clientHandler.sendMessage("ROOM_ERROR:房間名稱不能為空");
            return;
        }

        if (gameRooms.containsKey(roomName)) {
            clientHandler.sendMessage("ROOM_ERROR:房間 '" + roomName + "' 已存在");
            return;
        }

        if (clientHandler.getCurrentRoom() != null) {
            clientHandler.sendMessage("ROOM_ERROR:你已經在一個房間裡了");
            return;
        }

        // 創建新房間並將客戶端加入為玩家 1
        GameRoom newRoom = new GameRoom(roomName, this);
        gameRooms.put(roomName, newRoom);
        newRoom.addPlayer(clientHandler, 1, playerName); // 加入為玩家 1

        clientHandler.sendMessage("ROOM_CREATED:" + roomName);
        // 通知客戶端成功加入房間
        clientHandler.sendMessage("ROOM_JOINED:" + roomName + ":" + 1 + ":" + playerName + ":" + "空位"); // 玩家 2 初始為空位

        System.out.println("房間 '" + roomName + "' 已由 " + playerName + " 建立.");
    }

    // 處理加入房間請求
    private void handleJoinRoom(ClientHandler clientHandler, String messageContent) {
        String[] parts = messageContent.split(":");
        if (parts.length < 2) {
            clientHandler.sendMessage("ROOM_ERROR:無效的加入房間格式");
            return;
        }
        String roomName = parts[0];
        String playerName = parts[1];

        GameRoom room = gameRooms.get(roomName);
        if (room == null) {
            clientHandler.sendMessage("ROOM_ERROR:房間 '" + roomName + "' 不存在");
            return;
        }

        if (clientHandler.getCurrentRoom() != null) {
            clientHandler.sendMessage("ROOM_ERROR:你已經在一個房間裡了");
            return;
        }

        // 嘗試將客戶端加入房間為玩家 2
        if (room.addPlayer(clientHandler, 2, playerName)) { // 嘗試加入為玩家 2
            // 通知客戶端成功加入房間
            String p1Name = room.getPlayer1() != null ? room.getPlayer1().getPlayerName() : "Empty";
            clientHandler.sendMessage("ROOM_JOINED:" + roomName + ":" + 2 + ":" + p1Name + ":" + playerName); // 發送玩家 1
                                                                                                              // 和玩家 2
                                                                                                              // 的名稱

            System.out.println(playerName + " 已加入房間 '" + roomName + "'");
        } else {
            clientHandler.sendMessage("ROOM_ERROR:房間 '" + roomName + "' 已滿");
        }
    }

    // 處理離開房間請求
    private void handleLeaveRoom(ClientHandler clientHandler) {
        GameRoom room = clientHandler.getCurrentRoom();
        if (room != null) {
            room.removePlayer(clientHandler);
            clientHandler.sendMessage("LEFT_ROOM"); // 通知客戶端已離開房間

            // 如果房間變空，移除房間
            if (room.isEmpty()) {
                gameRooms.remove(room.getRoomName());
                System.out.println("房間 '" + room.getRoomName() + "' 已關閉.");
            }
        }
    }

    // 處理玩家準備狀態更新
    private void handleReadyStatus(ClientHandler clientHandler, String messageContent) {
        // 這個方法內容可以直接留空，或直接刪除整個方法
    }

    // 遊戲房間邏輯運行 (簡化為一個線程處理所有房間)
    private void runGameRooms() {
        long lastUpdateTime = System.nanoTime();
        double timePerFrame = 1_000_000_000.0 / 60.0; // 目標 60 FPS

        while (true) {
            long now = System.nanoTime();
            double deltaTime = (now - lastUpdateTime) / 1_000_000_000.0;
            lastUpdateTime = now;

            // 迭代所有房間並更新遊戲狀態
            Iterator<GameRoom> roomIterator = gameRooms.values().iterator();
            while (roomIterator.hasNext()) {
                GameRoom room = roomIterator.next();
                room.update(deltaTime);
                room.checkGameOver();
            }

            // 控制遊戲循環速度
            long sleepTime = (long) ((lastUpdateTime + timePerFrame - System.nanoTime()) / 1_000_000.0);
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break; // 退出循環
                }
            }
        }
    }

    // 遊戲狀態
    private enum RoomState {
        IN_ROOM, // 在房間中等待或準備
        COUNTDOWN, // 遊戲開始倒數
        GAME, // 遊戲進行中
        GAME_OVER // 遊戲結束
    }

    // 遊戲房間類別 (Server side)
    private class GameRoom {
        private String roomName;
        private ClientHandler player1Handler; // 房間裡的玩家 1 的處理器
        private ClientHandler player2Handler; // 房間裡的玩家 2 的處理器
        private MarioVolleyballServer server; // 對伺服器的引用

        // 遊戲元素 (權威狀態)
        private Player serverPlayer1;
        private Player serverPlayer2;
        private Ball serverBall;

        // 遊戲狀態
        private RoomState currentState = RoomState.IN_ROOM;

        // 比分
        private int scorePlayer1 = 0;
        private int scorePlayer2 = 0;
        private static final int MAX_SCORE = 5;

        // 伺服器端：追蹤客戶端 (player2) 最後處理的輸入序列號
        private int lastProcessedClientInputSequenceServer = 0;

        private boolean gameOver = false;

        public GameRoom(String roomName, MarioVolleyballServer server) {
            this.roomName = roomName;
            this.server = server;
            // 初始化遊戲元素 (伺服器端維護權威狀態)
            // 伺服器端物理模擬不需要圖片，但 Player/Ball 類別結構保留
            Image dummyImage = null; // Placeholder for server side

            serverPlayer1 = new Player(50, HEIGHT - 80, 50, 80, dummyImage, 50, 80, 1, "玩家 1");
            serverPlayer2 = new Player(WIDTH - 100, HEIGHT - 80, 50, 80, dummyImage, 50, 80, 2, "玩家 2");
            serverBall = new Ball(WIDTH / 4, HEIGHT / 2, 15, dummyImage, 30, 2);
        }

        public String getRoomName() {
            return roomName;
        }

        public ClientHandler getPlayer1() {
            return player1Handler;
        }

        public ClientHandler getPlayer2() {
            return player2Handler;
        }

        public RoomState getState() {
            return currentState;
        }

        // 添加玩家到房間
        public boolean addPlayer(ClientHandler clientHandler, int playerNum, String playerName) {
            if (playerNum == 1) {
                if (player1Handler == null) {
                    player1Handler = clientHandler;
                    clientHandler.setCurrentRoom(this);
                    clientHandler.setPlayerNumber(1);
                    clientHandler.setPlayerName(playerName);
                    serverPlayer1.playerName = playerName;
                    if (player2Handler != null) {
                        startGame(); // 兩人齊全自動開始
                    }
                    return true;
                }
            } else if (playerNum == 2) {
                if (player2Handler == null) {
                    player2Handler = clientHandler;
                    clientHandler.setCurrentRoom(this);
                    clientHandler.setPlayerNumber(2);
                    clientHandler.setPlayerName(playerName);
                    serverPlayer2.playerName = playerName;
                    if (player1Handler != null) {
                        startGame(); // 兩人齊全自動開始
                    }
                    return true;
                }
            }
            return false;
        }

        // 從房間移除玩家
        public void removePlayer(ClientHandler clientHandler) {
            if (player1Handler == clientHandler) {
                player1Handler = null;
                serverPlayer1.playerName = "等待..."; // 重置伺服器端玩家名稱
            } else if (player2Handler == clientHandler) {
                player2Handler = null;
                serverPlayer2.playerName = "空位"; // 重置伺服器端玩家名稱
            }
            clientHandler.setCurrentRoom(null);
            clientHandler.setPlayerNumber(0);
            clientHandler.setPlayerName(null);

            // 如果遊戲正在進行或倒數中，結束遊戲
            if (currentState == RoomState.GAME) {
                endGame();
            }
            // 如果房間變空，會在 server.removeClient 中移除房間
        }

        // 檢查房間是否為空
        public boolean isEmpty() {
            return player1Handler == null && player2Handler == null;
        }

        // 檢查房間是否已滿
        public boolean isFull() {
            return player1Handler != null && player2Handler != null;
        }

        // 廣播訊息給房間內所有玩家
        public void broadcastMessage(String message) {
            if (player1Handler != null)
                player1Handler.sendMessage(message);
            if (player2Handler != null)
                player2Handler.sendMessage(message);
        }

        // 開始遊戲
        public void startGame() {
            currentState = RoomState.GAME;
            gameOver = false;
            scorePlayer1 = 0;
            scorePlayer2 = 0;

            // 重置遊戲元素狀態 (Server authoritative)
            serverPlayer1.x = 50;
            serverPlayer1.y = HEIGHT - serverPlayer1.height;
            serverPlayer1.velocityX = 0;
            serverPlayer1.velocityY = 0;
            serverPlayer1.isJumping = false;
            serverPlayer1.currentState = PlayerState.IDLE;

            serverPlayer2.x = WIDTH - 100;
            serverPlayer2.y = HEIGHT - serverPlayer2.height;
            serverPlayer2.velocityX = 0;
            serverPlayer2.velocityY = 0;
            serverPlayer2.isJumping = false;
            serverPlayer2.currentState = PlayerState.IDLE;

            resetBall(); // 重置球的位置和速度

            // 重置客戶端輸入序列號追蹤
            lastProcessedClientInputSequenceServer = 0;

            broadcastMessage("START_ROOM_GAME:" + roomName);
            System.out.println("房間 '" + roomName + "' 遊戲開始!");
        }

        // 結束遊戲
        public void endGame() {
            currentState = RoomState.GAME_OVER;
            gameOver = true;
            int winningPlayer = (scorePlayer1 > scorePlayer2) ? 1 : 2;
            broadcastMessage("GAME_OVER:" + winningPlayer);
            System.out.println("房間 '" + roomName + "' 遊戲結束. 獲勝者: 玩家 " + winningPlayer);
        }

        // 處理玩家輸入 (Server side)
        public void handlePlayerInput(ClientHandler clientHandler, String message) {
            String[] parts = message.split(":");
            if (parts.length < 4)
                return; // INPUT:<playerNumber>:<action>:<sequenceNumber>

            try {
                int playerNum = Integer.parseInt(parts[1]);
                String action = parts[2];
                int sequenceNumber = Integer.parseInt(parts[3]);

                Player playerToUpdate = null;
                // 根據玩家編號和客戶端處理器匹配來確定是哪個玩家的輸入
                if (playerNum == 1 && player1Handler == clientHandler) {
                    playerToUpdate = serverPlayer1;
                } else if (playerNum == 2 && player2Handler == clientHandler) {
                    playerToUpdate = serverPlayer2;
                    // 更新伺服器對客戶端最後處理輸入序列號的追蹤 (只針對 player2)
                    lastProcessedClientInputSequenceServer = Math.max(lastProcessedClientInputSequenceServer,
                            sequenceNumber);
                }

                if (playerToUpdate == null)
                    return; // 無效的玩家或客戶端

                // 處理輸入動作 (應用到伺服器權威狀態)
                switch (action) {
                    case "A": // Move Left
                        playerToUpdate.velocityX = -PLAYER_MOVE_SPEED; // Use constant speed
                        break;
                    case "D": // Move Right
                        playerToUpdate.velocityX = PLAYER_MOVE_SPEED; // Use constant speed
                        break;
                    case "STOP_MOVE_A": // Stop Moving Left
                        if (playerToUpdate.velocityX < 0)
                            playerToUpdate.velocityX = 0;
                        break;
                    case "STOP_MOVE_D": // Stop Moving Right
                        if (playerToUpdate.velocityX > 0)
                            playerToUpdate.velocityX = 0;
                        break;
                    case "JUMP_PRESSED":
                        playerToUpdate.jump(); // 伺服器權威觸發跳躍
                        break;
                    case "HIT_PRESSED":
                        playerToUpdate.tryHit(); // 伺服器權威檢查擊球冷卻
                        // 實際的擊球碰撞處理在 checkCollisions 中進行
                        break;
                    // TODO: Handle other input types if needed
                    default:
                        // Handle full key state updates (sent periodically by client)
                        // This is a simplified approach, a more robust system would handle
                        // input bundles and apply them based on sequence number.
                        // For now, we just update velocity based on A/D keys if present.
                        if (action.contains(",")) { // Check if it's a comma-separated key list
                            Set<KeyCode> remoteKeys = new HashSet<>();
                            String[] keyStrings = action.split(",");
                            for (String keyStr : keyStrings) {
                                try {
                                    // Note: Server side uses KeyCode for simplicity in parsing,
                                    // but in a real server, you'd use custom enums or strings.
                                    remoteKeys.add(KeyCode.valueOf(keyStr));
                                } catch (IllegalArgumentException e) {
                                    System.err.println("Invalid KeyCode in INPUT message: " + keyStr);
                                }
                            }
                            playerToUpdate.velocityX = 0;
                            if (remoteKeys.contains(KeyCode.A))
                                playerToUpdate.velocityX = -PLAYER_MOVE_SPEED;
                            if (remoteKeys.contains(KeyCode.D))
                                playerToUpdate.velocityX = PLAYER_MOVE_SPEED;
                        }
                        break;
                }

            } catch (NumberFormatException e) {
                System.err.println("處理玩家輸入時無效的數字格式: " + message);
            }
        }

        // 更新遊戲房間狀態 (Server side authoritative update)
        public void update(double deltaTime) {
            if (currentState == RoomState.GAME) {
                // 更新遊戲元素狀態
                serverPlayer1.update(deltaTime); // 伺服器更新玩家 1 (根據接收到的輸入)
                serverPlayer2.update(deltaTime); // 伺服器更新玩家 2 (根據接收到的輸入)
                serverBall.update(deltaTime);

                // 碰撞檢測和處理 (伺服器權威)
                checkCollisions();

                // 計分和遊戲結束檢查 (伺服器權威)
                checkScoring();
                checkGameOver();

                // 定期發送遊戲狀態給客戶端
                sendGameState();

            }
        }

        // 檢查碰撞 (Server side)
        private void checkCollisions() {
            // Player 1 collision with ball
            if (checkCircleRectangleCollision(serverBall, serverPlayer1.getBounds())) {
                handlePlayerBallCollision(serverPlayer1, serverBall);
            }
            // Player 2 collision with ball
            if (checkCircleRectangleCollision(serverBall, serverPlayer2.getBounds())) {
                handlePlayerBallCollision(serverPlayer2, serverBall);
            }

            // Net collision with ball
            Rectangle2D netBounds = new Rectangle2D(WIDTH / 2 - 5, 0, 10, HEIGHT);
            if (checkCircleRectangleCollision(serverBall, netBounds)) {
                handleNetBallCollision(serverBall, netBounds);
            }
        }

        // 檢查圓形-矩形碰撞
        private boolean checkCircleRectangleCollision(Ball ball, Rectangle2D rectangle) {
            // Find the closest point to the circle center on the rectangle
            double closestX = Math.max(rectangle.getMinX(), Math.min(ball.x, rectangle.getMaxX()));
            double closestY = Math.max(rectangle.getMinY(), Math.min(ball.y, rectangle.getMaxY()));

            // Calculate the distance between the circle center and the closest point
            double distanceX = ball.x - closestX;
            double distanceY = ball.y - closestY;
            double distanceSquared = (distanceX * distanceX) + (distanceY * distanceY);

            // If the distance is less than the circle's radius, an intersection occurs
            return distanceSquared < (ball.radius * ball.radius);
        }

        // 處理玩家-球碰撞 (Server side)
        private void handlePlayerBallCollision(Player player, Ball ball) {
            // Prevent duplicate collisions (simple approach)
            boolean isPlayer1 = (player.playerNumber == 1);
            // Check if the ball was just touched by the other player
            if (isPlayer1 && ball.lastTouchedByPlayer1)
                return;
            if (!isPlayer1 && !ball.lastTouchedByPlayer1 && ball.lastTouchedByPlayer1)
                return; // Fixed logic

            // Calculate vector from player center to ball center
            double playerCenterX = player.x + player.width / 2;
            double playerCenterY = player.y + player.height / 2;
            double ballCenterX = ball.x;
            double ballCenterY = ball.y;

            double deltaX = ballCenterX - playerCenterX;
            double deltaY = ballCenterY - playerCenterY;

            // Calculate ball velocity after collision
            double newVelocityX = deltaX * 0.5 + player.velocityX * 0.8 + (Math.random() - 0.5) * 50;
            double newVelocityY = deltaY * 0.5 + player.velocityY * 0.8 - HIT_STRENGTH + (Math.random() - 0.5) * 50; // Use
                                                                                                                     // constant
                                                                                                                     // HIT_STRENGTH

            double maxBallSpeed = 750;
            double currentBallSpeed = Math.sqrt(newVelocityX * newVelocityX + newVelocityY * newVelocityY);
            if (currentBallSpeed > maxBallSpeed) {
                double scaleFactor = maxBallSpeed / currentBallSpeed;
                newVelocityX *= scaleFactor;
                newVelocityY *= scaleFactor;
            }

            ball.velocityX = newVelocityX;
            ball.velocityY = newVelocityY;

            // Record which player last touched the ball (player 1 or player 2)
            ball.lastTouchedByPlayer1 = isPlayer1;

            // 廣播擊球事件給所有客戶端
            broadcastMessage("HIT_BALL:" + player.playerNumber);

            // Simple push out the ball from the player to resolve overlap
            Rectangle2D playerBounds = player.getBounds();
            double ballCenterX_p = ball.x;
            double ballCenterY_p = ball.y;

            // Find the closest point to the circle center on the rectangle
            double closestX = Math.max(playerBounds.getMinX(), Math.min(ballCenterX_p, playerBounds.getMaxX()));
            double closestY = Math.max(playerBounds.getMinY(), Math.min(ballCenterY_p, playerBounds.getMaxY()));

            // Calculate the distance between the circle center and the closest point
            double distanceX = ballCenterX_p - closestX;
            double distanceY = ballCenterY_p - closestY;
            double distance = Math.sqrt((distanceX * distanceX) + (distanceY * distanceY));

            // Calculate overlap depth
            double overlap = ball.radius - distance;

            if (overlap > 0) {
                // Calculate collision normal
                double normalX = distanceX / distance;
                double normalY = distanceY / distance;

                // Push the ball out along the normal to resolve overlap
                ball.x += normalX * overlap;
                ball.y += normalY * overlap;
            }
        }

        // 處理球-網碰撞 (Server side)
        private void handleNetBallCollision(Ball ball, Rectangle2D netBounds) {
            // Determine which side the ball hit the net from
            boolean hitFromLeft = ball.x < netBounds.getMinX();
            boolean hitFromRight = ball.x > netBounds.getMaxX();
            boolean hitFromTop = ball.y < netBounds.getMinY();
            boolean hitFromBottom = ball.y > netBounds.getMaxY();

            // Find the closest point to the circle center on the rectangle
            double closestX = Math.max(netBounds.getMinX(), Math.min(ball.x, netBounds.getMaxX()));
            double closestY = Math.max(netBounds.getMinY(), Math.min(ball.y, netBounds.getMaxY()));

            // Calculate the distance between the circle center and the closest point
            double distanceX = ball.x - closestX;
            double distanceY = ball.y - closestY;
            double distance = Math.sqrt((distanceX * distanceX) + (distanceY * distanceY));

            // Calculate overlap depth
            double overlap = ball.radius - distance;

            if (overlap > 0) {
                // Overlap exists, a collision occurred

                // Calculate collision normal
                double normalX = distanceX / distance;
                double normalY = distanceY / distance;

                // Push the ball out along the normal to resolve overlap
                ball.x += normalX * overlap;
                ball.y += normalY * overlap;

                // Calculate velocity component along the normal
                double dotProduct = ball.velocityX * normalX + ball.velocityY * normalY;

                // Calculate velocity component along the tangent
                double tangentX = ball.velocityX - dotProduct * normalX;
                double tangentY = ball.velocityY - dotProduct * normalY;

                // Bounce velocity along the normal, considering bounce factor
                double newDotProduct = -dotProduct * BOUNCE_FACTOR; // Use constant BOUNCE_FACTOR

                // Update velocity
                ball.velocityX = newDotProduct * normalX + tangentX;
                ball.velocityY = newDotProduct * normalY + tangentY;

                // Consider net's special property: significant horizontal velocity decay
                if (hitFromLeft || hitFromRight) {
                    ball.velocityX *= 0.4; // More horizontal velocity decay
                } else if (hitFromTop || hitFromBottom) {
                    ball.velocityY *= 0.8; // Vertical velocity decay
                }

                // If velocity is very small, might be stuck on the net, give a slight push
                if (Math.abs(ball.velocityX) < 20 && Math.abs(ball.velocityY) < 20) {
                    if (hitFromLeft)
                        ball.velocityX = -50;
                    else
                        ball.velocityX = 50;
                    ball.velocityY = -50; // Push upwards
                }
            }
        }

        // 檢查計分條件 (Server side)
        private void checkScoring() {
            if (serverBall.y + serverBall.radius >= HEIGHT) { // Use constant HEIGHT
                int scoringPlayer = 0;
                if (serverBall.x < WIDTH / 2) { // Use constant WIDTH
                    scorePlayer2++;
                    scoringPlayer = 2;
                    System.out.println("房間 '" + roomName + "' - 玩家 2 得分! 比分: " + scorePlayer1 + " - " + scorePlayer2);
                } else {
                    scorePlayer1++;
                    scoringPlayer = 1;
                    System.out.println("房間 '" + roomName + "' - 玩家 1 得分! 比分: " + scorePlayer1 + " - " + scorePlayer2);
                }

                // 廣播得分事件
                broadcastMessage("SCORE:" + scoringPlayer);

                // 檢查遊戲是否結束
                checkGameOver();
            }
        }

        // 重置球的位置和速度 (Server side authoritative)
        private void resetBall() {
            // 重置到得分方一側發球 (示例)
            if (scorePlayer1 > scorePlayer2) {
                serverBall.x = WIDTH * 3 / 4; // Use constant WIDTH; 玩家 1 得分，玩家 2 發球
                serverBall.velocityX = -150; // 朝玩家 1 一側發球
            } else if (scorePlayer2 > scorePlayer1) {
                serverBall.x = WIDTH / 4; // Use constant WIDTH; 玩家 2 得分，玩家 1 發球
                serverBall.velocityX = 150; // 朝玩家 2 一側發球
            } else {
                // 遊戲開始或平局，玩家 1 發球
                serverBall.x = WIDTH / 4; // Use constant WIDTH
                serverBall.velocityX = 150;
            }

            serverBall.y = HEIGHT / 2; // Use constant HEIGHT; 發球高度
            serverBall.velocityY = -200; // 發球向上初速度
            serverBall.lastTouchedByPlayer1 = false; // 重置上次觸碰記錄
        }

        // 發送遊戲狀態給房間內所有客戶端 (Server side)
        private void sendGameState() {
            // STATE 訊息格式:
            // STATE:<inputSequence>:<ballX,ballY,ballVX,ballVY>:<p1X,p1Y,p1VX,p1VY,p1State>:<p2X,p2Y,p2VX,p2VY,p2State>:<score1,score2>
            // 伺服器包含最後處理的客戶端輸入序列號用於客戶端協調 (只針對 player2)

            String gameStateMsg = "STATE:" + lastProcessedClientInputSequenceServer + ":" +
                    server.decimalFormat.format(serverBall.x) + "," + server.decimalFormat.format(serverBall.y) + ","
                    + server.decimalFormat.format(serverBall.velocityX) + ","
                    + server.decimalFormat.format(serverBall.velocityY) + ":" +
                    server.decimalFormat.format(serverPlayer1.x) + "," + server.decimalFormat.format(serverPlayer1.y)
                    + "," + server.decimalFormat.format(serverPlayer1.velocityX) + ","
                    + server.decimalFormat.format(serverPlayer1.velocityY) + "," + serverPlayer1.currentState.getValue()
                    + ":" +
                    server.decimalFormat.format(serverPlayer2.x) + "," + server.decimalFormat.format(serverPlayer2.y)
                    + "," + server.decimalFormat.format(serverPlayer2.velocityX) + ","
                    + server.decimalFormat.format(serverPlayer2.velocityY) + "," + serverPlayer2.currentState.getValue()
                    + ":" +
                    scorePlayer1 + "," + scorePlayer2;

            broadcastMessage(gameStateMsg);
        }

        // 檢查遊戲是否結束
        public void checkGameOver() {
            if (scorePlayer1 >= MAX_SCORE || scorePlayer2 >= MAX_SCORE) {
                currentState = RoomState.GAME_OVER;
                broadcastMessage("GAME_OVER:" + (scorePlayer1 >= MAX_SCORE ? 1 : 2));
                System.out.println("房間 '" + roomName + "' 遊戲結束. 獲勝者: 玩家 " + (scorePlayer1 >= MAX_SCORE ? 1 : 2));
            }
        }
    }

    // 客戶端處理器類別 (Server side)
    private class ClientHandler implements Runnable {
        private Socket clientSocket;
        private MarioVolleyballServer server;
        private PrintWriter out;
        private BufferedReader in;
        private GameRoom currentRoom = null; // 客戶端當前所在的房間
        private int playerNumber = 0; // 客戶端在房間裡的玩家編號 (1 或 2)
        private String playerName; // 客戶端玩家名稱

        public ClientHandler(Socket socket, MarioVolleyballServer server) {
            this.clientSocket = socket;
            this.server = server;
        }

        public GameRoom getCurrentRoom() {
            return currentRoom;
        }

        public void setCurrentRoom(GameRoom room) {
            this.currentRoom = room;
        }

        public int getPlayerNumber() {
            return playerNumber;
        }

        public void setPlayerNumber(int playerNumber) {
            this.playerNumber = playerNumber;
        }

        public String getPlayerName() {
            return playerName;
        }

        public void setPlayerName(String playerName) {
            this.playerName = playerName;
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                String message;
                while ((message = in.readLine()) != null) {
                    server.handleMessage(this, message); // 將訊息傳遞給伺服器處理
                }

            } catch (IOException e) {
                System.out
                        .println("客戶端斷開連線: " + clientSocket.getInetAddress().getHostAddress() + " - " + e.getMessage());
            } finally {
                server.removeClient(this); // 從伺服器移除客戶端
                closeConnection();
            }
        }

        // 發送訊息給客戶端
        public void sendMessage(String message) {
            if (out != null) {
                out.println(message);
            }
        }

        // 關閉客戶端連線
        public void closeConnection() {
            try {
                if (in != null)
                    in.close();
                if (out != null)
                    out.close();
                if (clientSocket != null && !clientSocket.isClosed())
                    clientSocket.close();
            } catch (IOException e) {
                System.err.println("關閉客戶端連線錯誤: " + e.getMessage());
            }
        }
    }

    // Player class (Server side physics simulation)
    private static class Player {
        double x, y;
        double width, height;
        double velocityX, velocityY;
        boolean isJumping;
        int playerNumber;
        String playerName;
        // Animation related (not used for server physics, but kept for structure)
        PlayerState currentState = PlayerState.IDLE;
        double hitCooldownTimer = 0;

        public Player(double x, double y, double width, double height, Image spriteSheet, double frameWidth,
                double frameHeight, int playerNumber, String playerName) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.playerNumber = playerNumber;
            this.playerName = playerName;
            this.velocityX = 0;
            this.velocityY = 0;
            this.isJumping = false;
            // Image and animation details are not needed for server physics
        }

        // Update player state (movement, jump physics, cooldown timer) - Server
        // authoritative
        public void update(double deltaTime) {
            // Apply vertical velocity (affected by gravity)
            velocityY += GRAVITY * deltaTime; // Use constant GRAVITY
            y += velocityY * deltaTime;

            // Apply horizontal velocity
            x += velocityX * deltaTime;

            // Simple boundary checking
            if (x < 0) {
                x = 0;
            } else if (x + width > WIDTH) { // Use constant WIDTH
                x = WIDTH - width;
            }

            // Check for landing
            if (y + height > HEIGHT) { // Use constant HEIGHT
                y = HEIGHT - height;
                velocityY = 0;
                isJumping = false;
            }

            // Update hit cooldown timer
            if (hitCooldownTimer > 0) {
                hitCooldownTimer -= deltaTime;
            }

            // Update animation state based on physics (for sending to client)
            if (isJumping) {
                currentState = PlayerState.JUMPING;
            } else if (Math.abs(velocityX) > 10) {
                currentState = PlayerState.RUNNING;
            } else {
                currentState = PlayerState.IDLE;
            }
        }

        // Start jump (Server authoritative)
        public void jump() {
            if (!isJumping) {
                velocityY = PLAYER_JUMP_STRENGTH; // Use constant PLAYER_JUMP_STRENGTH
                isJumping = true;
                // Server doesn't play sound, client plays on receiving HIT_BALL or other event
            }
        }

        // Attempt to hit the ball (Server authoritative)
        public boolean tryHit() {
            if (hitCooldownTimer <= 0) {
                hitCooldownTimer = PLAYER_HIT_COOLDOWN; // Use constant PLAYER_HIT_COOLDOWN; Reset cooldown timer
                return true; // Can hit
            }
            return false; // Still on cooldown
        }

        // Get player's bounds rectangle (for collision detection)
        public Rectangle2D getBounds() {
            return new Rectangle2D(x, y, width, height);
        }
    }

    // Ball class (Server side physics simulation)
    private static class Ball {
        double x, y;
        double radius;
        double velocityX, velocityY;
        boolean lastTouchedByPlayer1 = false;

        // Animation related (not used for server physics)
        // Image spriteSheet;
        // int frameCount;
        // double frameSize;
        // int currentFrameIndex = 0;
        // double frameTime = 0;

        public Ball(double x, double y, double radius, Image spriteSheet, double frameSize, int frameCount) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.velocityX = 150; // Initial horizontal velocity (example)
            this.velocityY = -200; // Initial vertical velocity (example, upwards)
            // Image and animation details are not needed for server physics
        }

        // Update ball state (physics simulation, collision) - Server authoritative
        public void update(double deltaTime) {
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

            // TODO: Add net collision detection here in the future
        }

        // Get ball's bounds rectangle (for collision detection)
        public Rectangle2D getBounds() {
            return new Rectangle2D(x - radius, y - radius, radius * 2, radius * 2);
        }

        // Set ball state (for network synchronization) - Server authoritative
        public void setState(double x, double y, double velocityX, double velocityY) {
            this.x = x;
            this.y = y;
            this.velocityX = velocityX;
            this.velocityY = velocityY;
        }
    }

    // Player animation state enum (Server side also needs this for STATE message)
    private enum PlayerState {
        IDLE(0), RUNNING(1), JUMPING(2);

        private final int value;

        PlayerState(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
        // Server doesn't need fromValue, client does
    }
}
