import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;
import javax.servlet.AsyncContext;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author dapengzhang0@github
 */
@WebServlet(urlPatterns = {"/large_async_write"}, asyncSupported = true)
public class LargeAsyncWrite extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    final AsyncContext asyncContext = request.startAsync();
    final ServletOutputStream output = response.getOutputStream();

    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/binary");

    class MyWriteListener implements javax.servlet.WriteListener {

      final byte[] initialBytes = new byte[1024 * 1024];
      final byte[] smallBytes = new byte[4096];
      final Queue<byte[]> queue = new ArrayDeque<>();

      MyWriteListener() {
        queue.add(initialBytes);
        for (int i = 0; i < 5; i++) {
          queue.add(smallBytes);
        }
      }

      @Override
      public void onWritePossible() throws IOException {
        System.err.println("onWritePossible() ENTRY");

        while (output.isReady()) {
          if (queue.isEmpty()) {
            System.err.println("All bytes sent");
            asyncContext.complete();
            return;
          }
          byte[] bytes = queue.poll();
          System.err.println("Send out " + bytes.length + " bytes");
          output.write(bytes);
        }

        System.err.println("onWritePossible() EXIT, output stream becomes not ready");
      }

      @Override
      public void onError(Throwable throwable) {
        throwable.printStackTrace();
      }
    }

    output.setWriteListener(new MyWriteListener());
  }
}
