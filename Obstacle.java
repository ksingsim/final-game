import java.io.Serializable;

public class Obstacle implements Serializable {

    private static final long serialVersionUID = 4L;

    // ตำแหน่ง (x, y) และ ขนาด (width, height) ของสิ่งกีดขวาง
    public int x, y, width, height;

    // ---  ตัวแปรสำหรับเก็บ "ประเภท" ของสิ่งกีดขวาง ---
    // ( 0 = หิน, 1 = กล่อง, 2 = พุ่มไม้)
    public int type;


    public Obstacle(int x, int y, int width, int height, int type) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.type = type;
    }
}