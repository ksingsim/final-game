import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

/**
 * คลาส Server
 */
public class Server {

    public static int port = 8080;

    public static final int MAP_WIDTH = 1000;
    public static final int MAP_HEIGHT = 600;

    private static final Map<Integer, PlayerHandler.Player> players = Collections.synchronizedMap(new HashMap<>());
    private static final Map<Integer, PlayerHandler.ClientHandler> handlers = Collections.synchronizedMap(new HashMap<>());

    private static final List<Obstacle> obstacles = Collections.synchronizedList(new ArrayList<>());
    private static final int NUM_OBSTACLES = 15;
    private static final int MIN_OBSTACLE_DISTANCE = 25;

    private static final int NUM_OBSTACLE_TYPES = 3;

    private static final int[][] OBSTACLE_TYPE_SIZES = {
            {125, 125}, // type 0 (หิน)
            {100, 100}, // type 1 (กล่องไม้)
            {60, 60}  // type 2 (พุ่มไม้)
    };

    private static final int PLAYER_SPEED = 5;
    private static final int GAME_DURATION_SECONDS = 60;

    // (ใช้ PlayerHandler.PLAYER_SIZE ที่ถูกต้อง)
    private static final int PADDING = PlayerHandler.PLAYER_SIZE + 5;

    private static volatile boolean gameStarted = false;
    private static volatile boolean gameOver = false;
    private static volatile int remainingSeconds = GAME_DURATION_SECONDS;
    private static int winner = -1;
    private static int nextPlayerId = 0;

    public Server(int port) {
        Server.port = port;
    }

    public void start() {
        System.out.println("Server starting on port: " + port);
        spawnObstacles();
        new Thread(new GameTimer()).start();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected");
                int playerId = nextPlayerId++;

                PlayerHandler.Player player = new PlayerHandler.Player(playerId, PADDING + (playerId * 40), PADDING + (playerId * 40), false);
                players.put(playerId, player);

                PlayerHandler.ClientHandler handler = new PlayerHandler.ClientHandler(socket, playerId, players, handlers, obstacles);
                handlers.put(playerId, handler);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        int p = (args.length > 0) ? Integer.parseInt(args[0]) : 8080;
        new Server(p).start();
    }

    public static void checkAllReady() {
        if (gameStarted) return;
        synchronized (players) {
            if (players.isEmpty()) return;
            boolean allReady = true;
            for (PlayerHandler.Player p : players.values()) {
                if (!p.isReady) {
                    allReady = false;
                    break;
                }
            }
            if (allReady) {
                System.out.println("All players are READY! Starting game...");
                startGame();
            }
        }
    }

    private static void startGame() {
        if (gameStarted) return;
        gameStarted = true;
        remainingSeconds = GAME_DURATION_SECONDS;
        if (!players.isEmpty()) {
            synchronized (players) {
                for (PlayerHandler.Player p : players.values()) {
                    p.score = 0;
                    p.isTagger = false;
                }
                int taggerId = new Random().nextInt(players.size());
                players.get(taggerId).isTagger = true;
                System.out.println("Player " + taggerId + " is the new Tagger.");
            }
        }
        broadcastPlayer();
    }

    private static void spawnObstacles() {
        Random rand = new Random();
        synchronized (obstacles) {
            obstacles.clear();
            for (int i = 0; i < NUM_OBSTACLES; i++) {
                int type = rand.nextInt(NUM_OBSTACLE_TYPES);
                int width = OBSTACLE_TYPE_SIZES[type][0];
                int height = OBSTACLE_TYPE_SIZES[type][1];
                int x, y;

                int minX = PADDING;
                int maxX = MAP_WIDTH - width - PADDING;
                int minY = PADDING;
                int maxY = MAP_HEIGHT - height - PADDING;

                int rangeX = maxX - minX;
                int rangeY = maxY - minY;

                if (rangeX <= 0) rangeX = 1;
                if (rangeY <= 0) rangeY = 1;

                boolean overlap;
                do {
                    overlap = false;
                    x = rand.nextInt(rangeX) + minX;
                    y = rand.nextInt(rangeY) + minY;
                    for (Obstacle other : obstacles) {
                        if (x < other.x + other.width + MIN_OBSTACLE_DISTANCE &&
                                x + width + MIN_OBSTACLE_DISTANCE > other.x &&
                                y < other.y + other.height + MIN_OBSTACLE_DISTANCE &&
                                y + height + MIN_OBSTACLE_DISTANCE > other.y) {
                            overlap = true;
                            break;
                        }
                    }
                } while (overlap);
                obstacles.add(new Obstacle(x, y, width, height, type));
            }
        }
        System.out.println("Spawned " + obstacles.size() + " obstacles.");
    }


