import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.net.URL;
// (ไม่มี import BasicStroke แล้ว)

public class ClientGame {

    public static final int MAP_WIDTH = 1000;
    public static final int MAP_HEIGHT = 600;

    private final Socket socket;
    private final ObjectInputStream in;
    private final ObjectOutputStream out;
    private final int myId;
    private volatile boolean running = true;

    private final java.util.List<PlayerHandler.Player> players = Collections.synchronizedList(new ArrayList<>());
    private final java.util.List<Obstacle> obstacles = Collections.synchronizedList(new ArrayList<>());

    private int lastRemaining = 60;
    private Panel panel;
    private JFrame frame;

    private JButton readyButton;
    private volatile boolean localGameStarted = false;

    private volatile boolean isPressingUp = false;
    private volatile boolean isPressingDown = false;
    private volatile boolean isPressingLeft = false;
    private volatile boolean isPressingRight = false;

    // --- ส่วนของการโหลดรูปภาพ (ย้ายมารวมกัน) ---
    private Image bgImage;
    private Image playerTaggerImg;
    private Image playerRunnerImg;
    private Image[] obstacleImages;
    //----------------con
    public ClientGame(String host, int port) throws IOException {

        socket = new Socket(host, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socket.getInputStream());
        myId = in.readInt();
        System.out.println("My ID: " + myId);
        loadImages();
        frame = new JFrame("Tag Game - Client " + myId);
        panel = new Panel();
        panel.setPreferredSize(new Dimension(MAP_WIDTH, MAP_HEIGHT));
        panel.setFocusable(true);
        frame.setLayout(new BorderLayout());
        readyButton = new JButton("I'm Ready!");
        readyButton.setFont(new Font("Arial", Font.BOLD, 24));
        readyButton.addActionListener(e -> {
            try {
                out.writeObject("READY");
                out.flush();
                readyButton.setEnabled(false);
                readyButton.setText("Waiting for others...");
                panel.requestFocusInWindow();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        });
        JPanel southPanel = new JPanel();
        southPanel.add(readyButton);
        frame.add(panel, BorderLayout.CENTER);
        frame.add(southPanel, BorderLayout.SOUTH);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        panel.requestFocusInWindow();
        Thread listenThread = new Thread(this::listenServer);
        listenThread.setDaemon(true);
        listenThread.start();
        Thread inputThread = new Thread(this::sendInput);
        inputThread.setDaemon(true);
        inputThread.start();
        panel.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {}
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_W: case KeyEvent.VK_UP: isPressingUp = true; break;
                    case KeyEvent.VK_S: case KeyEvent.VK_DOWN: isPressingDown = true; break;
                    case KeyEvent.VK_A: case KeyEvent.VK_LEFT: isPressingLeft = true; break;
                    case KeyEvent.VK_D: case KeyEvent.VK_RIGHT: isPressingRight = true; break;
                }
            }
            @Override
            public void keyReleased(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_W: case KeyEvent.VK_UP: isPressingUp = false; break;
                    case KeyEvent.VK_S: case KeyEvent.VK_DOWN: isPressingDown = false; break;
                    case KeyEvent.VK_A: case KeyEvent.VK_LEFT: isPressingLeft = false; break;
                    case KeyEvent.VK_D: case KeyEvent.VK_RIGHT: isPressingRight = false; break;
                }
            }
        });
    }

    private void loadImages() {
        // (โค้ดส่วนนี้เหมือนเดิม)
        bgImage = loadImage("background.png");
        playerTaggerImg = loadImage("player_tagger.png");
        playerRunnerImg = loadImage("player_runner.png");
        obstacleImages = new Image[3];
        obstacleImages[0] = loadImage("obstacle_0.png");
        obstacleImages[1] = loadImage("obstacle_1.png");
        obstacleImages[2] = loadImage("obstacle_2.png");
    }

    private Image loadImage(String fileName) {
        // (โค้ดส่วนนี้เหมือนเดิม)
        try {
            URL url = getClass().getClassLoader().getResource(fileName);
            if (url == null) {
                System.err.println("Cannot find image: " + fileName);
                return null;
            }
            return ImageIO.read(url);
        } catch (IOException e) {
            System.err.println("Error loading image: " + fileName);
            e.printStackTrace();
            return null;
        }
    }


    /**
     * คลาส Panel สำหรับวาดเกม
     */
    class Panel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;

            // 1. วาดพื้นหลัง
            if (bgImage != null) {
                g.drawImage(bgImage, 0, 0, getWidth(), getHeight(), this);
            } else {
                g.setColor(Color.LIGHT_GRAY);
                g.fillRect(0, 0, getWidth(), getHeight());
            }

            // 2. วาดสิ่งกีดขวาง (เหมือนเดิม)
            synchronized (obstacles) {
                for (Obstacle o : obstacles) {
                    Image obsImg = obstacleImages[o.type];
                    if (obsImg != null) {
                        g.drawImage(obsImg, o.x, o.y, o.width, o.height, null);
                    } else {
                        g.setColor(Color.DARK_GRAY);
                        g.fillRect(o.x, o.y, o.width, o.height);
                    }
                }
            }

            // 3. วาดผู้เล่น
            synchronized (players) {
                for (PlayerHandler.Player p : players) {
                    Image playerImg = p.isTagger ? playerTaggerImg : playerRunnerImg;

                    // --- ★★★ แก้ไขการวาดผู้เล่น ★★★ ---
                    if (playerImg != null) {
                        // (ของเดิม: g.drawImage(playerImg, p.x, p.y, null);)
                        // แก้เป็น:
                        g.drawImage(playerImg, p.x, p.y,
                                PlayerHandler.PLAYER_SIZE, PlayerHandler.PLAYER_SIZE, null);
                    } else {
                        // (สำรอง)
                        g.setColor(p.isTagger ? Color.RED : Color.BLUE);
                        g.fillRect(p.x, p.y, PlayerHandler.PLAYER_SIZE, PlayerHandler.PLAYER_SIZE);
                    }
                    // --- สิ้นสุดการแก้ไข ---

                    // (วาดชื่อ)
                    g.setColor(Color.BLACK);
                    g.drawString(p.name + " (" + p.score + ")", p.x, p.y - 5);
                }
            }

            // 4. วาด UI (เวลา)
            if (localGameStarted) {
                // (โค้ดส่วนนี้เหมือนเดิม)
                g.setColor(Color.WHITE);
                g.setFont(new Font("Arial", Font.BOLD, 36));
                String time = String.format("%02d:%02d", lastRemaining / 60, lastRemaining % 60);
                g.drawString(time, MAP_WIDTH - 120, 40);
            }
        }
    }


    private void sendInput() {
        // (โค้ดส่วนนี้เหมือนเดิม)
        try {
            while (running) {
                String cmd = null;
                if (isPressingUp) cmd = "UP";
                else if (isPressingDown) cmd = "DOWN";
                else if (isPressingLeft) cmd = "LEFT";
                else if (isPressingRight) cmd = "RIGHT";

                if (cmd != null && localGameStarted) {
                    out.writeObject(cmd);
                    out.flush();
                }

                Thread.sleep(1000 / 30);
            }
        } catch (Exception e) {
            System.out.println("Input thread error: " + e.getMessage());
        }
    }

    private void listenServer() {
        // (โค้ดส่วนนี้เหมือนเดิม)
        try {
            while (running) {
                PlayerHandler.GameState gs = (PlayerHandler.GameState) in.readObject();

                if (gs != null) {
                    synchronized (players) { players.clear(); players.addAll(gs.player); }
                    synchronized (obstacles) { obstacles.clear(); obstacles.addAll(gs.obstacles); }
                    lastRemaining = gs.remainingSeconds;
                    boolean lastGameOver = gs.gameover;
                    int lastWinner = gs.winnerId;

                    if (gs.gameStarted && !localGameStarted) {
                        localGameStarted = true;
                        readyButton.setVisible(false);
                        frame.validate();
                        panel.requestFocusInWindow();
                    }

                    panel.repaint();

                    if (lastGameOver) {
                        String msg;
                        if (lastWinner == -1) { msg = "Game Over! (Tie)"; }
                        else if (lastWinner == myId) { msg = "You win!"; }
                        else { msg = "Player " + lastWinner + " wins!"; }
                        JOptionPane.showMessageDialog(frame, msg, "Game Over", JOptionPane.INFORMATION_MESSAGE);
                        running = false;
                        break;
                    }
                }
            }
        } catch (Exception e) { System.out.println("Server connection lost: " + e.getMessage()); }
        finally {
            try { socket.close(); } catch (IOException ignored) {}
            System.exit(0);
        }
    }

    public static void main(String[] args) throws IOException {
        // (โค้dส่วนนี้เหมือนเดิม)
        String host = "localhost"; int port = 8080;
        if (args.length >= 1) { host = args[0]; }
        if (args.length >= 2) { port = Integer.parseInt(args[1]); }
        new ClientGame(host, port);
    }
}