package com.example;

import java.io.*;
import java.net.*;
import java.util.*;

public class GameServer {
    private static final int PORT = 12345;
    private static Map<String, Room> rooms = new HashMap<>();
    private static Map<Socket, String> clientRooms = new HashMap<>();
    private static final int MAX_ROOMS = 100;
    private static final int PING_INTERVAL = 5000; // 5秒發送一次心跳包
    private static String serverIP;

    public static void main(String[] args) {
        try {
            // 獲取伺服器的公網 IP 地址
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
            // 嘗試獲取公網 IP
            URL url = new URL("http://checkip.amazonaws.com");
            BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
            String ip = reader.readLine().trim();
            reader.close();
            return ip;
        } catch (Exception e) {
            try {
                // 如果無法獲取公網 IP，則使用本地 IP
                InetAddress localHost = InetAddress.getLocalHost();
                return localHost.getHostAddress();
            } catch (Exception ex) {
                return "localhost";
            }
        }
    }

    private static class Room {
        String name;
        Socket host;
        Socket guest;
        PrintWriter hostWriter;
        PrintWriter guestWriter;
        boolean ballDirection;
        long lastHostPing;
        long lastGuestPing;
        boolean gameStarted;

        public Room(String name, Socket host, PrintWriter hostWriter) {
            this.name = name;
            this.host = host;
            this.hostWriter = hostWriter;
            this.ballDirection = Math.random() > 0.5;
            this.lastHostPing = System.currentTimeMillis();
            this.lastGuestPing = System.currentTimeMillis();
            this.gameStarted = false;
        }

        public void checkConnection() {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastHostPing > PING_INTERVAL * 2) {
                if (guest != null) {
                    guestWriter.println("OPPONENT_DISCONNECTED");
                }
                cleanup();
            }
            if (guest != null && currentTime - lastGuestPing > PING_INTERVAL * 2) {
                hostWriter.println("OPPONENT_DISCONNECTED");
                cleanup();
            }
        }

        private void cleanup() {
            rooms.remove(name);
            clientRooms.remove(host);
            if (guest != null) {
                clientRooms.remove(guest);
            }
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private boolean isAlive = true;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // 啟動心跳包檢查
                startPingCheck();

                String message;
                while (isAlive && (message = in.readLine()) != null) {
                    System.out.println("收到消息: " + message);
                    handleMessage(message);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                cleanup();
            }
        }

        private void startPingCheck() {
            new Thread(() -> {
                while (isAlive) {
                    try {
                        Thread.sleep(PING_INTERVAL);
                        String roomName = clientRooms.get(socket);
                        if (roomName != null) {
                            Room room = rooms.get(roomName);
                            if (room != null) {
                                if (socket == room.host) {
                                    room.lastHostPing = System.currentTimeMillis();
                                } else {
                                    room.lastGuestPing = System.currentTimeMillis();
                                }
                                room.checkConnection();
                            }
                        }
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }).start();
        }

        private void handleMessage(String message) {
            if (message.startsWith("CREATE_ROOM:")) {
                String roomName = message.substring(12);
                createRoom(roomName);
            } else if (message.startsWith("JOIN_ROOM:")) {
                String roomName = message.substring(10);
                joinRoom(roomName);
            } else if (message.startsWith("RESET_BALL:")) {
                String roomName = clientRooms.get(socket);
                if (roomName != null) {
                    Room room = rooms.get(roomName);
                    if (room != null && room.gameStarted) {
                        String direction = room.ballDirection ? "RIGHT" : "LEFT";
                        room.hostWriter.println("BALL_DIRECTION:" + direction);
                        room.guestWriter.println("BALL_DIRECTION:" + direction);
                        room.ballDirection = !room.ballDirection;
                    }
                }
            } else if (message.startsWith("GAME_READY:")) {
                String roomName = clientRooms.get(socket);
                if (roomName != null) {
                    Room room = rooms.get(roomName);
                    if (room != null) {
                        if (socket == room.host) {
                            room.hostWriter.println("WAITING_FOR_OPPONENT");
                        } else {
                            room.gameStarted = true;
                            room.hostWriter.println("GAME_START");
                            room.guestWriter.println("GAME_START");
                        }
                    }
                }
            } else {
                // 遊戲動作消息
                String roomName = clientRooms.get(socket);
                if (roomName != null) {
                    Room room = rooms.get(roomName);
                    if (room != null && room.gameStarted) {
                        if (socket == room.host) {
                            room.guestWriter.println(message);
                        } else {
                            room.hostWriter.println(message);
                        }
                    }
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
                out.println("ROOM_CREATED:" + roomName);
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
                room.guest = socket;
                room.guestWriter = out;
                clientRooms.put(socket, roomName);
                out.println("ROOM_JOINED:" + roomName);
                room.hostWriter.println("OPPONENT_JOINED:" + roomName);
            }
        }

        private void cleanup() {
            isAlive = false;
            try {
                String roomName = clientRooms.get(socket);
                if (roomName != null) {
                    Room room = rooms.get(roomName);
                    if (room != null) {
                        if (socket == room.host) {
                            if (room.guest != null) {
                                room.guestWriter.println("OPPONENT_DISCONNECTED");
                            }
                        } else {
                            room.hostWriter.println("OPPONENT_DISCONNECTED");
                        }
                        rooms.remove(roomName);
                    }
                    clientRooms.remove(socket);
                }
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