    // --- ★★★ 1. แก้ไข movePlayer ให้เรียกใช้เมธอดใหม่ ★★★ ---
    public static void movePlayer(int playerId, String cmd) {
        synchronized (players) {
            PlayerHandler.Player player = players.get(playerId);
            if (player == null) return;

            int newX = player.x;
            int newY = player.y;

            switch (cmd) {
                case "UP": newY -= PLAYER_SPEED; break;
                case "DOWN": newY += PLAYER_SPEED; break;
                case "LEFT": newX -= PLAYER_SPEED; break;
                case "RIGHT": newX += PLAYER_SPEED; break;
            }

            // (ลบโค้ดตรวจสอบการชนเก่าทั้งหมด)
            // เรียกใช้เมธอด helper ใหม่
            trySetPlayerPosition(player, newX, newY);
        }
    }

    // --- ★★★ 2. เพิ่มเมธอด trySetPlayerPosition (เมธอดใหม่) ★★★ ---
    /**
     * พยายามย้ายผู้เล่นไปยังพิกัดใหม่ (newX, newY)
     * เมธอดนี้จะตรวจสอบขอบเขตแผนที่และสิ่งกีดขวางทั้งหมด
     * ถ้าการย้ายนั้น hợp lệ (ไม่ชน) ก็จะอัปเดตตำแหน่งผู้เล่น
     *
     * @param player ผู้เล่นที่จะย้าย
     * @param newX   พิกัด X เป้าหมาย
     * @param newY   พิกัด Y เป้าหมาย
     * @return true หากย้ายสำเร็จ, false หากถูกบล็อก (ชน)
     */
    public static boolean trySetPlayerPosition(PlayerHandler.Player player, int newX, int newY) {

        // 1. ตรวจสอบขอบเขตแผนที่ (Clamping)
        int clampedX = newX;
        int clampedY = newY;
        if (clampedX < 0) clampedX = 0;
        if (clampedX + PlayerHandler.PLAYER_SIZE > MAP_WIDTH) clampedX = MAP_WIDTH - PlayerHandler.PLAYER_SIZE;
        if (clampedY < 0) clampedY = 0;
        if (clampedY + PlayerHandler.PLAYER_SIZE > MAP_HEIGHT) clampedY = MAP_HEIGHT - PlayerHandler.PLAYER_SIZE;

        // 2. ตรวจสอบการชนสิ่งกีดขวาง (AABB) ด้วยพิกัดที่ *clamped* แล้ว
        boolean collision = false;
        synchronized (obstacles) {
            for (Obstacle rect : obstacles) {
                if (clampedX < rect.x + rect.width &&
                        clampedX + PlayerHandler.PLAYER_SIZE > rect.x &&
                        clampedY < rect.y + rect.height &&
                        clampedY + PlayerHandler.PLAYER_SIZE > rect.y) {

                    collision = true;
                    break;
                }
            }
        }

        // 3. ถ้าไม่ชน ถึงจะอัปเดตตำแหน่ง
        if (!collision) {
            player.x = clampedX;
            player.y = clampedY;
            return true; // ย้ายสำเร็จ
        } else {
            // ถ้าชนขอบเขต หรือ ชนสิ่งกีดขวาง
            // เราอาจจะลองย้ายแค่แกน X หรือ Y (Slide)
            // แต่เพื่อความง่าย เราจะ "ไม่อนุญาต" ให้ย้ายเลย
            return false; // ย้ายไม่สำเร็จ (ถูกบล็อก)
        }
    }
    // --- สิ้นสุดการเพิ่มเมธอดใหม่ ---


    static class GameTimer implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (gameStarted && !gameOver) {
                    remainingSeconds--;
                    broadcastPlayer();

                    if (remainingSeconds <= 0) {
                        System.out.println("Game Over");
                        int best = -1;
                        int bestId = -1;
                        boolean tie = false;
                        for (PlayerHandler.Player player : players.values()) {
                            if (player.score > best) {
                                best = player.score;
                                bestId = player.id;
                                tie = false;
                            } else if (player.score == best) {
                                tie = true;
                                bestId = -1;
                            }
                        }
                        winner = tie ? -1 : bestId;
                        gameOver = true;
                        System.out.println(winner + " wins!");
                        broadcastPlayer();
                    }
                }
            }
        }
    }

    public static void broadcastPlayer() {
        ArrayList<PlayerHandler.Player> snap;
        synchronized (players) {
            snap = new ArrayList<>(players.values());
        }
        PlayerHandler.GameState gs = new PlayerHandler.GameState(
                snap,
                new ArrayList<>(obstacles),
                remainingSeconds,
                gameOver,
                winner,
                gameStarted
        );
        synchronized (handlers) {
            for (Map.Entry<Integer, PlayerHandler.ClientHandler> entry : handlers.entrySet()) {
                PlayerHandler.ClientHandler ch = entry.getValue();
                if (ch != null) {
                    ch.sendGameState(gs);
                }
            }
        }
    }

    public static boolean isGameOver() {
        return gameOver;
    }

    public static boolean isGameStarted() {
        return gameStarted;
    }
}