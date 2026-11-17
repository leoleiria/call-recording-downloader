import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Generates every application icon from the source logo.
 *
 * <pre>
 *   java script/GenerateIcons.java
 * </pre>
 *
 * Reads {@code packaging/icons/logo.png} (square, transparent corners, ideally 1024 px) and writes:
 *
 * <ul>
 *   <li>{@code modules/app/src/main/resources/icons/app-<size>.png}: window, taskbar and dialog
 *       icons used by JavaFX at runtime;
 *   <li>{@code packaging/icons/app.ico}: Windows executable and installer icon;
 *   <li>{@code packaging/icons/app.icns}: macOS application icon;
 *   <li>{@code packaging/icons/app.png}: Linux icon (512 px).
 * </ul>
 *
 * Run it again whenever the logo changes and commit the results. Uses only the JDK.
 */
public final class GenerateIcons {

  private static final Path SOURCE = Path.of("packaging/icons/logo.png");
  private static final Path RUNTIME = Path.of("modules/app/src/main/resources/icons");
  private static final Path PACKAGING = Path.of("packaging/icons");

  private static final List<Integer> RUNTIME_SIZES = List.of(16, 24, 32, 48, 64, 128, 256);
  private static final List<Integer> ICO_SIZES = List.of(16, 20, 24, 32, 40, 48, 64, 128, 256);

  public static void main(String[] args) throws IOException {
    BufferedImage source = toPremultiplied(ImageIO.read(SOURCE.toFile()));
    if (source.getWidth() != source.getHeight()) {
      throw new IllegalArgumentException("The logo must be square");
    }
    Files.createDirectories(RUNTIME);

    for (int size : RUNTIME_SIZES) {
      writePng(scale(source, size), RUNTIME.resolve("app-" + size + ".png"));
    }

    Map<Integer, byte[]> ico = new LinkedHashMap<>();
    for (int size : ICO_SIZES) {
      ico.put(size, png(scale(source, size)));
    }
    Files.write(PACKAGING.resolve("app.ico"), ico(ico));

    // ICNS entry types holding PNG data, by pixel size.
    Map<String, byte[]> icns = new LinkedHashMap<>();
    icns.put("icp4", png(scale(source, 16)));
    icns.put("icp5", png(scale(source, 32)));
    icns.put("icp6", png(scale(source, 64)));
    icns.put("ic07", png(scale(source, 128)));
    icns.put("ic08", png(scale(source, 256)));
    icns.put("ic09", png(scale(source, 512)));
    icns.put("ic10", png(scale(source, 1024)));
    Files.write(PACKAGING.resolve("app.icns"), icns(icns));

    writePng(scale(source, 512), PACKAGING.resolve("app.png"));
    System.out.println("Icons generated from " + SOURCE);
  }

  /**
   * High-quality resize: halves the image repeatedly with bilinear filtering (avoids the aliasing
   * of a single large step), then does a final bicubic step to the exact size. Works on
   * premultiplied alpha so transparent edges do not get dark fringes.
   */
  static BufferedImage scale(BufferedImage source, int size) {
    BufferedImage current = source;
    int width = source.getWidth();
    while (width / 2 >= size * 2) {
      width /= 2;
      current = resize(current, width, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }
    return resize(current, size, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
  }

  private static BufferedImage resize(BufferedImage image, int size, Object interpolation) {
    BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB_PRE);
    Graphics2D g = out.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
    g.drawImage(image, 0, 0, size, size, null);
    g.dispose();
    return out;
  }

  private static BufferedImage toPremultiplied(BufferedImage image) {
    BufferedImage out =
        new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB_PRE);
    Graphics2D g = out.createGraphics();
    g.drawImage(image, 0, 0, null);
    g.dispose();
    return out;
  }

  private static byte[] png(BufferedImage image) throws IOException {
    BufferedImage straight =
        new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = straight.createGraphics();
    g.drawImage(image, 0, 0, null);
    g.dispose();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(straight, "png", out);
    return out.toByteArray();
  }

  private static void writePng(BufferedImage image, Path file) throws IOException {
    Files.write(file, png(image));
  }

  /** Windows .ico with PNG-compressed entries (supported since Windows Vista). */
  private static byte[] ico(Map<Integer, byte[]> images) {
    int headerSize = 6 + 16 * images.size();
    int total = headerSize + images.values().stream().mapToInt(b -> b.length).sum();
    ByteBuffer buffer = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
    buffer.putShort((short) 0).putShort((short) 1).putShort((short) images.size());
    int offset = headerSize;
    for (Map.Entry<Integer, byte[]> e : images.entrySet()) {
      int size = e.getKey();
      buffer.put((byte) (size >= 256 ? 0 : size)).put((byte) (size >= 256 ? 0 : size));
      buffer.put((byte) 0).put((byte) 0);
      buffer.putShort((short) 1).putShort((short) 32);
      buffer.putInt(e.getValue().length).putInt(offset);
      offset += e.getValue().length;
    }
    images.values().forEach(buffer::put);
    return buffer.array();
  }

  /** macOS .icns with PNG entries. */
  private static byte[] icns(Map<String, byte[]> entries) throws IOException {
    int total = 8 + entries.values().stream().mapToInt(b -> 8 + b.length).sum();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream(total);
    DataOutputStream out = new DataOutputStream(bytes);
    out.writeBytes("icns");
    out.writeInt(total);
    for (Map.Entry<String, byte[]> e : entries.entrySet()) {
      out.writeBytes(e.getKey());
      out.writeInt(8 + e.getValue().length);
      out.write(e.getValue());
    }
    out.flush();
    return bytes.toByteArray();
  }
}
