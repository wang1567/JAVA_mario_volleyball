package com.example;

import java.io.*;
import java.net.*;
import java.util.*;

public class GameServer {
    private static final int PORT = 12345;
    private static Map<String, Room> rooms = new HashMap<>();
    private static Map<Socket, String> clientRooms = new HashMap<>();

    public static void main(String[] args) {
        System.out.println("伺服器啟動，等待玩家連接...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                new ClientHandler(serverSocket.accept()).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class Room {
        String name;
        Socket host;
        Socket guest;
        PrintWriter hostWriter;
        PrintWriter guestWriter;
        boolean ballDirection; // true 表示向右，false 表示向左

        public Room(String name, Socket host, PrintWriter hostWriter) {
            this.name = name;
            this.host = host;
            this.hostWriter = hostWriter;
            this.ballDirection = Math.random() > 0.5; // 初始化發球方向
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                String message;
                while ((message = in.readLine()) != null) {
                    System.out.println("收到消息: " + message);
                    handleMessage(message);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                cleanup();
            }
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
                    if (room != null) {
                        // 發送統一的發球方向給兩個玩家
                        String direction = room.ballDirection ? "RIGHT" : "LEFT";
                        room.hostWriter.println("BALL_DIRECTION:" + direction);
                        room.guestWriter.println("BALL_DIRECTION:" + direction);
                        // 更新下一次的發球方向
                        room.ballDirection = !room.ballDirection;
                    }
                }
            } else {
                // 遊戲動作消息
                String roomName = clientRooms.get(socket);
                if (roomName != null) {
                    Room room = rooms.get(roomName);
                    if (room != null) {
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
                room.guestWriter.println("GAME_START:" + roomName);
            }
        }

        private void cleanup() {
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
