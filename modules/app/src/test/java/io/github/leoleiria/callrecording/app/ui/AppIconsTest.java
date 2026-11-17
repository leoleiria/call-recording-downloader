package io.github.leoleiria.callrecording.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Guards against a missing or renamed icon, which would only fail when the app starts. */
class AppIconsTest {

  private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

  @ParameterizedTest
  @ValueSource(ints = {16, 24, 32, 48, 64, 128, 256})
  void everyIconSizeIsBundledAsPng(int size) throws IOException {
    try (InputStream in = AppIcons.class.getResourceAsStream("/icons/app-" + size + ".png")) {
      assertThat(in).as("icon %d px", size).isNotNull();
      assertThat(in.readNBytes(PNG_SIGNATURE.length)).containsExactly(PNG_SIGNATURE);
    }
  }
}
