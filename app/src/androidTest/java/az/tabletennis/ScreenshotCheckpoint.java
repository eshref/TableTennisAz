package az.tabletennis;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.View;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

final class ScreenshotCheckpoint {
    private ScreenshotCheckpoint() {}

    static void captureWindow(File png) throws Exception {
        CompletableFuture<Bitmap> copied = new CompletableFuture<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                View root = activity.getWindow().getDecorView();
                Bitmap frame = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
                Runnable copy = () -> PixelCopy.request(activity.getWindow(), frame, status -> {
                    if (status == PixelCopy.SUCCESS) copied.complete(frame);
                    else {
                        frame.recycle();
                        copied.completeExceptionally(new IllegalStateException("Android screenshot copy failed: " + status));
                    }
                }, new Handler(Looper.getMainLooper()));
                // A committed frame is ready for PixelCopy before it necessarily reaches the display.
                if (Build.VERSION.SDK_INT >= 29 && root.isHardwareAccelerated())
                    root.getViewTreeObserver().registerFrameCommitCallback(copy);
                else root.postOnAnimation(copy);
                root.invalidate();
                return;
            }
            throw new IllegalStateException("No resumed activity to capture");
        });
        Bitmap screenshot = copied.get(30, TimeUnit.SECONDS);
        try (var output = new FileOutputStream(png)) {
            if (!screenshot.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IOException("Cannot save screenshot " + png);
        } finally { screenshot.recycle(); }
        exportIfRequired(png);
    }

    static void exportIfRequired(File png) throws Exception {
        var arguments = InstrumentationRegistry.getArguments();
        String port = arguments.getString("checkpointPort");
        if (port == null || !Arrays.asList(arguments.getString("checkpointScreenshots", "").split(",")).contains(png.getName()))
            return;

        // ADB reverse forwards this loopback connection to the host verifier.
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", Integer.parseInt(port)), 10_000);
            socket.setSoTimeout(45_000);
            var output = new DataOutputStream(socket.getOutputStream());
            output.writeUTF(png.getName());
            output.writeInt(Math.toIntExact(png.length()));
            Files.copy(png.toPath(), output);
            output.flush();
            if (socket.getInputStream().read() != 1)
                throw new IllegalStateException("Host did not validate " + png.getName());
        }
    }
}
