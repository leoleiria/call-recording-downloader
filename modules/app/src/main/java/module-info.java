/** JavaFX desktop user interface of Call Recording Downloader. */
module io.github.leoleiria.callrecording.app {
  requires io.github.leoleiria.callrecording.ringover;
  requires javafx.controls;
  requires java.prefs;
  requires org.slf4j;
  requires ch.qos.logback.classic;
  requires ch.qos.logback.core;
  // Logback declares java.naming as optional ("requires static") but loads its classes while
  // reading logback.xml. Without this line jlink/jpackage leave it out and the app cannot start.
  requires java.naming;

  // JavaFX instantiates the Application subclass reflectively.
  exports io.github.leoleiria.callrecording.app to
      javafx.graphics;
  // Logback instantiates the redacting converters named in logback.xml.
  exports io.github.leoleiria.callrecording.app.logging to
      ch.qos.logback.core;
}
