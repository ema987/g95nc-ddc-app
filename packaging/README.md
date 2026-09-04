# Packaging assets

`icon.icns` (macOS) and `icon.png` (Linux) are generated, not hand-drawn - they render the same
glyph as `AppIcon.kt`'s `renderAppIconImage()`/`drawScreenGlyph()` (a rounded "screen" split into
two panes), just via a throwaway `java.awt.Graphics2D` program instead of Compose, since the
packaging tools need plain raster images rather than a Compose `Painter`.

**Windows is missing `icon.ico`.** Java's built-in ImageIO can't write the ICO format, and there
was no Windows environment available to verify one. Until that's added, `packageMsi` falls back
to Compose Desktop's own default icon.

## TODO on Windows

1. **Generate `icon.ico`.** Windows `.ico` needs multiple resolutions bundled into one file
   (16/32/48/256 at minimum) - ImageIO can't write that format on any platform, so it has to be
   built with a Windows-side tool once there's a machine to do it on. Two options:
   - ImageMagick: `magick icon_16.png icon_32.png icon_48.png icon_256.png icon.ico` (generate the
     PNGs first at those four sizes using the same `IconGen.java` recipe above - just skip the
     `.iconset`/`iconutil` steps, they're macOS-only).
   - Python + Pillow: `Image.open("icon_256.png").save("icon.ico", sizes=[(16,16),(32,32),(48,48),(256,256)])`.
2. **Wire it up** in `build.gradle.kts`, inside `nativeDistributions`:
   ```kotlin
   windows {
       iconFile.set(project.file("packaging/icon.ico"))
   }
   ```
3. **Verify `packageMsi`** actually picks it up and shows the right icon/name in the Start Menu
   and taskbar. The app's display name there already comes from the top-level `packageName` (no
   Windows-specific name override was needed for macOS's problem - that was a macOS-only quirk),
   so this should be icon-only work.

## Building a real .app/.dmg (not just `./gradlew run`)

`./gradlew packageDistributionForCurrentOS` (or `packageDmg`) needs a full JDK with `jpackage` -
the JetBrains Runtime bundled with Android Studio doesn't include it (`checkRuntime` fails with
"'jpackage' is missing" if that's the JDK Gradle is running on). To fix: install a full JDK 21
(matching this project's `jvmToolchain(21)`) - e.g. `brew install openjdk@21`, or download
Temurin/Zulu 21 - and point `JAVA_HOME` at it before running the packaging task, e.g.:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew packageDmg
```

Once that works, that's also the point to confirm the things that couldn't be verified from this
sandboxed dev environment: the Dock icon/tooltip and the "About G95NC Controller" panel on a real,
properly-bundled `.app` (as opposed to the `./gradlew run` dev-mode approximation via
`-Xdock:icon=`/`-Xdock:name=` and `java.awt.Taskbar`/`Desktop.setAboutHandler`).

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
