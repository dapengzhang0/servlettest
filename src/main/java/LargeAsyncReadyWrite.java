import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.servlet.AsyncContext;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(urlPatterns = {"/large_async_read_write"}, asyncSupported = true)
public class LargeAsyncReadyWrite extends HttpServlet {
  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    final AsyncContext asyncContext = request.startAsync();



    final ServletInputStream input = request.getInputStream();
    final ServletOutputStream output = response.getOutputStream();

    class ReadWriteListener implements ReadListener, WriteListener {
      final Object lock = new Object();
      final byte[] initialBytes = new byte[1024 * 1024];
      final byte[] smallBytes = new byte[4096];
      volatile int i;

      boolean onWritePossibleExitedWhileStillReady;
      boolean onAllDataReadCalled;
      volatile boolean initialBytesSent;

      byte[] bytes = new byte[4096];
      int bytesRead;

      @Override
      public void onDataAvailable() throws IOException {
        // System.out.println("onDataAvailable() ENTRY");
        while (input.isReady()) {
          int len = input.read(bytes);
          if (len != -1) {
            bytesRead += len;
          }
        }
        // System.out.println("onDataAvailable() EXIT");
      }

      @Override
      public void onAllDataRead() {
        System.out.println("onAllDataRead() ENTRY");
        System.out.println("read " + bytesRead + " bytes");
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/binary");
        boolean shouldWriteResponse;

        synchronized (lock) {
          onAllDataReadCalled = true;
          shouldWriteResponse = onWritePossibleExitedWhileStillReady;
        }

        if (shouldWriteResponse) {
          ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
          scheduler.schedule(
              () -> {
                try {
                  while (output.isReady()) {
                    if (initialBytesSent) {
                      if (i < 10) {
                        System.out.println("sending small bytes inside onAllDataRead(), i = " + i);
                        i++;
                        output.write(smallBytes);
                      } else {
                        System.out.println("all bytes sent");
                        asyncContext.complete();
                        System.out.println("onAllDataRead() EXIT");
                        return;
                      }
                    } else {
                      System.out.println(
                          "onWritePossibleExitedWhileStillReady, now sending initial bytes inside onAllDataRead()");
                      output.write(initialBytes);
                      initialBytesSent = true;
                    }
                  }
                } catch (IOException e) {
                  e.printStackTrace();
                } finally {
                  scheduler.shutdown();
                }
                System.out.println("output.isReady() became false inside onAllDataRead()");
              },
              0,
              TimeUnit.SECONDS);
        }
        System.out.println("onAllDataRead() EXIT");
      }

      @Override
      public void onWritePossible() throws IOException {
        System.out.println("onWritePossible() ENTRY");
        while(output.isReady()) {
          if (initialBytesSent) {
            if (i < 10) {
              System.out.println("sending small bytes inside onWritePossible(), i = " + i);
              i++;
              output.write(smallBytes);
            } else {
              System.out.println("all bytes sent");
              asyncContext.complete();
              System.out.println("onWritePossible() EXIT");
              return;
            }
          } else {
            synchronized (lock) {
              if (!onAllDataReadCalled) {
                onWritePossibleExitedWhileStillReady = true;
                System.out.println("still reading request, so I don't know what to respond yet");
                System.out.println("onWritePossible() EXIT");
                return;
              }
            }

            System.out.println("onAllDataRead already called, now sending initial bytes inside onWritePossible()");
            output.write(initialBytes);
            initialBytesSent = true;
          }
        }

        System.out.println("onWritePossible() EXIT, output.isReady() is false");
      }

      @Override
      public void onError(Throwable throwable) {
        throwable.printStackTrace();
      }
    }

    ReadWriteListener listener = new ReadWriteListener();
    request.getInputStream().setReadListener(listener);
    response.getOutputStream().setWriteListener(listener);
  }
}