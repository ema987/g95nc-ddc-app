# Packaging assets

`icon.icns` (macOS), `icon.png` (Linux) and `icon.ico` (Windows) are generated, not hand-drawn -
they render the same glyph as `AppIcon.kt`'s `renderAppIconImage()`/`drawScreenGlyph()` (a rounded
"screen" split into a white pane and a teal pane), just via a throwaway `java.awt.Graphics2D`
program instead of Compose, since the packaging tools need plain raster images rather than a
Compose `Painter`.

`icon.ico` was built with ImageMagick (`brew install imagemagick`) on macOS - ImageMagick and
Pillow both run fine cross-platform, so producing a valid Windows `.ico` never actually required a
Windows machine, just a tool that can write that format (the JDK's own ImageIO can't). It has not
been verified on a real Windows box that `packageMsi` picks it up correctly and shows the right
icon/name in the Start Menu and taskbar - the app's display name there already comes from the
top-level `packageName` (no Windows-specific name override was needed for macOS's Dock/About
problem - that was a macOS-only quirk), so if something's off it should just be icon wiring, not
naming.

## Building a real .app/.dmg (not just `./gradlew run`)

`./gradlew packageDistributionForCurrentOS` (or `packageDmg`) needs a full JDK with `jpackage` -
the JetBrains Runtime bundled with Android Studio doesn't include it (`checkRuntime` fails with
"'jpackage' is missing" if that's the JDK Gradle is running on).

**`brew install openjdk@21` works but Compose Desktop actively warns against it**
(`compose-multiplatform#3107`) - it fails `checkRuntime` with "Homebrew's JDK distribution may
cause issues with packaging" unless you pass `-Pcompose.desktop.packaging.checkJdkVendor=false`.
Tested with that flag this session: `packageDmg` completed and the resulting `.app` launched and
ran correctly, but that's one build on one machine, not a guarantee the known issue never bites -
worth treating a Homebrew-JDK-built artifact as fine for local testing, not as the thing you'd
ship. For a distribution build, Compose recommends a different vendor (e.g. Amazon Corretto -
`brew install --cask corretto@21`, which needs sudo/your password to install into
`/Library/Java/JavaVirtualMachines`, so it can't be done unattended).

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew packageDmg -Pcompose.desktop.packaging.checkJdkVendor=false
```

Once you have a proper JDK (Corretto or similar) without that warning, drop the
`-Pcompose.desktop.packaging.checkJdkVendor=false` flag and point `JAVA_HOME` at it instead.

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

# macOS: bundle into .icns
iconutil -c icns icon.iconset -o icon.icns

# Linux: a single large PNG
java -Djava.awt.headless=true IconGen 1024 icon.png

# Windows: bundle a few sizes into one multi-resolution .ico (needs ImageMagick: brew install
# imagemagick, or apt/dnf equivalent - or Pillow: Image.open(...).save("icon.ico", sizes=[...]))
magick icon.iconset/icon_16x16.png icon.iconset/icon_32x32.png \
       icon.iconset/icon_128x128.png icon.iconset/icon_256x256.png icon.ico
```

Then copy `icon.icns`, `icon.png` and `icon.ico` here, overwriting the existing ones.
