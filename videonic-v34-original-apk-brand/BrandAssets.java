import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import javax.imageio.ImageIO;

/** Render minimal splash from the REAL installed Videonic brand raster, not a generated logo. */
public final class BrandAssets {
    private static final int CANVAS = 864;
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Expected: icon.png splash.png play-icon.png");
        BufferedImage icon = ImageIO.read(new File(args[0]));
        if (icon == null || icon.getWidth() < 96 || icon.getHeight() < 96) {
            throw new IllegalArgumentException("Source icon is missing, unsupported, or too small (must be at least 96px)");
        }
        if (Math.abs((double)icon.getWidth() / icon.getHeight() - 1) > 0.05) {
            throw new IllegalArgumentException("Source icon should be square; found " + icon.getWidth() + "x" + icon.getHeight());
        }
        icon = clearOutsideWhite(icon);
        // Android 12+ splash imposes a circular mask. Both the icon and the app name
        // are deliberately confined to the conservative central safe region.
        BufferedImage splash = new BufferedImage(CANVAS, CANVAS, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = splash.createGraphics();
        quality(g);
        int sz = 172;
        g.drawImage(icon, (CANVAS-sz)/2, 311, sz, sz, null);
        String label="Videonic Player";
        Font font = new Font("SansSerif", Font.BOLD, 25);
        g.setFont(font);
        g.setColor(new Color(15,25,46));
        FontMetrics fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(label);
        if (textWidth > 220) {
            font = font.deriveFont(22f);
            g.setFont(font);
            fm = g.getFontMetrics();
            textWidth = fm.stringWidth(label);
        }
        g.drawString(label, (CANVAS-textWidth)/2, 535);
        g.dispose();
        if (!ImageIO.write(splash, "png", new File(args[1]))) throw new IllegalStateException("PNG encoder unavailable");

        // Exact same icon image resized for Play Console; transparent corners preserved.
        BufferedImage listing = new BufferedImage(512,512, BufferedImage.TYPE_INT_ARGB);
        Graphics2D h = listing.createGraphics();
        quality(h);
        h.drawImage(icon, 0, 0, 512, 512, null);
        h.dispose();
        if (!ImageIO.write(listing, "png", new File(args[2]))) throw new IllegalStateException("PNG encoder unavailable");
        System.out.println("BRAND VERIFIED: used original project icon " + args[0]);
        System.out.println("Minimal single-screen splash: centered icon + Videonic Player; white background supplied by Android launch theme.");
        System.out.println("Play Store icon: 512x512 PNG from same source.");
    }
    // Remove ONLY white connected to the outer image edge. The white play
    // triangle is enclosed by blue pixels and therefore remains untouched.
    // This gives Play Console a transparent PNG without changing the logo art.
    private static BufferedImage clearOutsideWhite(BufferedImage source) {
        int w=source.getWidth(), h=source.getHeight();
        BufferedImage clean=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        Graphics2D gg=clean.createGraphics(); gg.drawImage(source,0,0,null); gg.dispose();
        boolean[] visited=new boolean[w*h];
        ArrayDeque<Integer> q=new ArrayDeque<>();
        for (int x=0;x<w;x++) { addEdge(x,0,w,h,clean,visited,q); addEdge(x,h-1,w,h,clean,visited,q); }
        for (int y=1;y<h-1;y++) { addEdge(0,y,w,h,clean,visited,q); addEdge(w-1,y,w,h,clean,visited,q); }
        while (!q.isEmpty()) {
            int p=q.removeFirst(), x=p%w, y=p/w;
            clean.setRGB(x,y,0x00000000);
            if(x>0)addEdge(x-1,y,w,h,clean,visited,q);
            if(x<w-1)addEdge(x+1,y,w,h,clean,visited,q);
            if(y>0)addEdge(x,y-1,w,h,clean,visited,q);
            if(y<h-1)addEdge(x,y+1,w,h,clean,visited,q);
        }
        return clean;
    }
    private static void addEdge(int x,int y,int w,int h,BufferedImage img,boolean[] vis,ArrayDeque<Integer> q) {
        int at=y*w+x;
        if(vis[at])return;
        vis[at]=true;
        int c=img.getRGB(x,y);
        int alpha=(c >>> 24)&255, r=(c >>> 16)&255, g=(c>>>8)&255, b=c&255;
        // Do not erase colored parts of the original blue launcher icon.
        if(alpha>240 && r>=245 && g>=245 && b>=245)q.addLast(at);
    }
    private static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }
}
