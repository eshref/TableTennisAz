import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ServerSocket;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;

/** Repository tooling. Product assertions run in the installed Android tests. */
class AndroidVerify {
    private final Path repo, cache, evidence, work, sdk;
    private final Map<String, String> env = new HashMap<>(System.getenv());
    private final Properties tools = new Properties(), result = new Properties();
    private final List<Process> children = Collections.synchronizedList(new ArrayList<>());
    private final List<Path> temporaryDirectories = Collections.synchronizedList(new ArrayList<>());
    private Path build;
    private Process emulator;
    private boolean retain, preflightOnly, software, cleaned;
    private String tests = "az.tabletennis.MatchDeviceTest", serial;
    private int adbPort;
    private FileChannel portChannel;
    private FileLock portLock;
    private volatile long deadline;
    private String phase = "setup";

    public static void main(String[] args) throws Exception {
        AndroidVerify runner;
        try { runner = new AndroidVerify(args); }
        catch (Exception invalid) {
            Properties failure = new Properties();
            failure.setProperty("status", "failed");
            failure.setProperty("phase", "arguments-or-setup");
            failure.setProperty("failure", invalid.toString());
            try (Writer output = Files.newBufferedWriter(Path.of(System.getenv("ANDROID_VERIFY_EVIDENCE")).resolve("result.properties"))) { failure.store(output, "Android verification"); }
            System.err.println(invalid.getMessage());
            System.exit(2);
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(runner::cleanup));
        Thread.ofPlatform().daemon().start(() -> {
            while (true) {
                try { Thread.sleep(1000); runner.remaining(); }
                catch (InterruptedException stopped) { return; }
                catch (IOException expired) {
                    runner.result.setProperty("status", "failed");
                    runner.result.setProperty("failure", "Phase time budget exhausted");
                    runner.result.setProperty("phase", runner.phase);
                    System.err.println("Time budget exhausted; stopping owned processes and saving evidence");
                    System.exit(124);
                }
            }
        });
        int exit = 0;
        try { runner.execute(); runner.result.setProperty("status", "passed"); }
        catch (Exception failure) {
            exit = 1;
            runner.result.setProperty("status", "failed");
            runner.result.setProperty("failure", failure.toString());
            System.err.println("FAILED during " + runner.phase + ": " + failure.getMessage());
            runner.diagnose();
        } finally {
            runner.result.setProperty("phase", runner.phase);
            runner.cleanup();
        }
        System.out.println("Result: " + runner.result.getProperty("status") + "; evidence: " + runner.evidence);
        System.exit(runner.result.getProperty("status").equals("passed") ? exit : 1);
    }

    private AndroidVerify(String[] args) throws Exception {
        repo = Path.of(args[0]).toRealPath();
        cache = Path.of(System.getenv("ANDROID_VERIFY_CACHE")).toAbsolutePath();
        evidence = Path.of(System.getenv("ANDROID_VERIFY_EVIDENCE")).toRealPath();
        for (int i = 1; i < args.length; i++) switch (args[i]) {
            case "--retain-device" -> retain = true;
            case "--preflight-only" -> preflightOnly = true;
            case "--software" -> software = true;
            case "--tests" -> tests = args[++i];
            default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
        }
        if (!tests.matches("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)+"))
            throw new IllegalArgumentException("--tests requires one fully qualified test class; run separate fixtures separately");
        software |= !Files.isReadable(Path.of("/dev/kvm")) || !Files.isWritable(Path.of("/dev/kvm"));
        deadline = Long.parseLong(System.getenv("ANDROID_VERIFY_STARTED")) * 1000 + Long.parseLong(System.getenv("ANDROID_VERIFY_PREFLIGHT_SECONDS")) * 1000;
        sdk = sdkDirectory();
        try (Reader input = Files.newBufferedReader(repo.resolve("scripts/android/tools.properties"))) { tools.load(input); }
        Files.copy(repo.resolve("scripts/android/tools.properties"), evidence.resolve("tools.properties"));
        Files.copy(repo.resolve("scripts/android/limits.properties"), evidence.resolve("limits.properties"));
        // Allocate disposable state only after fallible cache/configuration setup succeeds.
        work = Files.createTempDirectory(evidence.getParent(), "device-");
        env.put("ANDROID_HOME", sdk.toString());
        env.put("ANDROID_SDK_ROOT", sdk.toString());
        env.put("ANDROID_USER_HOME", work.resolve("android-user").toString());
        env.put("ANDROID_EMULATOR_HOME", work.resolve("android-user").toString());
        env.put("ANDROID_AVD_HOME", work.resolve("avds").toString());
        env.remove("ANDROID_SERIAL");
        result.setProperty("mode", software ? "software" : "accelerated");
        result.setProperty("api", "36");
        result.setProperty("tests", tests);
        result.setProperty("started", Instant.now().toString());
        result.setProperty("sdkCache", sdk.toString());
    }

