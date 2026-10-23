import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Native Android 12 splash artwork from the existing Play-store icon. */
public final class SplashV35 {
  static final int SIZE = 864;
  static void quality(Graphics2D g) {
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
  }
  static void centerText(Graphics2D g, String txt, int baseline, Font font, Color color) {
    g.setFont(font); g.setColor(color);
    FontMetrics m = g.getFontMetrics();
    g.drawString(txt, (SIZE-m.stringWidth(txt))/2, baseline);
  }
  public static void main(String[] args) throws Exception {
    if (args.length != 2) throw new IllegalArgumentException("Expected icon.png output.png");
    BufferedImage icon = ImageIO.read(new File(args[0]));
    if (icon == null || icon.getWidth() < 96 || icon.getHeight() < 96)
      throw new IllegalArgumentException("Missing or unsupported existing Videonic icon");
    BufferedImage result = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = result.createGraphics();
    quality(g);
    // Keep native splash safe-area, original APK artwork and white Android background.
    g.drawImage(icon, 346, 295, 172, 172, null);
    centerText(g, "Videonic", 514,
      new Font("SansSerif", Font.BOLD, 29), new Color(13, 25, 47));
    centerText(g, "Lite Video Player", 541,
      new Font("SansSerif", Font.PLAIN, 16), new Color(98, 111, 134));
    g.dispose();
    if (!ImageIO.write(result, "png", new File(args[1]))) throw new IllegalStateException("PNG encoder unavailable");
    System.out.println("Generated Videonic / Lite Video Player native splash from project's existing icon");
  }
}
