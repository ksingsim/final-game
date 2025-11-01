import javax.swing.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Map;

public class PlayerHandler {

    // (ใช้ค่าจากไฟล์ที่คุณอัปโหลดมา)
    public static final int PLAYER_SIZE = 30;
    public static final int HITBOX_PADDING = 5;

    //---------------------Player-------------------------------------//
    public static class Player implements Serializable {
        private static final long serialVersionUID = 1L;
        public  int id ;
        public  int x , y ;
        public  int score ;
        public  boolean isTagger ;
        public  String name;
        public boolean isReady = false;

        Player(int id, int x, int y, boolean isTagger) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.score = 0;
            this.isTagger = isTagger;
            this.name = "P : " + id;
        }
        @Override
        public String toString() {
            return "Player" + id + " (" + x + "," + y + ")" + "Score : " + score + "Tag : " + isTagger;
        }
    }

    //-----------------------GameState-------------------//
    public static class GameState implements Serializable {
        private static final long serialVersionUID = 3L;
        public ArrayList<Player> player;
        public ArrayList<Obstacle> obstacles;
        public int remainingSeconds;
        public boolean gameover;
        public int winnerId;
        public boolean gameStarted;

        public GameState(ArrayList<Player> p, ArrayList<Obstacle> o, int time, boolean gameover, int winner, boolean gameStarted) {
            this.player = p;
            this.obstacles = o;
            this.remainingSeconds = time;
            this.gameover = gameover;
            this.winnerId = winner;
            this.gameStarted = gameStarted;
        }
    }

    //-------------------ClientHandler--------------------------//
    public static class ClientHandler implements Runnable {
        private final Socket socket;
        private final ObjectInputStream in;
        private final ObjectOutputStream out;
        private final int playerId;
        private final Map<Integer, Player> players;
        private final Map<Integer, ClientHandler> handlers;
        private final java.util.List<Obstacle> obstacles;

        public ClientHandler(Socket socket, int playerId, Map<Integer, Player> players, Map<Integer, ClientHandler> handlers, java.util.List<Obstacle> obstacles) throws IOException {
            this.socket = socket;
            this.playerId = playerId;
            this.players = players;
            this.handlers = handlers;
            this.obstacles = obstacles;
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
            out.writeInt(playerId);
            out.flush();
        }

        @Override
        public void run() {
            try {
                Player player = players.get(playerId);
                Server.broadcastPlayer();

                while (true) {
                    String cmd = (String) in.readObject();
                    if(cmd.equals("READY")){
                        player.isReady = true;
                        System.out.println("Player " + playerId + " is READY.");
                        Server.checkAllReady();
                    }

                    if (Server.isGameStarted()) {
                        Server.movePlayer(playerId, cmd);
                        checkTag(playerId, player);
                        checkPlayerCollisions(player); // <--- เมธอดนี้ถูกแก้ไขแล้ว
                    }
                    Server.broadcastPlayer();
                }
            } catch (Exception e) {
                System.out.println("Player " + playerId + " disconnected: " + e.getMessage());
            } finally {
                players.remove(playerId);
                handlers.remove(playerId);
                Server.broadcastPlayer();
                try { socket.close(); } catch (IOException ignored) {}
            }
        }


        private void checkTag(int playerId, Player player) {
            if (player.isTagger) {
                synchronized (players) {
                    for(Player other : players.values()){
                        if(other.id != player.id && !other.isTagger){

                            boolean hit = (player.x + HITBOX_PADDING < other.x + PLAYER_SIZE - HITBOX_PADDING &&
                                    player.x + PLAYER_SIZE - HITBOX_PADDING > other.x + HITBOX_PADDING &&
                                    player.y + HITBOX_PADDING < other.y + PLAYER_SIZE - HITBOX_PADDING &&
                                    player.y + PLAYER_SIZE - HITBOX_PADDING > other.y + HITBOX_PADDING);

                            if(hit) {
                                player.score++;
                                player.isTagger = false;
                                other.isTagger = true;
                                System.out.println("Player : " + player.id + " tagged player : " + other.id);
                                break;
                            }
                        }
                    }
                }
            }
        }


        // --- ★★★ 3. แก้ไขเมธอด checkPlayerCollisions ★★★ ---
        private void checkPlayerCollisions(Player movingPlayer) {
            synchronized (players) {
                for (Player other : players.values()) {
                    if (other.id == movingPlayer.id) {
                        continue;
                    }

                    // (ตรรกะการตรวจจับการชน 'hit' เหมือนเดิม)
                    boolean hit = (movingPlayer.x + HITBOX_PADDING < other.x + PLAYER_SIZE - HITBOX_PADDING &&
                            movingPlayer.x + PLAYER_SIZE - HITBOX_PADDING > other.x + HITBOX_PADDING &&
                            movingPlayer.y + HITBOX_PADDING < other.y + PLAYER_SIZE - HITBOX_PADDING &&
                            movingPlayer.y + PLAYER_SIZE - HITBOX_PADDING > other.y + HITBOX_PADDING);


                    if (hit) {
                        // (คำนวณ Overlap เหมือนเดิม)
                        double overlapX = Math.min(movingPlayer.x + PLAYER_SIZE - HITBOX_PADDING, other.x + PLAYER_SIZE - HITBOX_PADDING)
                                - Math.max(movingPlayer.x + HITBOX_PADDING, other.x + HITBOX_PADDING);
                        double overlapY = Math.min(movingPlayer.y + PLAYER_SIZE - HITBOX_PADDING, other.y + PLAYER_SIZE - HITBOX_PADDING)
                                - Math.max(movingPlayer.y + HITBOX_PADDING, other.y + HITBOX_PADDING);

                        // --- (เริ่มส่วนที่แก้ไข) ---

                        // คำนวณพิกัด "เป้าหมาย" (Target) ที่ต้องการผลักไป
                        int movingTargetX = movingPlayer.x;
                        int movingTargetY = movingPlayer.y;
                        int otherTargetX = other.x;
                        int otherTargetY = other.y;

                        if (overlapX < overlapY) {
                            // ผลักในแนวนอน (แกน X)
                            double push = Math.ceil(overlapX / 2.0);
                            if (movingPlayer.x < other.x) {
                                movingTargetX -= push;
                                otherTargetX += push;
                            } else {
                                movingTargetX += push;
                                otherTargetX -= push;
                            }
                        } else {
                            // ผลักในแนวตั้ง (แกน Y)
                            double push = Math.ceil(overlapY / 2.0);
                            if (movingPlayer.y < other.y) {
                                movingTargetY -= push;
                                otherTargetY += push;
                            } else {
                                movingTargetY += push;
                                otherTargetY -= push;
                            }
                        }

                        // ★★★ แทนที่จะแก้ไข .x .y โดยตรง ★★★
                        // ให้เรียกเมธอด `trySetPlayerPosition` จาก Server
                        // ซึ่งเมธอดนี้จะตรวจสอบการชนกับสิ่งกีดขวางให้เราเอง
                        Server.trySetPlayerPosition(movingPlayer, movingTargetX, movingTargetY);
                        Server.trySetPlayerPosition(other, otherTargetX, otherTargetY);

                        // --- (สิ้นสุดส่วนที่แก้ไข) ---
                    }
                }
            }
        }
        // --- สิ้นสุดการแก้ไข ---


        private Player getTagger() {
            for(Player player : players.values()){
                if(player.isTagger) return player;
            }
            return null;
        }

        public void sendGameState(GameState gs) {
            try {
                if (out != null) {
                    out.reset();
                    out.writeObject(gs);
                    out.flush();
                }
            }
            catch (IOException e) {
                System.out.println("Player"+playerId+" Error"+e.getMessage());
            }
        }
    }
}