    private void execute() throws Exception {
        note("Preparing pinned tools (" + (software ? "software emulation; " + Long.parseLong(env.get("ANDROID_VERIFY_PREFLIGHT_SECONDS")) / 60 + "-minute preflight limit" : "hardware acceleration") + ")");
        if (Files.getFileStore(cache).getUsableSpace() < 12L * 1024 * 1024 * 1024 || Files.getFileStore(work).getUsableSpace() < 12L * 1024 * 1024 * 1024)
            throw new IOException("Tool/device filesystem needs 12 GiB free. Set ANDROID_VERIFY_CACHE and ANDROID_VERIFY_OUTPUT to a larger filesystem.");
        for (String tool : List.of("platform", "build-tools", "platform-tools", "emulator", "image")) install(tool);
        if (!software) {
            String acceleration = command("acceleration", 30, false, sdk.resolve("emulator/emulator").toString(), "-accel-check");
            software = !acceleration.contains("installed and usable");
            result.setProperty("mode", software ? "software" : "accelerated");
        }
        phase = "build";
        Path buildParent = Path.of(env.getOrDefault("ANDROID_VERIFY_BUILD_ROOT", "/tmp"));
        if (Files.getFileStore(buildParent).getUsableSpace() < 1024L * 1024 * 1024) buildParent = work;
        build = Files.createTempDirectory(buildParent, "tabletennisaz-build-");
        Path init = work.resolve("build-paths.gradle");
        Files.writeString(init, "allprojects { layout.buildDirectory.set(new File('" + groovy(build.toString()) + "', project.name)) }\n");
        note("Building both APKs in isolated output");
        run("build", 600, repo.toString() + "/gradlew", "--no-daemon", "--project-cache-dir", build.resolve("gradle-cache").toString(),
                "-I", init.toString(), ":app:assembleDebug", ":app:assembleDebugAndroidTest");
        Path apk = build.resolve("app/outputs/apk/debug/app-debug.apk");
        result.setProperty("apk.sha256", digest(apk));
        Files.copy(apk, evidence.resolve("app-debug.apk"));
        Files.copy(build.resolve("app/outputs/apk/androidTest/debug/app-debug-androidTest.apk"), evidence.resolve("app-debug-androidTest.apk"));
        phase = "preflight";
        try { startDevice(); preflight(); }
        catch (Exception first) {
            if (remaining() < 120 || !environmentFailure(first)) throw first;
            note("One automatic environment recovery; the original preflight deadline remains in force");
            result.setProperty("recovery", "1");
            diagnose();
            try (var logs = Files.list(evidence)) {
                Path previous = Files.createDirectories(evidence.resolve("first-attempt"));
                for (Path log : logs.filter(p -> p.toString().endsWith(".log")).toList())
                    Files.copy(log, previous.resolve(log.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
            run("reconnect-offline", 30, adbBinary(), "-P", Integer.toString(adbPort), "reconnect", "offline");
            waitAdb(software ? 600 : 60);
            configureRuntime();
            waitBoot();
            dismissSystemAnr();
            preflight();
        }
        result.setProperty("preflight", "passed");
        note("Preflight passed: installed scores, window focus, nonblank screenshots and dimensions");
        if (preflightOnly) return;
        phase = "device-matrix";
        deadline = System.currentTimeMillis() + Long.parseLong(env.get("ANDROID_VERIFY_MATRIX_SECONDS")) * 1000;
        note("Running host integration tests and lint");
        run("host-checks", Math.toIntExact(remaining()), repo.toString() + "/gradlew", "--no-daemon", "--project-cache-dir", build.resolve("gradle-cache").toString(),
                "-I", init.toString(), ":app:testDebugUnitTest", ":app:lintDebug");
        result.setProperty("hostChecks", "passed");
        instrument("light", tests, false, false, 412, 891);
        disconnect();
        appearance("dark", "412x891", "1.0");
        instrument("dark-offline", tests, false, true, 412, 891);
        appearance("light", "320x568", "1.5");
        instrument("narrow-large", tests, false, true, 320, 568);
        appearance("dark", "891x320", "1.5");
        instrument("landscape-large", tests, false, true, 891, 320);
        if (tests.equals("az.tabletennis.MatchDeviceTest")) {
            for (int id : List.of(501, 502)) {
                Properties before = proof("light", id);
                for (String configuration : List.of("dark-offline", "narrow-large", "landscape-large")) {
                    Properties after = proof(configuration, id);
                    for (String key : List.of("leftScore", "rightScore", "updated"))
                        require(Objects.equals(before.getProperty(key), after.getProperty(key)), "Offline visible " + key + " changed");
                    require("false".equals(after.getProperty("refreshEnabled")), "Offline Refresh must be disabled");
                    require(after.getProperty("status", "").contains("İnternet bağlantısı yoxdur"), "Offline notice missing");
                }
            }
            result.setProperty("offlineVisibleValues", "preserved after force-stop/new instrumentation processes");
        }
        result.setProperty("matrix", "passed");
        note("Installed matrix passed");
    }

    private void install(String id) throws Exception {
        Path target = id.equals("image") ? imageDirectory() : sdk.resolve(tools.getProperty(id + ".destination"));
        String sha = tools.getProperty(id + ".sha256");
        Path marker = target.resolve(".verified-archive");
        Files.createDirectories(cache);
        try (FileChannel channel = FileChannel.open(cache.resolve("sdk.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = acquire(channel)) {
            if (Files.isRegularFile(marker) && Files.readString(marker).equals(sha)) return;
            require(!Files.exists(target) || Files.isRegularFile(marker) || Files.isRegularFile(target.resolve(".installing")), "Unmanaged SDK directory: " + target + "; select an empty ANDROID_VERIFY_CACHE");
            Path downloads = Files.createDirectories(cache.resolve("downloads"));
            Path archive = downloads.resolve(id + "-" + sha.substring(0, 12) + ".zip");
            if (!Files.isRegularFile(archive) || !digest(archive).equals(sha)) {
                note("Downloading " + id);
                Path partial = archive.resolveSibling(archive.getFileName() + ".part");
                run("download-" + id, 600, "curl", "--fail", "--location", "--retry", "2", "--connect-timeout", "20", "--max-time", "600",
                        tools.getProperty(id + ".url"), "-o", partial.toString());
                require(digest(partial).equals(sha), "Checksum mismatch: " + id);
                Files.move(partial, archive, StandardCopyOption.REPLACE_EXISTING);
            }
            note("Unpacking " + id);
            if (id.equals("image")) {
                remove(target);
                Files.createDirectories(target);
                Files.writeString(target.resolve(".installing"), sha);
                run("unpack-" + id, 240, "unzip", "-q", "-o", archive.toString(), "-d", target.getParent().toString());
                Files.writeString(marker, sha);
                Files.delete(target.resolve(".installing"));
                return;
            }
            Path staging = Files.createTempDirectory(cache, "unpack-");
            if (!id.equals("image")) {
                Files.delete(staging);
                staging = Files.createTempDirectory(sdk.getParent(), "unpack-");
            }
            temporaryDirectories.add(staging);
            try {
                run("unpack-" + id, 240, "unzip", "-q", archive.toString(), "-d", staging.toString());
                Path extracted = staging.resolve(tools.getProperty(id + ".root"));
                require(Files.isDirectory(extracted), "Archive root missing: " + id);
                remove(target);
                Files.createDirectories(target.getParent());
                try { Files.move(extracted, target); }
                catch (IOException cannotRenameDirectory) {
                    Files.createDirectories(target);
                    Files.writeString(target.resolve(".installing"), sha);
                    run("copy-" + id, 240, "cp", "-R", extracted + "/.", target.toString());
                    Files.delete(target.resolve(".installing"));
                }
                Files.writeString(marker, sha);
            } finally { remove(staging); temporaryDirectories.remove(staging); }
        }
    }

    private void startDevice() throws Exception {
        Path avds = Files.createDirectories(work.resolve("avds"));
        Path avd = Files.createDirectories(avds.resolve("verification.avd"));
        Path image = imageDirectory();
        prepareUserdata(avd, image);
        Files.writeString(avds.resolve("verification.ini"), "avd.ini.encoding=UTF-8\npath=" + avd + "\n");
        Files.writeString(avd.resolve("config.ini"), """
                avd.ini.encoding=UTF-8
                abi.type=x86_64
                hw.cpu.arch=x86_64
                hw.cpu.ncore=2
                hw.ramSize=2560
                hw.lcd.width=412
                hw.lcd.height=891
                hw.lcd.density=160
                hw.gpu.enabled=yes
                hw.gpu.mode=swiftshader
                hw.keyboard=yes
                disk.dataPartition.size=6G
                showDeviceFrame=no
                tag.id=aosp_atd
                tag.display=AOSP ATD
                """ + "image.sysdir.1=" + image + "/\n");
        int port = reservePort();
        adbPort = freePort();
        env.put("ANDROID_ADB_SERVER_PORT", Integer.toString(adbPort));
        env.put("ADB_SERVER_SOCKET", "tcp:localhost:" + adbPort);
        run("adb-start", 30, adbBinary(), "-P", Integer.toString(adbPort), "start-server");
        serial = "emulator-" + port;
        note("Starting isolated API 36 emulator");
        emulator = launch(evidence.resolve("emulator.log"), sdk.resolve("emulator/emulator").toString(), "-avd", "verification",
                "-port", Integer.toString(port), "-no-window", "-no-audio", "-no-snapshot", "-no-boot-anim", "-no-metrics", "-show-kernel",
                "-gpu", "swiftshader", "-accel", software ? "off" : "on", "-memory", "2560", "-cores", "2");
        waitAdb(software ? 1800 : 180);
        configureRuntime();
        waitBoot();
    }

    private void waitAdb(int seconds) throws Exception {
        long until = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < until) {
            require(emulator.isAlive(), "Emulator exited; see emulator.log");
            try {
                if (shell("early-adb", 15, false, "getprop ro.build.version.sdk").strip().equals("36")) return;
            } catch (IOException ignored) { }
            pause(3000);
        }
        throw new IOException("Android ADB timeout");
    }

    private void prepareUserdata(Path avd, Path image) throws Exception {
        note("Preparing sparse blank data image on Linux scratch space");
        require(Files.getFileStore(Path.of("/tmp")).getUsableSpace() >= 256L * 1024 * 1024, "Need 256 MiB free Linux scratch space to format the blank data image");
        Path scratch = Files.createTempDirectory(Path.of("/tmp"), "tabletennisaz-data-");
        temporaryDirectories.add(scratch);
        try {
            Path raw = scratch.resolve("userdata.raw");
            Path data = Files.createDirectories(scratch.resolve("data"));
            try (var source = Files.walk(image.resolve("data"))) {
                for (Path entry : source.toList()) {
                    Path target = data.resolve(image.resolve("data").relativize(entry));
                    if (Files.isDirectory(entry)) Files.createDirectories(target);
                    else Files.copy(entry, target);
                }
            }
            Files.writeString(data.resolve("local.prop"), "\nro.hw_timeout_multiplier=" + (software ? "10" : "1")
                    + "\ndebug.hwui.drawing_enabled=1\n", StandardOpenOption.APPEND);
            try (RandomAccessFile file = new RandomAccessFile(raw.toFile(), "rw")) { file.setLength(6L * 1024 * 1024 * 1024); }
            // Keep host e2fsprogs defaults (notably orphan_file) out of the older guest's filesystem.
            run("format-data", 120, "mkfs.ext4", "-q", "-F", "-m", "0", "-O",
                    "none,has_journal,ext_attr,resize_inode,dir_index,filetype,extent,64bit,flex_bg,sparse_super,large_file,huge_file,dir_nlink,extra_isize,metadata_csum,encrypt",
                    "-d", data.toString(), raw.toString());
            run("convert-data", 120, sdk.resolve("emulator/qemu-img").toString(), "convert", "-f", "raw", "-O", "qcow2", raw.toString(), avd.resolve("userdata-qemu.img").toString());
        } finally { remove(scratch); temporaryDirectories.remove(scratch); }
    }

    private void configureRuntime() throws Exception {
        String multiplier = software ? "10" : "1";
        if (shell("initial-runtime-timeout", 60, true, "getprop ro.hw_timeout_multiplier").strip().equals(multiplier)) {
            shell("initial-runtime-drawing", 60, true, "su 0 setprop debug.hwui.drawing_enabled 1");
            require(shell("runtime-drawing", 60, true, "getprop debug.hwui.drawing_enabled").strip().equals("1"), "Drawing remains disabled");
            note("Verified first-boot runtime settings without restarting the framework");
            return;
        }
        // Configure the disposable guest before restarting the Java runtime, where Build caches this property.
        shell("runtime-configure", 60, true, "su 0 sh -c 'setprop ro.hw_timeout_multiplier " + (software ? "10" : "1")
                + "; setprop debug.hwui.drawing_enabled 1; setprop sys.boot_completed 0; setprop ctl.restart zygote'");
        require(shell("runtime-timeout", 30, true, "getprop ro.hw_timeout_multiplier").strip().equals(software ? "10" : "1"), "Hardware timeout property not applied");
        require(shell("runtime-drawing", 30, true, "getprop debug.hwui.drawing_enabled").strip().equals("1"), "Drawing remains disabled");
    }

    private void waitBoot() throws Exception {
        note("Waiting for Android services");
        long until = System.currentTimeMillis() + (software ? 600_000 : 180_000);
        while (System.currentTimeMillis() < until) {
            try {
                if (shell("boot", 20, false, "getprop sys.boot_completed").strip().equals("1")
                        && shell("packages", 30, false, "pm path android").contains("package:")) return;
            } catch (IOException ignored) { }
            pause(5000);
        }
        throw new IOException("Android boot timeout");
    }

    private void preflight() throws Exception {
        waitBoot();
        dismissSystemAnr();
        shell("animations", 60, true, "settings put global window_animation_scale 0; settings put global transition_animation_scale 0; settings put global animator_duration_scale 0; settings put system screen_off_timeout 1800000");
        shell("wake", 60, true, "input keyevent 224; wm dismiss-keyguard");
        appearance("light", "412x891", "1.0");
        note("Installing app and instrumentation APKs");
        // Software first-boot work can keep installation busy beyond three minutes.
        // command() also clamps each install to the remaining total phase budget.
        int installSeconds = software ? Math.toIntExact(remaining()) : 180;
        adb("install-app", installSeconds, true, "install", "--no-streaming", "-r", evidence.resolve("app-debug.apk").toString());
        adb("install-tests", installSeconds, true, "install", "--no-streaming", "-r", "-t", evidence.resolve("app-debug-androidTest.apk").toString());
        instrument("preflight", "az.tabletennis.MatchDeviceTest", true, false, 412, 891);
        for (int id : List.of(501, 502)) {
            Properties proof = proof("preflight", id);
            require("true".equals(proof.getProperty("windowFocused")), "Installed match window has no focus");
            require((id == 501 ? "3" : "1").equals(proof.getProperty("leftScore")), "Installed left score mismatch");
            require((id == 501 ? "2" : "3").equals(proof.getProperty("rightScore")), "Installed right score mismatch");
            require(!proof.getProperty("updated", "").isBlank(), "Installed update label missing");
        }
    }

    private void appearance(String theme, String size, String font) throws Exception {
        shell("display-" + size, 90, true, "wm size " + size + "; settings put system font_scale " + font
                + "; settings put system accelerometer_rotation 0; settings put system user_rotation 0; cmd uimode night " + (theme.equals("dark") ? "yes" : "no"));
    }

    private void instrument(String prefix, String classes, boolean smoke, boolean reuse, int width, int height) throws Exception {
        note("Installed checks: " + prefix + " (" + width + "x" + height + ")");
        shell(prefix + "-stop", 60, true, "am force-stop az.tabletennis");
        String command = "am instrument -w -r -e class '" + classes + "' -e capturePrefix " + prefix
                + (smoke ? " -e preflight true" : "") + (reuse ? " -e reuseSaved true" : "")
                + " az.tabletennis.test/androidx.test.runner.AndroidJUnitRunner";
        String output = shell("tests-" + prefix, software ? 720 : 240, true, command);
        require(Pattern.compile("(?m)^OK \\([1-9][0-9]* tests?\\)").matcher(output).find(), "Installed tests did not pass with nonzero cases; see tests-" + prefix + ".log");
        Path captures = evidence.resolve("captures").resolve(prefix);
        Files.createDirectories(captures);
        adb("pull-" + prefix, 90, true, "pull", "/sdcard/Android/data/az.tabletennis/files", captures.toString());
        int count = 0;
        try (var paths = Files.walk(captures)) {
            for (Path png : paths.filter(p -> p.getFileName().toString().startsWith(prefix + "-") && p.toString().endsWith(".png")).toList()) {
                BufferedImage image = ImageIO.read(png.toFile());
                require(image != null && image.getWidth() == width && image.getHeight() == height, "Screenshot dimensions differ from requested " + width + "x" + height + ": " + png);
                Set<Integer> colors = new HashSet<>();
                for (int y = 0; y < height; y += 3) for (int x = 0; x < width; x += 3) colors.add(image.getRGB(x, y));
                require(colors.size() >= 16, "Blank screenshot: " + png);
                count++;
            }
        }
        require(count > 0, "No installed screenshots captured for " + prefix);
        result.setProperty(prefix + ".screenshots", Integer.toString(count));
        result.setProperty(prefix + ".tests", output.substring(output.lastIndexOf("OK (")).strip().split("\n")[0]);
    }

    private Properties proof(String prefix, int id) throws Exception {
        Path directory = evidence.resolve("captures").resolve(prefix);
        try (var files = Files.walk(directory)) {
            Path path = files.filter(p -> p.getFileName().toString().equals(prefix + "-" + id + ".properties")).findFirst()
                    .orElseThrow(() -> new IOException("Installed UI evidence missing for " + prefix + "/" + id));
            Properties value = new Properties();
            try (Reader input = Files.newBufferedReader(path)) { value.load(input); }
            return value;
        }
    }

    private void disconnect() throws Exception {
        shell("disconnect", 90, true, "svc wifi disable; svc data disable; cmd connectivity airplane-mode enable");
        for (int i = 0; i < 12; i++) {
            if (shell("offline-connectivity", 45, true, "dumpsys connectivity").contains("Active default network: none")) return;
            pause(3000);
        }
        throw new IOException("Android still has an active default network");
    }

    private boolean environmentFailure(Exception failure) {
        String message = failure.toString();
        if (message.contains("Blank screenshot") || message.contains("dimensions") || message.contains("boot timeout") || message.contains("Android ADB timeout")) return true;
        try {
            return Files.readString(evidence.resolve("tests-preflight.log")).contains("RootViewWithoutFocusException")
                    || shell("recovery-focus", 30, false, "dumpsys window windows").contains("Application Not Responding: system");
        } catch (Exception ignored) { return false; }
    }

    private void dismissSystemAnr() throws Exception {
        if (!shell("startup-windows", 45, true, "dumpsys window windows").contains("Application Not Responding: system")) return;
        shell("system-dialog-dump", 90, true, "uiautomator dump /sdcard/system-dialog.xml");
        String xml = shell("system-dialog", 45, true, "cat /sdcard/system-dialog.xml");
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var nodes = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))).getElementsByTagName("node");
        for (int i = 0; i < nodes.getLength(); i++) {
            var attributes = nodes.item(i).getAttributes();
            if ("android:id/aerr_wait".equals(attributes.getNamedItem("resource-id").getNodeValue())) {
                var bounds = Pattern.compile("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]").matcher(attributes.getNamedItem("bounds").getNodeValue());
                require(bounds.matches(), "System dialog bounds unavailable");
                int x = (Integer.parseInt(bounds.group(1)) + Integer.parseInt(bounds.group(3))) / 2;
                int y = (Integer.parseInt(bounds.group(2)) + Integer.parseInt(bounds.group(4))) / 2;
                shell("system-dialog-wait", 60, true, "input tap " + x + " " + y);
                return;
            }
        }
        throw new IOException("System ANR dialog has no Wait action");
    }

    private String shell(String name, int seconds, boolean required, String command) throws Exception {
        return adb(name, seconds, required, "shell", "-n", command);
    }

    private String adb(String name, int seconds, boolean required, String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of(adbBinary(), "-P", Integer.toString(adbPort)));
        if (!List.of("connect", "start-server").contains(args[0])) command.addAll(List.of("-s", serial));
        command.addAll(List.of(args));
        return command(name, seconds, required, command.toArray(String[]::new));
    }

