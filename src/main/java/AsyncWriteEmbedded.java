import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.http2.Http2Protocol;

public class AsyncWriteEmbedded {

  public static void main(String[] args) throws Exception {
    Tomcat tomcat = new Tomcat();
    tomcat.setPort(0);
    // Context ctx = tomcat.addContext("/large_async_read_write", new File("build/tmp").getAbsolutePath());
    /// Tomcat.addServlet(ctx, "large_async_read_write", new LargeAsyncReadyWrite()).setAsyncSupported(true);
    // ctx.addServletMappingDecoded("/*", "large_async_read_write");
    tomcat.getConnector().addUpgradeProtocol(new Http2Protocol());

    try {
      tomcat.start();
    } catch (LifecycleException e) {
      tomcat.stop();
      throw e;
    }
    int port = tomcat.getConnector().getLocalPort();

    byte[] bytes = new byte[1024 * 1024];
    new Random().nextBytes(bytes);
    try (FileOutputStream file = new FileOutputStream("newfile")) {
      file.write(bytes);
    }

    for (int i = 0; i < 0; i++) {
      long startTime = System.nanoTime();
      Process proc =
          Runtime.getRuntime()
              .exec(
                  "nghttp -t 10 --header=:method:POST --window-bits=17 --header=Content-Type:application/binary -d newfile"
                      + " http://localhost:"
                      + port
                      + "/large_async_read_write");

      String s;
      try (BufferedReader stdInput = new BufferedReader(
          new InputStreamReader(proc.getInputStream()))) {
        while ((s = stdInput.readLine()) != null) {
          System.out.println(s);
        }
      }

      try (BufferedReader stdErr = new BufferedReader(
          new InputStreamReader(proc.getErrorStream()))) {
        while ((s = stdErr.readLine()) != null) {
          System.out.println(s);
        }
      }

      if (System.nanoTime() - startTime > TimeUnit.SECONDS.toNanos(9)) {
        throw new RuntimeException("time out");
      }
    }

    tomcat.stop();
  }
}
