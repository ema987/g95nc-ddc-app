# Packaging assets

`icon.icns` (macOS) and `icon.png` (Linux) are generated, not hand-drawn - they render the same
glyph as `AppIcon.kt`'s `renderAppIconImage()`/`drawScreenGlyph()` (a rounded "screen" split into
two panes), just via a throwaway `java.awt.Graphics2D` program instead of Compose, since the
packaging tools need plain raster images rather than a Compose `Painter`.

**Windows is missing `icon.ico`.** Java's built-in ImageIO can't write the ICO format, and there
was no Windows environment available to verify one. Until that's added, `packageMsi` falls back
to Compose Desktop's own default icon.

## Regenerating (e.g. after a palette change in `AppColors`)

From a scratch directory, using the same JDK the project builds with:

```bash
cat > IconGen.java <<'EOF'
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class IconGen {
    public static void main(String[] args) throws Exception {
        int size = Integer.parseInt(args[0]);
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int corner = (int) (size * 0.22f);
        g.setColor(new Color(0x9C, 0x7C, 0xFF)); // AppColors.accent
        g.fillRoundRect(0, 0, size, size, corner, corner);
        float inset = size * 0.14f, gap = size * 0.05f;
        float paneWidth = (size - inset * 2 - gap) / 2f, paneHeight = size - inset * 2;
        int paneCorner = (int) (size * 0.06f);
        g.setColor(Color.WHITE);
        g.fillRoundRect((int) inset, (int) inset, (int) paneWidth, (int) paneHeight, paneCorner, paneCorner);
        g.setColor(new Color(0x52, 0xE0, 0xC4)); // AppColors.signalActive
        g.fillRoundRect((int) (inset + paneWidth + gap), (int) inset, (int) paneWidth, (int) paneHeight, paneCorner, paneCorner);
        g.dispose();
        ImageIO.write(img, "png", new File(args[1]));
    }
}
EOF
javac IconGen.java

# -Djava.awt.headless=true matters in sandboxed/no-display contexts: AWT's toolkit init can hang
# trying to reach a window server otherwise, even for pure offscreen BufferedImage rendering.
mkdir -p icon.iconset
for spec in 16:icon_16x16 32:icon_16x16@2x 32:icon_32x32 64:icon_32x32@2x \
            128:icon_128x128 256:icon_128x128@2x 256:icon_256x256 512:icon_256x256@2x \
            512:icon_512x512 1024:icon_512x512@2x; do
  size="${spec%%:*}"; name="${spec#*:}"
  java -Djava.awt.headless=true IconGen "$size" "icon.iconset/${name}.png"
done
iconutil -c icns icon.iconset -o icon.icns

java -Djava.awt.headless=true IconGen 1024 icon.png
```

Then copy `icon.icns` and `icon.png` here, overwriting the existing ones.