    private String adbBinary() { return sdk.resolve("platform-tools/adb").toString(); }
    private void run(String name, int seconds, String... command) throws Exception { command(name, seconds, true, command); }

    private String command(String name, int seconds, boolean required, String... command) throws Exception {
        Path log = evidence.resolve(name + ".log");
        Process process = launch(log, command);
        if (!process.waitFor(Math.min(seconds, remaining()), TimeUnit.SECONDS)) {
            kill(process);
            throw new IOException(name + " timeout; see " + log);
        }
        if (required && process.exitValue() != 0) throw new IOException(name + " exited " + process.exitValue() + "; see " + log);
        return Files.readString(log);
    }

    private synchronized Process launch(Path log, String... command) throws IOException {
        if (cleaned) throw new IOException("Run is shutting down");
        ProcessBuilder builder = new ProcessBuilder(command).directory(repo.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().clear();
        builder.environment().putAll(env);
        Process process = builder.start();
        children.add(process);
        process.getOutputStream().close();
        return process;
    }

    private long remaining() throws IOException {
        long seconds = (deadline - System.currentTimeMillis()) / 1000;
        if (seconds <= 0) throw new IOException("Phase time budget exhausted");
        return seconds;
    }

    private void pause(int milliseconds) throws Exception { remaining(); Thread.sleep(milliseconds); }
    private void note(String message) { System.out.println(Instant.now() + " " + message); }
    private void require(boolean condition, String message) throws IOException { if (!condition) throw new IOException(message); }
    private String groovy(String value) { return value.replace("\\", "\\\\").replace("'", "\\'"); }

    private String digest(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) { byte[] buffer = new byte[131072]; int count; while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count); }
        return HexFormat.of().formatHex(digest.digest());
    }

    private int freePort() throws IOException { try (ServerSocket socket = new ServerSocket(0)) { return socket.getLocalPort(); } }

    private Path imageDirectory() throws Exception {
        return cache.resolve("images/" + tools.getProperty("image.sha256").substring(0, 12) + "/x86_64");
    }

    private Path sdkDirectory() throws Exception {
        String version = digest(repo.resolve("scripts/android/tools.properties")).substring(0, 12);
        Path pointer = cache.resolve("native-sdk-" + version + ".path");
        try (FileChannel channel = FileChannel.open(cache.resolve("sdk.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = acquire(channel)) {
            if (Files.isRegularFile(pointer)) {
                Path existing = Path.of(Files.readString(pointer));
                if (Files.isDirectory(existing) && !Files.isSymbolicLink(existing) && Files.getOwner(existing).equals(Files.getOwner(cache))
                        && Files.isRegularFile(existing.resolve(".repository")) && Files.readString(existing.resolve(".repository")).equals(repo.toString())) return existing;
            }
            Path nativeParent = Path.of("/tmp");
            Path directory;
            if (Files.getFileStore(nativeParent).getUsableSpace() >= 1800L * 1024 * 1024) {
                directory = Files.createTempDirectory(nativeParent, "tabletennisaz-sdk-");
                Files.writeString(directory.resolve(".repository"), repo.toString());
            } else {
                directory = Files.createDirectories(cache.resolve("sdk-" + version));
                Files.writeString(directory.resolve(".repository"), repo.toString());
            }
            Files.writeString(pointer, directory.toString());
            return directory;
        }
    }

    private FileLock acquire(FileChannel channel) throws Exception {
        while (true) { FileLock lock = channel.tryLock(); if (lock != null) return lock; pause(1000); }
    }

    private int reservePort() throws Exception {
        Path locks = Files.createDirectories(cache.resolve("ports"));
        for (int port = 5560; port < 5680; port += 2) {
            FileChannel channel = FileChannel.open(locks.resolve(port + ".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = channel.tryLock();
            if (lock != null) {
                try (ServerSocket console = new ServerSocket(port); ServerSocket transport = new ServerSocket(port + 1)) {
                    portChannel = channel; portLock = lock; return port;
                } catch (IOException busy) { lock.close(); }
            }
            channel.close();
        }
        throw new IOException("No free isolated emulator port pair");
    }

    private void diagnose() {
        if (serial == null) return;
        long original = deadline;
        if (original <= System.currentTimeMillis()) return;
        deadline = Math.min(original, System.currentTimeMillis() + 60_000);
        try { shell("failure-window", 20, false, "dumpsys window displays"); } catch (Exception ignored) { }
        try { shell("failure-logcat", 20, false, "logcat -d -t 400 -s AndroidRuntime ActivityManager TestRunner Watchdog"); } catch (Exception ignored) { }
        deadline = original;
    }

    private static void kill(Process process) {
        process.descendants().forEach(child -> child.destroyForcibly());
        process.destroyForcibly();
        try { process.waitFor(5, TimeUnit.SECONDS); } catch (InterruptedException stopped) { Thread.currentThread().interrupt(); }
    }

    private synchronized void cleanup() {
        if (cleaned) return;
        cleaned = true;
        if (result.getProperty("status") == null) {
            result.setProperty("status", "failed");
            result.setProperty("failure", "Interrupted before completion");
            result.setProperty("phase", phase);
        }
        for (Process child : List.copyOf(children)) if (child.isAlive()) kill(child);
        if (adbPort != 0) try {
            Process server = new ProcessBuilder(adbBinary(), "-P", Integer.toString(adbPort), "kill-server").redirectErrorStream(true)
                    .redirectOutput(evidence.resolve("adb-stop.log").toFile()).start();
            if (!server.waitFor(5, TimeUnit.SECONDS)) kill(server);
        } catch (Exception ignored) { }
        try {
            if (build != null) {
                Path reports = build.resolve("app/test-results/testDebugUnitTest");
                if (Files.isDirectory(reports)) try (var files = Files.list(reports)) {
                    Path destination = Files.createDirectories(evidence.resolve("host-results"));
                    for (Path file : files.filter(p -> p.toString().endsWith(".xml")).toList()) Files.copy(file, destination.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                }
                remove(build);
            }
            if (retain) result.setProperty("retainedDevice", work.toString());
            else remove(work);
            for (Path temporary : List.copyOf(temporaryDirectories)) remove(temporary);
            if (portLock != null) portLock.close();
            if (portChannel != null) portChannel.close();
            result.setProperty("cleanup", retain ? "device retained explicitly" : "complete");
        } catch (Exception failure) { result.setProperty("cleanup", failure.toString()); result.setProperty("status", "failed"); }
        saveResult();
    }

    private void saveResult() {
        result.setProperty("finished", Instant.now().toString());
        try (Writer output = Files.newBufferedWriter(evidence.resolve("result.properties"))) { result.store(output, "Android verification"); }
        catch (IOException failure) { System.err.println("Cannot write result: " + failure); }
    }

    private static void remove(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        try (var files = Files.walk(path)) { for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file); }
    }
}
