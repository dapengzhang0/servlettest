import static io.undertow.servlet.Servlets.defaultContainer;
import static io.undertow.servlet.Servlets.deployment;
import static io.undertow.servlet.Servlets.servlet;

import io.netty.example.http2.helloworld.client.Http2Client;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import java.io.File;
import java.net.InetSocketAddress;
import javax.servlet.ServletException;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.http2.Http2Protocol;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author dapengzhang0@github
 */
@RunWith(JUnit4.class)
public class LargeAsyncWriteTest {

  // To test with Tomcat, add @Before annotation to setUpTomcat(), and remove @Before annotation
  // from setUpUndertow()
  private Tomcat tomcat;

  // To test with Undertow, add @Before annotation to setUpUndertow(), and remove @Before annotation
  // from setUpTomcat()
  private Undertow undertow;
  private DeploymentManager undertowManager;

  private int port;

  @Before
  public void setUpTomcat() throws Exception {
    tomcat = new Tomcat();
    tomcat.setPort(0);
    Context ctx = tomcat.addContext("/large_async_write", new File("build/tmp").getAbsolutePath());
    Tomcat.addServlet(ctx, "large_async_write", new LargeAsyncWrite()).setAsyncSupported(true);
    ctx.addServletMappingDecoded("/*", "large_async_write");
    tomcat.getConnector().addUpgradeProtocol(new Http2Protocol());

    try {
      tomcat.start();
    } catch (LifecycleException e) {
      tomcat.stop();
      throw e;
    }

    port = tomcat.getConnector().getLocalPort();
  }

  // @Before
  public void setUpUndertow() {
    DeploymentInfo servletBuilder =
        deployment()
            .setClassLoader(LargeAsyncWrite.class.getClassLoader())
            .setContextPath("/large_async_write")
            .setDeploymentName("UndertowTest.war")
            .addServlets(
                servlet("LargeAsyncReadyWrite", LargeAsyncWrite.class)
                    .addMapping("/*")
                    .setAsyncSupported(true));

    undertowManager = defaultContainer().addDeployment(servletBuilder);
    undertowManager.deploy();

    HttpHandler servletHandler;
    try {
      servletHandler = undertowManager.start();
    } catch (ServletException e) {
      throw new RuntimeException(e);
    }

    undertow =
        Undertow.builder()
            .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
            .setServerOption(UndertowOptions.SHUTDOWN_TIMEOUT, 5000 /* 5 sec */)
            .addHttpListener(0, "localhost")
            .setHandler(servletHandler)
            .build();
    undertow.start();
    port = ((InetSocketAddress) undertow.getListenerInfo().get(0).getAddress()).getPort();
  }

  @Test
  public void reproduceBug() {
    System.setProperty("port", "" + port);
    System.setProperty("url2",  "/large_async_write");
    Http2Client.main(new String[0]);
  }

  @After
  public void tearDown() throws Exception {
    if (tomcat != null) {
      tomcat.stop();
    }
    if (undertow != null) {
      undertow.stop();
    }
    if (undertowManager != null) {
      try {
        undertowManager.stop();
      } catch (ServletException e) {
        throw new AssertionError("failed to stop container", e);
      }
    }
  }
}
