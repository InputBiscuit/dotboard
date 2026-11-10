import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Renders the store icon (fastlane metadata) at 512x512 with the same design as tools/icon/icon.py:
 * an amber dot-matrix "D" on a grid of dim dots, on black.
 *
 * Usage: java -Djava.awt.headless=true tools/icon/IconPng.java fastlane/metadata/android/en-US/images/icon.png
 */
public class IconPng {
    static final String[] LETTER = {
        "####..",
        "#...#.",
        "#....#",
        "#....#",
        "#....#",
        "#...#.",
        "####..",
    };

    public static void main(String[] args) throws Exception {
        int size = 512;
        double scale = size / 108.0; // adaptive icon canvas is 108dp
        double pitch = 8.0, r = 3.1;
        int cols = LETTER[0].length(), rows = LETTER.length;
        double x0 = 54 - (cols - 1) * pitch / 2, y0 = 54 - (rows - 1) * pitch / 2;

        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, size, size);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                double cx = (x0 + col * pitch) * scale, cy = (y0 + row * pitch) * scale, rr = r * scale;
                g.setColor(LETTER[row].charAt(col) == '#' ? new Color(0xFFB300) : new Color(0x2A1D00));
                g.fill(new Ellipse2D.Double(cx - rr, cy - rr, 2 * rr, 2 * rr));
            }
        }
        ImageIO.write(img, "png", new File(args[0]));
    }
}
