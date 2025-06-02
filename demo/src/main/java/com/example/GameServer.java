package com.example;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class GameServer {
    private static final int PORT = 12345;
    private static Map<String, Room> rooms = new ConcurrentHashMap<>();
    private static Map<Socket, String> clientRooms = new ConcurrentHashMap<>();
    private static final int MAX_ROOMS = 100;
    private static final int PING_INTERVAL = 5000; // 5秒發送一次心跳包
    private static String serverIP;

    // 遊戲邏輯更新間隔 (毫秒)，約 60 幀
    public static final long GAME_TICK_INTERVAL = 16;

    public static void main(String[] args) {
        try {
            serverIP = getPublicIP();
            System.out.println("伺服器啟動，等待玩家連接...");
            System.out.println("伺服器 IP 地址: " + serverIP);
            System.out.println("伺服器端口: " + PORT);

            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                while (true) {
                    new ClientHandler(serverSocket.accept()).start();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String getPublicIP() {
        try {
            URL url = new URL("http://checkip.amazonaws.com");
            BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
            String ip = reader.readLine().trim();
            reader.close();
            return ip;
        } catch (Exception e) {
            try {
                InetAddress localHost = InetAddress.getLocalHost();
                return localHost.getHostAddress();
            } catch (Exception ex) {
                return "localhost";
            }
        }
    }

    private static class Room extends Thread {
        String name;
        Socket host;
        Socket guest;
        PrintWriter hostWriter;
        PrintWriter guestWriter;
        long lastHostPing;
        long lastGuestPing;
        boolean gameStarted;
        boolean roomActive = true; // 標記房間是否活躍

        // 新增準備狀態變數
        boolean hostReady = false;
        boolean guestReady = false;

        // 遊戲狀態變數
        double ballX, ballY;
        double ballSpeedX, ballSpeedY;
        double player1X, player1Y, player1VelocityY;
        double player2X, player2Y, player2VelocityY;
        boolean player1IsJumping, player2IsJumping;
        int player1Score, player2Score;
        boolean isBallReset;
        boolean isGameOver;
        boolean ballDirection; // 初始發球方向

        private static final double GAME_WIDTH = 800;
        private static final double GAME_HEIGHT = 600;
        private static final double CHARACTER_WIDTH = 64; // 角色寬度
        private static final double CHARACTER_HEIGHT = 64; // 角色高度
        private static final double BALL_SIZE = 60; // 排球大小
        private static final double GRAVITY = 0.7;
        private static final double JUMP_POWER = -12;
        private static final double MOVE_SPEED = 8; // 調整移動速度
        private static final double NET_X = 400;
        private static final double NET_WIDTH = 4;
        private static final double NET_TOP_Y = 400;
        private static final double GROUND_Y = GAME_HEIGHT - CHARACTER_HEIGHT; // 地面 Y 座標 (以角色底部為準)
        private static final double BALL_GROUND_Y = GAME_HEIGHT - BALL_SIZE; // 地面 Y 座標 (以球底部為準)

        private static final int WINNING_SCORE = 11;

        public Room(String name, Socket host, PrintWriter hostWriter) {
            this.name = name;
            this.host = host;
            this.hostWriter = hostWriter;
            this.ballDirection = Math.random() > 0.5; // 初始化發球方向
            this.lastHostPing = System.currentTimeMillis();
            this.lastGuestPing = System.currentTimeMillis();
            this.gameStarted = false;

            // 初始化遊戲狀態
            resetGameState();
        }

        private void resetGameState() {
            ballX = GAME_WIDTH / 2 - BALL_SIZE / 2;
            ballY = 100;
            ballSpeedX = 0;
            ballSpeedY = 0;

            player1X = 50;
            player1Y = GROUND_Y;
            player1VelocityY = 0;
            player1IsJumping = false;

            player2X = GAME_WIDTH - 50 - CHARACTER_WIDTH;
            player2Y = GROUND_Y;
            player2VelocityY = 0;
            player2IsJumping = false;

            player1Score = 0;
            player2Score = 0;
            isBallReset = true; // 標記球需要重置發球
            isGameOver = false;
        }

        public void setGuest(Socket guest, PrintWriter guestWriter) {
            this.guest = guest;
            this.guestWriter = guestWriter;
            this.lastGuestPing = System.currentTimeMillis();
            System.out.println("玩家 " + guest.getInetAddress() + " 加入房間 [" + name + "]");
            broadcastMessage("OPPONENT_JOINED"); // 通知主機對手已加入
        }

        @Override
        public void run() {
            long lastTickTime = System.currentTimeMillis();
            while (roomActive) {
                long currentTime = System.currentTimeMillis();
                long elapsed = currentTime - lastTickTime;

                if (gameStarted && !isGameOver && elapsed >= GAME_TICK_INTERVAL) {
                    // 更新遊戲狀態
                    updateGameState();

                    // 廣播遊戲狀態給客戶端
                    broadcastGameState();

                    lastTickTime = currentTime;
                }

                // 心跳檢查
                checkConnection();

                try {
                    long sleepTime = GAME_TICK_INTERVAL - (System.currentTimeMillis() - lastTickTime);
                    if (sleepTime > 0) {
                        Thread.sleep(sleepTime);
                    }
                } catch (InterruptedException e) {
                    // 執行緒被中斷，通常是房間清理時發生
                    System.out.println("房間 [" + name + "] 遊戲邏輯執行緒中斷。");
                    break; // 退出循環
                }
            }
            System.out.println("房間 [" + name + "] 遊戲邏輯執行緒結束。");
        }

        private void updateGameState() {
            // 更新玩家垂直位置和速度 (應用重力)
            player1Y += player1VelocityY;
            player1VelocityY += GRAVITY;
            player2Y += player2VelocityY;
            player2VelocityY += GRAVITY;

            // 檢查玩家是否落地
            if (player1Y >= GROUND_Y) {
                player1Y = GROUND_Y;
                player1VelocityY = 0;
                player1IsJumping = false;
            }
            if (player2Y >= GROUND_Y) {
                player2Y = GROUND_Y;
                player2VelocityY = 0;
                player2IsJumping = false;
            }

            // 更新球的位置
            if (!isBallReset) {
                ballX += ballSpeedX;
                ballY += ballSpeedY;
                ballSpeedY += GRAVITY * 0.5; // 球受到的重力可以稍微小一點

                // 檢查球是否落地
                if (ballY >= BALL_GROUND_Y) {
                    if (!isBallReset) {
                        isBallReset = true;
                        ballY = BALL_GROUND_Y; // 防止穿透地面
                        // 判斷球落在哪一邊
                        if (ballX + BALL_SIZE / 2 < NET_X) { // 球落在左半場
                            player2Score++;
                        } else { // 球落在右半場
                            player1Score++;
                        }
                        // 發送分數更新
                        broadcastMessage("SCORE:" + player1Score + ":" + player2Score);
                        checkGameOver(); // 檢查是否遊戲結束
                        resetBall(); // 重置球
                    }
                }

                // 球碰到左右邊界
                if (ballX <= 0 || ballX >= GAME_WIDTH - BALL_SIZE) {
                    ballSpeedX *= -1;
                    // 防止球卡在邊界
                    if (ballX <= 0)
                        ballX = 0;
                    if (ballX >= GAME_WIDTH - BALL_SIZE)
                        ballX = GAME_WIDTH - BALL_SIZE;
                }

                // 球碰到網子 (簡化為矩形碰撞)
                if (ballX + BALL_SIZE > NET_X && ballX < NET_X + NET_WIDTH &&
                        ballY + BALL_SIZE > NET_TOP_Y && ballY < GAME_HEIGHT) { // 網子從 NET_TOP_Y 到地面
                    // 球在網子左側碰到
                    if (ballX + BALL_SIZE / 2 < NET_X + NET_WIDTH / 2 && ballSpeedX > 0) {
                        ballSpeedX *= -1.1;
                        ballX = NET_X - BALL_SIZE - 1; // 稍微彈開一點
                    }
                    // 球在網子右側碰到
                    else if (ballX + BALL_SIZE / 2 > NET_X + NET_WIDTH / 2 && ballSpeedX < 0) {
                        ballSpeedX *= -1.1;
                        ballX = NET_X + NET_WIDTH + 1; // 稍微彈開一點
                    }
                    // 球在網子頂端碰到
                    if (ballY + BALL_SIZE > NET_TOP_Y && ballY < NET_TOP_Y + 20 && ballSpeedY > 0) { // 簡化碰撞頂端
                        ballSpeedY = -Math.abs(ballSpeedY) * 1.2;
                        ballX *= 1.05; // 碰到網頂會稍微加速水平移動
                    } else if (ballY < NET_TOP_Y && ballY + BALL_SIZE > NET_TOP_Y && ballSpeedY < 0) { // 從下方碰到網頂
                        ballSpeedY = Math.abs(ballSpeedY) * 0.8; // 減速彈回
                        ballX *= 1.05;
                    }
                }

                // 球碰到玩家 (矩形碰撞檢測)
                // Player 1
                if (ballX < player1X + CHARACTER_WIDTH && ballX + BALL_SIZE > player1X &&
                        ballY < player1Y + CHARACTER_HEIGHT && ballY + BALL_SIZE > player1Y) {
                    // 判斷是從上方還是側面碰撞
                    if (ballY + BALL_SIZE / 2 < player1Y + CHARACTER_HEIGHT / 2) { // 上方碰撞
                        ballSpeedY = JUMP_POWER * 0.8; // 向上反彈
                        // 根據碰撞點水平位置給予水平速度
                        ballSpeedX = (ballX + BALL_SIZE / 2 - (player1X + CHARACTER_WIDTH / 2)) * 0.3;
                        if (Math.abs(ballSpeedX) < 3)
                            ballSpeedX = ballSpeedX > 0 ? 3 : -3; // 確保一定的水平速度
                    } else { // 側面碰撞
                        ballSpeedX *= -1.1;
                        ballSpeedY *= 0.9;
                        // 防止卡住
                        if (ballX + BALL_SIZE / 2 < player1X + CHARACTER_WIDTH / 2)
                            ballX = player1X - BALL_SIZE - 1;
                        else
                            ballX = player1X + CHARACTER_WIDTH + 1;
                    }
                }

                // Player 2
                if (ballX < player2X + CHARACTER_WIDTH && ballX + BALL_SIZE > player2X &&
                        ballY < player2Y + CHARACTER_HEIGHT && ballY + BALL_SIZE > player2Y) {
                    // 判斷是從上方還是側面碰撞
                    if (ballY + BALL_SIZE / 2 < player2Y + CHARACTER_HEIGHT / 2) { // 上方碰撞
                        ballSpeedY = JUMP_POWER * 0.8; // 向上反彈
                        // 根據碰撞點水平位置給予水平速度
                        ballSpeedX = (ballX + BALL_SIZE / 2 - (player2X + CHARACTER_WIDTH / 2)) * 0.3;
                        if (Math.abs(ballSpeedX) < 3)
                            ballSpeedX = ballSpeedX > 0 ? 3 : -3; // 確保一定的水平速度
                    } else { // 側面碰撞
                        ballSpeedX *= -1.1;
                        ballSpeedY *= 0.9;
                        // 防止卡住
                        if (ballX + BALL_SIZE / 2 < player2X + CHARACTER_WIDTH / 2)
                            ballX = player2X - BALL_SIZE - 1;
                        else
                            ballX = player2X + CHARACTER_WIDTH + 1;
                    }
                }
            }
        }

        private void resetBall() {
            ballX = GAME_WIDTH / 2 - BALL_SIZE / 2;
            ballY = 100;
            ballSpeedX = 0;
            ballSpeedY = 0;
            isBallReset = true;

            // 延遲一秒後發球
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    if (!roomActive)
                        return; // 房間已關閉則不再發球
                    ballDirection = !ballDirection; // 切換發球方向
                    ballSpeedX = 4 * (ballDirection ? 1 : -1); // 初始發球速度
                    ballSpeedY = -8; // 初始發球向上速度
                    isBallReset = false;
                    // 通知客戶端球已重置並開始運動 (可選，客戶端根據狀態更新即可)
                }
            }, 1000);
        }

        private void checkGameOver() {
            if (player1Score >= WINNING_SCORE || player2Score >= WINNING_SCORE) {
                isGameOver = true;
                String winner = player1Score >= WINNING_SCORE ? "左側玩家" : "右側玩家";
                broadcastMessage("GAME_OVER:" + player1Score + ":" + player2Score + ":" + winner);
                roomActive = false; // 停止遊戲邏輯循環
            }
        }

        public void processPlayerAction(Socket clientSocket, String action) {
            if (!gameStarted || isGameOver)
                return;

            // 處理玩家移動和跳躍指令
            if (clientSocket == host) { // 主機 (玩家1)
                if (action.equals("LEFT")) {
                    player1X = Math.max(0, player1X - MOVE_SPEED);
                } else if (action.equals("RIGHT")) {
                    player1X = Math.min(GAME_WIDTH / 2 - CHARACTER_WIDTH, player1X + MOVE_SPEED);
                } else if (action.equals("JUMP")) {
                    if (!player1IsJumping) {
                        player1VelocityY = JUMP_POWER;
                        player1IsJumping = true;
                    }
                }
            } else if (clientSocket == guest) { // 客機 (玩家2)
                if (action.equals("LEFT")) {
                    player2X = Math.max(GAME_WIDTH / 2, player2X - MOVE_SPEED);
                } else if (action.equals("RIGHT")) {
                    player2X = Math.min(GAME_WIDTH - CHARACTER_WIDTH, player2X + MOVE_SPEED);
                } else if (action.equals("JUMP")) {
                    if (!player2IsJumping) {
                        player2VelocityY = JUMP_POWER;
                        player2IsJumping = true;
                    }
                }
            }
        }

        private void broadcastGameState() {
            // 廣播遊戲狀態給所有玩家 (簡化狀態字串)
            String gameState = String.format(
                    "STATE:%.1f:%.1f:%.2f:%.2f:%.1f:%.1f:%.2f:%b:%.1f:%.1f:%.2f:%b:%d:%d:%b:%b",
                    ballX, ballY, ballSpeedX, ballSpeedY,
                    player1X, player1Y, player1VelocityY, player1IsJumping,
                    player2X, player2Y, player2VelocityY, player2IsJumping,
                    player1Score, player2Score, isBallReset, isGameOver);
            broadcastMessage(gameState);
        }

        private void broadcastMessage(String message) {
            if (hostWriter != null) {
                hostWriter.println(message);
            }
            if (guestWriter != null) {
                guestWriter.println(message);
            }
        }

        public void checkConnection() {
            long currentTime = System.currentTimeMillis();
            boolean hostDisconnected = currentTime - lastHostPing > PING_INTERVAL * 3; // 增加斷線判定時間
            boolean guestDisconnected = guest != null && currentTime - lastGuestPing > PING_INTERVAL * 3;

            if (hostDisconnected || guestDisconnected) {
                System.out.println("房間 [" + name + "] 檢測到玩家斷線，進行清理。");
                if (guestWriter != null && hostDisconnected) { // 主機斷線通知客機
                    guestWriter.println("OPPONENT_DISCONNECTED");
                }
                if (hostWriter != null && guestDisconnected) { // 客機斷線通知主機
                    hostWriter.println("OPPONENT_DISCONNECTED");
                }
                cleanup();
            }
        }

        private void cleanup() {
            if (!roomActive)
                return; // 防止重複清理
            roomActive = false;

            System.out.println("房間 [" + name + "] 正在清理...");
            synchronized (rooms) {
                rooms.remove(name);
            }
            if (host != null) {
                clientRooms.remove(host);
                try {
                    host.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("房間 [" + name + "] Host Socket 已關閉。");
            }
            if (guest != null) {
                clientRooms.remove(guest);
                try {
                    guest.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("房間 [" + name + "] Guest Socket 已關閉。");
            }
            // 中斷遊戲邏輯執行緒 (如果在運行)
            interrupt();
        }

        // 接收客戶端心跳
        public void receivePing(Socket clientSocket) {
            long currentTime = System.currentTimeMillis();
            if (clientSocket == host) {
                lastHostPing = currentTime;
            } else if (clientSocket == guest) {
                lastGuestPing = currentTime;
            }
        }

        public void processPlayerReady(Socket clientSocket) {
            if (clientSocket == host) {
                hostReady = true;
                System.out.println("房間 [" + name + "] Host 已準備。");
                broadcastMessage("HOST_READY"); // 通知對手主機已準備
            } else if (clientSocket == guest) {
                guestReady = true;
                System.out.println("房間 [" + name + "] Guest 已準備。");
                broadcastMessage("GUEST_READY"); // 通知對手客機已準備
            }

            // 檢查是否所有玩家都已準備
            if (hostReady && guestReady && !gameStarted) {
                gameStarted = true;
                System.out.println("房間 [" + name + "] 雙方已準備，遊戲開始！");
                // 初始化遊戲狀態，並通知客戶端遊戲開始
                resetGameState();
                broadcastMessage("GAME_START");
                start(); // 啟動遊戲邏輯執行緒
            }
        }

        public void resetGame() {
            resetGameState();
            broadcastMessage("RESET_GAME");
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private boolean isAlive = true;
        private Room playerRoom = null; // 儲存玩家所在的房間
        private String roomName = null; // 儲存玩家所在的房間名稱

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // 啟動心跳包發送 (客戶端也需要發送心跳)
                startPingSender();

                String message;
                while (isAlive && (message = in.readLine()) != null) {
                    System.out.println("收到消息 [" + socket.getInetAddress() + "]: " + message);
                    handleMessage(message);
                }
            } catch (IOException e) {
                // 連線斷開
                System.out.println("玩家 [" + socket.getInetAddress() + "] 連線斷開。");
                // e.printStackTrace(); // 連線斷開是預期行為，不需要每次都列印堆疊追蹤
            } finally {
                cleanup();
            }
        }

        private void startPingSender() {
            new Thread(() -> {
                while (isAlive && socket != null && !socket.isClosed()) {
                    try {
                        // 向客戶端發送心跳包 (可選，主要依賴客戶端發送心跳給伺服器)
                        // out.println("PING");
                        Thread.sleep(PING_INTERVAL);
                    } catch (InterruptedException | NullPointerException e) {
                        break;
                    }
                }
            }).start();
        }

        private void handleMessage(String message) {
            if (message.startsWith("CREATE_ROOM:")) {
                roomName = message.substring(12);
                createRoom(roomName);
            } else if (message.startsWith("JOIN_ROOM:")) {
                roomName = message.substring(10);
                joinRoom(roomName);
            } else if (message.equals("PING")) {
                // 收到客戶端的心跳包，傳遞給房間處理
                if (playerRoom != null) {
                    playerRoom.receivePing(socket);
                }
            } else if (message.equals("READY")) {
                if (playerRoom != null) {
                    playerRoom.processPlayerReady(socket);
                }
            } else if (message.startsWith("ACTION:")) {
                // 處理客戶端發送的遊戲動作指令
                String action = message.substring(7);
                if (playerRoom != null) {
                    playerRoom.processPlayerAction(socket, action);
                }
            } else if (message.equals("REMATCH_REQUEST")) {
                if (playerRoom != null) {
                    playerRoom.broadcastMessage("REMATCH_REQUEST");
                }
            } else if (message.equals("REMATCH_ACCEPT")) {
                // 處理重新開始同意 (目前由伺服器主導重置)
                // 可以在這裡添加邏輯確認雙方都同意
                if (playerRoom != null) {
                    playerRoom.resetGame(); // 伺服器直接重置遊戲
                    playerRoom.broadcastMessage("RESET_GAME"); // 通知客戶端重置
                }
            } else if (message.equals("READY")) {
                if (playerRoom != null) {
                    playerRoom.processPlayerReady(socket);
                }
            }
        }

        private void createRoom(String roomName) {
            synchronized (rooms) {
                if (rooms.size() >= MAX_ROOMS) {
                    out.println("ERROR:伺服器已滿");
                    return;
                }
                if (rooms.containsKey(roomName)) {
                    out.println("ERROR:房間已存在");
                    return;
                }
                Room room = new Room(roomName, socket, out);
                rooms.put(roomName, room);
                clientRooms.put(socket, roomName);
                playerRoom = room; // 記錄玩家所在的房間
                out.println("ROOM_CREATED:" + roomName);
                System.out.println("房間 [" + roomName + "] 已創建 by " + socket.getInetAddress());
            }
        }

        private void joinRoom(String roomName) {
            synchronized (rooms) {
                Room room = rooms.get(roomName);
                if (room == null) {
                    out.println("ERROR:房間不存在");
                    return;
                }
                if (room.guest != null) {
                    out.println("ERROR:房間已滿");
                    return;
                }
                room.setGuest(socket, out);
                clientRooms.put(socket, roomName);
                playerRoom = room; // 記錄玩家所在的房間
                out.println("ROOM_JOINED:" + roomName);
                System.out.println("玩家 " + socket.getInetAddress() + " 加入房間 [" + roomName + "]");
                // 遊戲開始的訊息已在 Room.setGuest 中發送
            }
        }

        private void cleanup() {
            isAlive = false;
            try {
                // 由房間來處理自身的清理和通知對手
                if (playerRoom != null) {
                    playerRoom.cleanup(); // 讓 Room 物件自行清理
                }
                clientRooms.remove(socket);
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("ClientHandler for [" + (roomName != null ? roomName : "N/A") + "] 結束。");
        }
    }
}
