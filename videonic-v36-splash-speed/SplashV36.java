import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Native Android 12 splash artwork from the existing Play-store icon. */
public final class SplashV36 {
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
    g.drawImage(icon, 346, 289, 172, 172, null);
    // Use restrained typographic weight, readable secondary text, and a
    // consistent centered rhythm. Only the splash bitmap is changed.
    Font title = new Font("SansSerif", Font.PLAIN, 30);
    title = title.deriveFont(java.util.Map.of(
      java.awt.font.TextAttribute.WEIGHT, java.awt.font.TextAttribute.WEIGHT_SEMIBOLD,
      java.awt.font.TextAttribute.TRACKING, 0.012f));
    Font tagline = new Font("SansSerif", Font.PLAIN, 19);
    tagline = tagline.deriveFont(java.util.Map.of(
      java.awt.font.TextAttribute.WEIGHT, java.awt.font.TextAttribute.WEIGHT_MEDIUM,
      java.awt.font.TextAttribute.TRACKING, 0.006f));
    centerText(g, "Videonic", 516, title, new Color(22, 35, 60));
    centerText(g, "Lite Video Player", 549, tagline, new Color(87, 101, 122));
    g.dispose();
    if (!ImageIO.write(result, "png", new File(args[1]))) throw new IllegalStateException("PNG encoder unavailable");
    System.out.println("Generated Videonic v36 restrained title and readable tagline with original project icon");
  }
}
