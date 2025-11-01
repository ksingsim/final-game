import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException; // <-- import นี้ยังจำเป็น

public class GameLauncher extends JFrame {

    private JTextField ipField;
    private JTextField portField;
    private JButton hostButton;
    private JButton joinButton;

    public GameLauncher() {
        setTitle("Tag Game - Launcher");
        setSize(350, 200);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // --- Panel สำหรับใส่ IP และ Port ---
        JPanel inputPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        inputPanel.add(new JLabel("Server IP:"));
        ipField = new JTextField("localhost");
        inputPanel.add(ipField);

        inputPanel.add(new JLabel("Port:"));
        portField = new JTextField("8080");
        inputPanel.add(portField);

        add(inputPanel, BorderLayout.CENTER);

        // --- Panel สำหรับปุ่ม ---
        JPanel buttonPanel = new JPanel(new FlowLayout());
        hostButton = new JButton("Host Game");
        joinButton = new JButton("Join Game");
        buttonPanel.add(hostButton);
        buttonPanel.add(joinButton);

        add(buttonPanel, BorderLayout.SOUTH);

        // --- เพิ่ม Action Listeners ---
        hostButton.addActionListener(e -> onHostGame(e));
        joinButton.addActionListener(e -> onJoinGame(e));
    }

    /**
     * เมธอดนี้จะทำงานเมื่อกดปุ่ม "Host Game"
     */
    private void onHostGame(ActionEvent e) {
        try {
            int port = Integer.parseInt(portField.getText());

            // 1. เริ่ม Server ใน Thread ใหม่
            startServer(port);

            // 2. ★★★ เพิ่มบรรทัดนี้ ★★★
            // เริ่ม ClientGame (localhost) สำหรับคนที่เป็น Host
            // บรรทัดนี้โยน IOException ได้ ซึ่งจะทำให้ catch บล็อกถูกต้อง
            new ClientGame("localhost", port);

            // 3. ปิดหน้าจอ Launcher
            dispose();

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Please enter a valid port number.", "Error", JOptionPane.ERROR_MESSAGE);

        } catch (IOException ioException) { // <-- ตอนนี้ catch บล็อกนี้มีความหมายแล้ว!
            // แจ้งเตือน หาก Client ของ Host เชื่อมต่อ Server ตัวเองไม่สำเร็จ
            JOptionPane.showMessageDialog(this,
                    "Could not start and connect client: " + ioException.getMessage(),
                    "Host Client Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * เมธอดสำหรับเริ่ม Server ใน Thread แยก
     */
    private Thread startServer(int port) {
        Thread serverThread = new Thread(() -> {
            try {
                // new Server(port).start() จะรันไปเรื่อยๆ
                // และจัดการ Exception ภายในคลาส Server เอง
                new Server(port).start();
            } catch (Exception ex) {
                // ดักจับ Error ตอนรัน Server (เช่น Port ใช้งานแล้ว)
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(null, "Server Error: " + ex.getMessage(),
                                "Host Error", JOptionPane.ERROR_MESSAGE)
                );
            }
        });

        serverThread.setDaemon(true); // ตั้งให้ Server ปิดตามเกม
        serverThread.start(); // <-- สั่งให้ Thread เริ่มทำงาน
        return serverThread;
    }

    /**
     * เมธอดนี้จะทำงานเมื่อกดปุ่ม "Join Game"
     */
    private void onJoinGame(ActionEvent e) {
        try {
            String ip = ipField.getText();
            int port = Integer.parseInt(portField.getText());

            // 1. เริ่ม ClientGame โดยเชื่อมต่อไปยัง IP และ Port ที่กรอก
            new ClientGame(ip, port); // <-- บรรทัดนี้โยน IOException ได้

            // 2. ปิดหน้าจอ Launcher นี้
            dispose();

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Please enter a valid port number.", "Error", JOptionPane.ERROR_MESSAGE);
        } catch (IOException ioException) { // <-- ซึ่งถูกดักจับที่นี่ (ถูกต้องแล้ว)
            // แจ้งเตือน หากเชื่อมต่อ Server ไม่สำเร็จ
            JOptionPane.showMessageDialog(this,
                    "Could not connect to server at " + ipField.getText() + ":" + portField.getText() +
                            "\nError: " + ioException.getMessage(),
                    "Connection Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * main
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new GameLauncher().setVisible(true);
        });
    }
}