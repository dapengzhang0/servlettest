import java.io.File;
import jdk.incubator.http.HttpClient;
import jdk.incubator.http.HttpClient.Version;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.http2.Http2Protocol;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LargeAsyncReadyWriteTest {

  Tomcat tomcat;

  @Before
  public void setUp() throws Exception {
    tomcat = new Tomcat();
    tomcat.setPort(0);
    Context ctx = tomcat.addContext("/large_async_read_write", new File("build/tmp").getAbsolutePath());
    Tomcat.addServlet(ctx, "large_async_read_write", new LargeAsyncReadyWrite()).setAsyncSupported(true);
    ctx.addServletMappingDecoded("/*", "large_async_read_write");
    tomcat.getConnector().addUpgradeProtocol(new Http2Protocol());

    try {
      tomcat.start();
    } catch (LifecycleException e) {
      tomcat.stop();
      throw e;
    }
  }

  @Test
  public void doNothing() {
    HttpClient client = HttpClient.newBuilder()
        .version(Version.HTTP_2)
  }

  @After
  public void tearDown() throws Exception {
    tomcat.stop();
  }
}