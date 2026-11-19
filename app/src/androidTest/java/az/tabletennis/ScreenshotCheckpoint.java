package az.tabletennis;

import androidx.test.platform.app.InstrumentationRegistry;

import java.io.DataOutputStream;
import java.io.File;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Arrays;

final class ScreenshotCheckpoint {
    private ScreenshotCheckpoint() {}

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
