import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
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
    private static final List<String> LIVE_TESTS = List.of("az.tabletennis.LiveRankingsTest",
            "az.tabletennis.LiveExperienceTest", "az.tabletennis.LiveDoublesTest");
    private static final String DOUBLES_PROFILE_TEST = "az.tabletennis.DoublesProfileDeviceTest";
    private static final String UPGRADE_TEST = "az.tabletennis.SavedHistoryUpgradeDeviceTest";
    private static final String APPLICATION_ID = "az.tabletennis";
    private static final String UPGRADE_BEFORE = "upgrade-before", UPGRADE_AFTER = "upgrade-after";
    private static final Pattern APK_PACKAGE = Pattern.compile("(?m)^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'");
    private static final Pattern APK_SIGNER = Pattern.compile("(?m)^Signer #\\d+ certificate SHA-256 digest: ([0-9a-f]{64})$");
    private static final List<String> DOUBLES_HISTORY_CAPTURES = List.of("rank", "points");
    private static final Map<String, List<String>> FIXTURE_CAPTURES = Map.of(
            "az.tabletennis.MatchDeviceTest", List.of("501", "502"),
            "az.tabletennis.ProfileDeviceTest", List.of("profile", "history"),
            "az.tabletennis.RatingHistoryDeviceTest", List.of("chart", "observations"),
            "az.tabletennis.ResultsDeviceTest", List.of("results", "long-names", "long-names-second-side", "empty"),
            DOUBLES_PROFILE_TEST, List.of("profile", "rank", "points", "matches", "long-names"),
            "az.tabletennis.NativeAcceptanceDeviceTest", List.of("players", "player-profile", "singles-history", "doubles",
                    "doubles-profile", "doubles-rank-history", "drawer", "results", "match", "empty-results"));
    private static final String CAPTURE_FILES = "/sdcard/Android/data/az.tabletennis/files";
    private static final List<String> MATRIX_CONFIGURATIONS = List.of("light", "dark-offline", "narrow-large", "landscape-large");
    private final Path repo, source, cache, evidence, work, sdk;
    private final Map<String, String> env = new HashMap<>(System.getenv());
    private final Properties tools = new Properties(), result = new Properties();
    private final List<Process> children = Collections.synchronizedList(new ArrayList<>());
    private final List<Path> temporaryDirectories = Collections.synchronizedList(new ArrayList<>());
    private final Path appApk, testApk;
    private final String variant;
    private Path releaseCandidate;
    private String expectedReleaseSha256;
    private Path previousRelease;
    private String previousReleaseTag;
    private Path build;
    private Process emulator;
    private boolean retain, preflightOnly, software, live, requireKvm, deviceOnly, focused, failAfterHistoryCheckpoint;
    private volatile boolean cleaned;
    private String tests = "az.tabletennis.MatchDeviceTest", serial;
    private int adbPort;
    private FileChannel portChannel;
    private FileLock portLock;
    private volatile long deadline;
    private volatile String phase = "setup";

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
                try { Thread.sleep(1000); }
                catch (InterruptedException stopped) { return; }
                synchronized (runner) {
                    if (runner.cleaned) return;
                    try { runner.remaining(); }
                    catch (IOException expired) {
                        runner.result.setProperty("status", "failed");
                        runner.result.setProperty("failure", "Phase time budget exhausted");
                        runner.result.setProperty("phase", runner.phase);
                        break;
                    }
                }
            }
            System.err.println("Time budget exhausted; stopping owned processes and saving evidence");
            System.exit(124);
        });
        int exit = 0;
        try {
            runner.execute();
            synchronized (runner) {
                runner.remaining();
                runner.result.putIfAbsent("status", "passed");
                runner.cleanup();
            }
        }
        catch (Exception failure) {
            exit = 1;
            runner.result.setProperty("status", "failed");
            runner.result.putIfAbsent("failure", failure.toString());
            System.err.println("FAILED during " + runner.phase + ": " + runner.result.getProperty("failure"));
        } finally { runner.cleanup(); }
        System.exit(runner.result.getProperty("status").equals("passed") ? exit : 1);
    }

    private AndroidVerify(String[] args) throws Exception {
        repo = Path.of(args[0]).toRealPath();
        source = Path.of(env.getOrDefault("ANDROID_VERIFY_SOURCE", repo.toString())).toRealPath();
        cache = Path.of(System.getenv("ANDROID_VERIFY_CACHE")).toAbsolutePath();
        evidence = Path.of(System.getenv("ANDROID_VERIFY_EVIDENCE")).toRealPath();
        boolean selectedTests = false;
        for (int i = 1; i < args.length; i++) switch (args[i]) {
            case "--retain-device" -> retain = true;
            case "--preflight-only" -> preflightOnly = true;
            case "--software" -> software = true;
            case "--require-kvm" -> requireKvm = true;
            case "--device-only" -> deviceOnly = true;
            case "--focused" -> focused = true;
            case "--fail-after-history-checkpoint" -> failAfterHistoryCheckpoint = true;
            case "--live" -> live = true;
            case "--tests" -> { tests = args[++i]; selectedTests = true; }
            case "--release-candidate" -> {
                if (args.length - i < 3)
                    throw new IllegalArgumentException("--release-candidate requires an APK path and its SHA-256");
                releaseCandidate = Path.of(args[++i]);
                expectedReleaseSha256 = args[++i];
            }
            case "--upgrade-from" -> {
                if (args.length - i < 3)
                    throw new IllegalArgumentException("--upgrade-from requires the previous official APK path and release tag");
                previousRelease = Path.of(args[++i]);
                previousReleaseTag = args[++i];
            }
            default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
        }
        if (!tests.matches("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)+"))
            throw new IllegalArgumentException("--tests requires one fully qualified test class; run separate fixtures separately");
        if (live && (preflightOnly || selectedTests))
            throw new IllegalArgumentException("--live runs the existing live journeys after preflight; use it separately from --tests and --preflight-only");
        if (requireKvm && software)
            throw new IllegalArgumentException("Use --require-kvm or --software");
        if (previousRelease != null) {
            if (releaseCandidate == null || !requireKvm || focused || live || preflightOnly || selectedTests
                    || failAfterHistoryCheckpoint || !previousReleaseTag.matches("v[0-9]+\\.[0-9]+\\.[0-9]+"))
                throw new IllegalArgumentException("--upgrade-from requires --require-kvm, a signed --release-candidate and an official release tag; it selects its own journey");
            tests = UPGRADE_TEST;
        }
        if (focused && (preflightOnly || live || !FIXTURE_CAPTURES.containsKey(tests)))
            throw new IllegalArgumentException("--focused requires a supported saved-fixture journey after preflight");
        if (releaseCandidate != null && (preflightOnly || live || (previousRelease == null && !FIXTURE_CAPTURES.containsKey(tests))
                || !expectedReleaseSha256.matches("[0-9a-f]{64}")))
            throw new IllegalArgumentException("--release-candidate requires a supported saved-fixture journey and a lowercase SHA-256");
        if (failAfterHistoryCheckpoint && (!tests.equals(DOUBLES_PROFILE_TEST) || preflightOnly))
            throw new IllegalArgumentException("--fail-after-history-checkpoint requires the doubles profile journey");
        software |= !Files.isReadable(Path.of("/dev/kvm")) || !Files.isWritable(Path.of("/dev/kvm"));
        deadline = Long.parseLong(System.getenv("ANDROID_VERIFY_STARTED")) * 1000 + Long.parseLong(System.getenv("ANDROID_VERIFY_PREFLIGHT_SECONDS")) * 1000;
        sdk = sdkDirectory();
        try (Reader input = Files.newBufferedReader(repo.resolve("scripts/android/tools.properties"))) { tools.load(input); }
        Files.copy(repo.resolve("scripts/android/tools.properties"), evidence.resolve("tools.properties"));
        Files.copy(repo.resolve("scripts/android/limits.properties"), evidence.resolve("limits.properties"));
        // Allocate disposable state only after fallible cache/configuration setup succeeds.
        work = Files.createTempDirectory(evidence.getParent(), "device-");
        variant = releaseCandidate == null ? "debug" : "release";
        appApk = evidence.resolve("app-" + variant + ".apk");
        testApk = evidence.resolve("app-" + variant + "-androidTest.apk");
        env.put("ANDROID_HOME", sdk.toString());
        env.put("ANDROID_SDK_ROOT", sdk.toString());
        env.put("ANDROID_USER_HOME", work.resolve("android-user").toString());
        env.put("ANDROID_EMULATOR_HOME", work.resolve("android-user").toString());
        env.put("ANDROID_AVD_HOME", work.resolve("avds").toString());
        env.remove("ANDROID_SERIAL");
        result.setProperty("mode", software ? "software" : "accelerated");
        result.setProperty("apk.variant", variant);
        result.setProperty("api", "36");
        result.setProperty("tests", live ? String.join(",", LIVE_TESTS) : tests);
        result.setProperty("preflight", "not-run");
        initializeConfiguration("preflight");
        if (previousRelease != null) {
            result.setProperty("upgrade", "not-run");
            result.setProperty("previous.tag", previousReleaseTag);
            for (String prefix : List.of(UPGRADE_BEFORE, UPGRADE_AFTER)) {
                initializeConfiguration(prefix);
                result.setProperty(prefix + ".install", "not-run");
                result.setProperty(prefix + ".expectedScreenshots", String.join(",", requiredScreenshots(prefix, tests)));
            }
            result.setProperty(UPGRADE_AFTER + ".offline", "not-run");
        } else if (focused) {
            result.setProperty("focused", "not-run");
            initializeConfiguration("light");
            result.setProperty("light.checkpoints", "not-run");
        } else if (!preflightOnly && !live) {
            result.setProperty("matrix", "not-run");
            MATRIX_CONFIGURATIONS.forEach(this::initializeConfiguration);
            if (releaseCandidate != null) {
                result.setProperty("light.checkpoints", "not-run");
                for (String configuration : MATRIX_CONFIGURATIONS)
                    result.setProperty(configuration + ".expectedScreenshots", String.join(",", requiredScreenshots(configuration, tests)));
            } else if (tests.equals(DOUBLES_PROFILE_TEST)) {
                result.setProperty("light.historyCheckpoint", "not-run");
            }
        }
        result.setProperty("started", Instant.now().toString());
        result.setProperty("sdkCache", sdk.toString());
    }

    private void execute() throws Exception {
        if (releaseCandidate != null) {
            phase = "candidate-checksum";
            result.setProperty("source.commit", command("source-revision", 15, true, "git", "rev-parse", "HEAD").strip());
            result.setProperty("apk.expectedSha256", expectedReleaseSha256);
            Files.copy(releaseCandidate, appApk);
            String actual = digest(appApk);
            result.setProperty("apk.sha256", actual);
            require(actual.equals(expectedReleaseSha256), "Signed release candidate SHA-256 mismatch");
            note("Verified signed release candidate SHA-256: " + actual);
        }
        Path previousApk = null;
        if (previousRelease != null) {
            phase = "previous-apk-checksum";
            previousApk = Files.copy(previousRelease, evidence.resolve("previous-official.apk"));
            result.setProperty("previous.sha256", digest(previousApk));
        }
        if (requireKvm && software) {
            phase = "acceleration";
            throw new IOException("KVM is required; /dev/kvm must be readable and writable");
        }
        note("Preparing pinned tools (" + (software ? "software emulation; " + Long.parseLong(env.get("ANDROID_VERIFY_PREFLIGHT_SECONDS")) / 60 + "-minute preflight limit" : "hardware acceleration") + ")");
        if (Files.getFileStore(cache).getUsableSpace() < 12L * 1024 * 1024 * 1024 || Files.getFileStore(work).getUsableSpace() < 12L * 1024 * 1024 * 1024)
            throw new IOException("Tool/device filesystem needs 12 GiB free. Set ANDROID_VERIFY_CACHE and ANDROID_VERIFY_OUTPUT to a larger filesystem.");
        for (String tool : List.of("platform", "build-tools", "platform-tools", "emulator", "image")) install(tool);
        if (!software) {
            phase = "acceleration";
            String acceleration = command("acceleration", 30, requireKvm, sdk.resolve("emulator/emulator").toString(), "-accel-check");
            software = !acceleration.contains("installed and usable");
            result.setProperty("mode", software ? "software" : "accelerated");
            if (requireKvm) {
                require(!software && acceleration.contains("KVM"), "KVM is required; see acceleration.log");
                result.setProperty("kvm", "confirmed");
                note("KVM acceleration confirmed by the Android emulator");
            }
        }
        phase = "build";
        prepareApks();
        if (previousApk != null) {
            verifyUpgrade(previousApk);
            return;
        }
        phase = "preflight";
        result.setProperty("preflight", "running");
        note("Preflight started");
        try { startDevice(); preflight(); }
        catch (Exception first) {
            if (remaining() < 120 || !environmentFailure(first)) throw first;
            note("One automatic environment recovery; the original preflight deadline remains in force");
            result.setProperty("recovery", "1");
            diagnose(deadline);
            Path previous = Files.createDirectories(evidence.resolve("first-attempt"));
            try (var logs = Files.list(evidence)) {
                for (Path log : logs.filter(p -> p.toString().endsWith(".log")).toList())
                    Files.copy(log, previous.resolve(log.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
            for (String directory : List.of("captures", "failure-captures")) {
                Path captures = evidence.resolve(directory);
                if (Files.isDirectory(captures)) Files.move(captures, previous.resolve(directory));
            }
            Properties firstResult = new Properties();
            for (String key : result.stringPropertyNames()) {
                if (!key.startsWith("preflight.")) continue;
                String value = (String) result.remove(key);
                firstResult.setProperty(key, value.equals("running") ? "failed" : value);
            }
            firstResult.setProperty("failure", first.toString());
            try (Writer output = Files.newBufferedWriter(previous.resolve("result.properties"))) {
                firstResult.store(output, "First preflight attempt");
            }
            initializeConfiguration("preflight");
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
        beginFullPhase(live ? "live-source" : focused ? "focused-journey" : "device-matrix");
        if (live) {
            result.setProperty("liveSource", "failed");
            connect();
            for (String test : LIVE_TESTS) {
                String prefix = "live-" + test.substring(test.lastIndexOf('.') + 1);
                note("Live website journey: " + test);
                runTests(prefix, test, false, false, Math.toIntExact(remaining()), List.of());
            }
            result.setProperty("liveSource", "passed");
            return;
        }
        if (focused) {
            result.setProperty("focused", "running");
            note("Focused installed fixture journey started: " + tests);
            instrument("light", tests, false, false, 412, 891);
            result.setProperty("focused", "passed");
            note("Focused installed fixture journey passed");
            return;
        }
        result.setProperty("matrix", "running");
        note("Installed fixture matrix started: " + tests);
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
        if (deviceOnly) return;
        // Software emulation and Robolectric together can exhaust an 8 GiB host.
        kill(emulator);
        phase = "host-checks";
        note("Owned emulator stopped; running host integration tests and lint within the same full-phase deadline");
        run("host-checks", Math.toIntExact(remaining()), source.resolve("gradlew").toString(), "--no-daemon", "--project-cache-dir", build.resolve("gradle-cache").toString(),
                "-I", work.resolve("build-paths.gradle").toString(), ":app:testDebugUnitTest", ":app:lintDebug");
        result.setProperty("hostChecks", "passed");
    }

    private void prepareApks() throws Exception {
        Path buildParent = Path.of(env.getOrDefault("ANDROID_VERIFY_BUILD_ROOT", "/tmp")).toAbsolutePath();
        if (Files.getFileStore(buildParent).getUsableSpace() < 1024L * 1024 * 1024) buildParent = work;
        build = Files.createTempDirectory(buildParent, "tabletennisaz-build-");
        Path init = work.resolve("build-paths.gradle");
        Path hostScratch = Files.createDirectories(work.resolve("host-tmp"));
        Files.writeString(init, "allprojects { layout.buildDirectory.set(new File('" + groovy(build.toString()) + "', project.name)); "
                + "tasks.withType(org.gradle.api.tasks.testing.Test).configureEach { systemProperty 'java.io.tmpdir', '"
                + groovy(hostScratch.toString()) + "' } }\n");
        List<String> gradle = new ArrayList<>(List.of(source.resolve("gradlew").toString(), "--no-daemon",
                "--project-cache-dir", build.resolve("gradle-cache").toString(), "-I", init.toString()));
        if (releaseCandidate == null) {
            note("Building both debug APKs in isolated output");
            gradle.addAll(List.of(":app:assembleDebug", ":app:assembleDebugAndroidTest"));
        } else {
            note("Building instrumentation for the release variant in isolated output");
            gradle.addAll(List.of(":app:assembleReleaseAndroidTest", "-PandroidTestBuildType=release"));
        }
        run("build", 600, gradle.toArray(String[]::new));
        if (releaseCandidate == null) {
            Files.copy(build.resolve("app/outputs/apk/debug/app-debug.apk"), appApk);
            result.setProperty("apk.sha256", digest(appApk));
        }
        Files.copy(build.resolve("app/outputs/apk/androidTest").resolve(variant).resolve(testApk.getFileName()), testApk);
        result.setProperty("testApk.sha256", digest(testApk));
    }

    private void initializeConfiguration(String prefix) {
        result.setProperty(prefix + ".instrumentation", "not-run");
        result.setProperty(prefix + ".screenshotCollection", "not-run");
    }

    private synchronized void beginFullPhase(String name) throws IOException {
        remaining();
        phase = name;
        deadline = System.currentTimeMillis() + Long.parseLong(env.get("ANDROID_VERIFY_MATRIX_SECONDS")) * 1000;
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
        String cores = "2";
        result.setProperty("emulatorCores", cores);
        Path avds = Files.createDirectories(work.resolve("avds"));
        Path avd = Files.createDirectories(avds.resolve("verification.avd"));
        Path image = imageDirectory();
        prepareUserdata(avd, image);
        Files.writeString(avds.resolve("verification.ini"), "avd.ini.encoding=UTF-8\npath=" + avd + "\n");
        Files.writeString(avd.resolve("config.ini"), """
                avd.ini.encoding=UTF-8
                abi.type=x86_64
                hw.cpu.arch=x86_64
                hw.cpu.ncore=%s
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
                """.formatted(cores) + "image.sysdir.1=" + image + "/\n");
        int port = reservePort();
        adbPort = freePort();
        env.put("ANDROID_ADB_SERVER_PORT", Integer.toString(adbPort));
        env.put("ADB_SERVER_SOCKET", "tcp:localhost:" + adbPort);
        run("adb-start", 30, adbBinary(), "-P", Integer.toString(adbPort), "start-server");
        serial = "emulator-" + port;
        note("Starting isolated API 36 emulator");
        emulator = launch(evidence.resolve("emulator.log"), sdk.resolve("emulator/emulator").toString(), "-avd", "verification",
                "-port", Integer.toString(port), "-no-window", "-no-audio", "-no-snapshot", "-no-boot-anim", "-no-metrics", "-show-kernel",
                "-gpu", "swiftshader", "-accel", software ? "off" : "on", "-memory", "2560", "-cores", cores);
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
        prepareDeviceDisplay();
        note("Installing app and instrumentation APKs");
        // Software first-boot work can keep installation busy beyond three minutes.
        // command() also clamps each install to the remaining total phase budget.
        int installSeconds = software ? Math.toIntExact(remaining()) : 180;
        adb("install-app", installSeconds, true, "install", "--no-streaming", "-r", appApk.toString());
        adb("install-tests", installSeconds, true, "install", "--no-streaming", "-r", "-t", testApk.toString());
        if (software) {
            note("Compiling app and test bytecode before installed checks (within the preflight budget)");
            for (String target : List.of("az.tabletennis", "az.tabletennis.test"))
                shell("compile-" + target, Math.toIntExact(remaining()), true, "cmd package compile --primary-dex -m speed " + target);
            result.setProperty("softwareCompilation", "passed");
        }
        instrument("preflight", "az.tabletennis.MatchDeviceTest", true, false, 412, 891);
        for (int id : List.of(501, 502)) {
            Properties proof = proof("preflight", id);
            require("true".equals(proof.getProperty("windowFocused")), "Installed match window has no focus");
            require((id == 501 ? "3" : "1").equals(proof.getProperty("leftScore")), "Installed left score mismatch");
            require((id == 501 ? "2" : "3").equals(proof.getProperty("rightScore")), "Installed right score mismatch");
            require(!proof.getProperty("updated", "").isBlank(), "Installed update label missing");
        }
    }

    private void prepareDeviceDisplay() throws Exception {
        waitBoot();
        dismissSystemAnr();
        // Android can report boot completion while the settings service still contends with startup work.
        shell("animations", software ? 180 : 60, true, "settings put global window_animation_scale 0; settings put global transition_animation_scale 0; settings put global animator_duration_scale 0; settings put system screen_off_timeout 1800000");
        if (software) {
            // Software input injection can hold Espresso's ordinary tap for seconds. Keep it
            // below the disposable guest's long-press threshold, including tooltip buttons.
            shell("software-touch", 60, true, "settings put secure long_press_timeout 10000");
            require(shell("software-touch-verified", 60, true, "settings get secure long_press_timeout").strip().equals("10000"),
                    "Software long-press threshold was not applied");
            result.setProperty("softwareLongPressMillis", "10000");
        }
        shell("wake", 60, true, "input keyevent 224; wm dismiss-keyguard");
        appearance("light", "412x891", "1.0");
    }

    private void verifyUpgrade(Path previousApk) throws Exception {
        phase = "upgrade-apk-identities";
        result.setProperty("upgrade", "running");
        ApkIdentity previous = readApkIdentity("previous", previousApk);
        ApkIdentity candidate = readApkIdentity("apk", appApk);
        require(previousReleaseTag.equals("v" + previous.versionName()), "Previous APK version does not match its official release tag");
        require(candidate.versionCode() >= previous.versionCode(), "Candidate APK version code is below the previous official APK");
        require(candidate.signers().equals(previous.signers()), "Candidate signing identity differs from the official APK");

        phase = "upgrade-device";
        startDevice();
        prepareDeviceDisplay();
        beginFullPhase(UPGRADE_BEFORE);
        note("Installing the official APK from " + previousReleaseTag);
        installUpgradeApk(UPGRADE_BEFORE, previousApk, result.getProperty("previous.sha256"), false);
        adb("install-tests", 180, true, "install", "--no-streaming", "-t", testApk.toString());
        disconnect();
        instrument(UPGRADE_BEFORE, UPGRADE_TEST, false, false, 412, 891);

        phase = UPGRADE_AFTER;
        note("Updating the installed app in place to the verified signed candidate");
        installUpgradeApk(UPGRADE_AFTER, appApk, expectedReleaseSha256, true);
        disconnect();
        result.setProperty(UPGRADE_AFTER + ".offline", "confirmed");
        instrument(UPGRADE_AFTER, UPGRADE_TEST, false, true, 412, 891);
        result.setProperty("upgrade", "passed");
        note("The official and updated APKs showed the same saved rating and ranking observations");
    }

    private record ApkIdentity(String versionName, long versionCode, Set<String> signers) {}

    private ApkIdentity readApkIdentity(String prefix, Path apk) throws Exception {
        Path buildTools = sdk.resolve(tools.getProperty("build-tools.destination"));
        String badging = command(prefix + "-badging", 30, true, buildTools.resolve("aapt").toString(), "dump", "badging", apk.toString());
        var pkg = APK_PACKAGE.matcher(badging);
        require(pkg.find(), "Missing package identity for " + apk.getFileName());
        result.setProperty(prefix + ".applicationId", pkg.group(1));
        result.setProperty(prefix + ".versionCode", pkg.group(2));
        result.setProperty(prefix + ".versionName", pkg.group(3));
        require(pkg.group(1).equals(APPLICATION_ID), "Unexpected application ID in " + apk.getFileName());
        String signatures = command(prefix + "-signature", 60, true, buildTools.resolve("apksigner").toString(), "verify", "--print-certs", apk.toString());
        Set<String> signers = new TreeSet<>();
        APK_SIGNER.matcher(signatures).results().forEach(signer -> signers.add(signer.group(1)));
        require(!signers.isEmpty(), "No verified signer for " + apk.getFileName());
        result.setProperty(prefix + ".signerSha256", String.join(",", signers));
        return new ApkIdentity(pkg.group(3), Long.parseLong(pkg.group(2)), signers);
    }

    private void installUpgradeApk(String prefix, Path apk, String expectedChecksum, boolean replace) throws Exception {
        result.setProperty(prefix + ".install", "running");
        List<String> args = new ArrayList<>(List.of("install", "--no-streaming"));
        if (replace) args.add("-r");
        args.add(apk.toString());
        adb(prefix + "-install", 180, true, args.toArray(String[]::new));
        String path = shell(prefix + "-package-path", 30, true, "pm path --user 0 " + APPLICATION_ID).strip();
        require(path.matches("package:/data/app/[A-Za-z0-9_./~+=-]+/base\\.apk"), "Unexpected installed APK path: " + path);
        String installed = shell(prefix + "-installed-checksum", 30, true, "su 0 sha256sum " + path.substring("package:".length())).split("\\s+", 2)[0];
        result.setProperty(prefix + ".installedSha256", installed);
        require(installed.equals(expectedChecksum), "Installed APK SHA-256 mismatch after " + prefix);
        result.setProperty(prefix + ".install", "passed");
    }

    private void appearance(String theme, String size, String font) throws Exception {
        note("Applying Android display settings: " + theme + ", " + size + ", font scale " + font);
        shell("display-" + size, 90, true, "wm size " + size + "; settings put system font_scale " + font
                + "; settings put system accelerometer_rotation 0; settings put system user_rotation 0; cmd uimode night " + (theme.equals("dark") ? "yes" : "no"));
    }

    private void instrument(String prefix, String classes, boolean smoke, boolean reuse, int width, int height) throws Exception {
        note("Installed configuration started: " + prefix + " (" + width + "x" + height + ")");
        result.setProperty(prefix + ".display", width + "x" + height);
        result.setProperty(prefix + ".instrumentation", "running");
        List<String> screenshots = requiredScreenshots(prefix, classes);
        runTests(prefix, classes, smoke, reuse, software ? 720 : 240, prefix.equals("light") ? screenshots : List.of());
        result.setProperty(prefix + ".screenshotCollection", "running");
        note("Screenshot collection started: " + prefix);
        Path captures = evidence.resolve("captures").resolve(prefix);
        Files.createDirectories(captures);
        adb("pull-" + prefix, 90, true, "pull", CAPTURE_FILES, captures.toString());
        Map<String, Path> validated = new HashMap<>();
        try (var paths = Files.walk(captures)) {
            for (Path png : paths.filter(p -> p.getFileName().toString().startsWith(prefix + "-") && p.toString().endsWith(".png")).toList()) {
                validateScreenshot(png, width, height);
                validated.put(png.getFileName().toString(), png);
                if (releaseCandidate != null && !prefix.equals("preflight"))
                    result.setProperty(prefix + ".screenshotSha256." + png.getFileName(), digest(png));
                result.setProperty(prefix + ".screenshots", Integer.toString(validated.size()));
            }
        }
        require(!validated.isEmpty(), "No installed screenshots captured for " + prefix);
        for (String screenshot : screenshots)
            require(validated.containsKey(screenshot), "Missing required screenshot: " + screenshot);
        if (classes.equals(DOUBLES_PROFILE_TEST)) {
            Path points = validated.get(prefix + "-points.png");
            Path rank = validated.get(prefix + "-rank.png");
            require(Files.mismatch(points, rank) != -1, "Rating and ranking history screenshots are identical for " + prefix);
            Path profile = validated.get(prefix + "-profile.png");
            if (profile != null) {
                require(Files.mismatch(profile, points) != -1 && Files.mismatch(profile, rank) != -1,
                        "History screenshot duplicates the profile screenshot for " + prefix);
            }
        }
        result.setProperty(prefix + ".screenshotCollection", "passed");
        note("Screenshot collection passed: " + prefix + " (" + validated.size() + " validated PNGs)");
    }

    private List<String> requiredScreenshots(String prefix, String classes) {
        List<String> captures;
        if (classes.equals(UPGRADE_TEST))
            captures = List.of("rating", "ranking");
        else if (MATRIX_CONFIGURATIONS.contains(prefix) && (focused || releaseCandidate != null))
            captures = FIXTURE_CAPTURES.get(classes);
        else
            captures = classes.equals(DOUBLES_PROFILE_TEST) ? DOUBLES_HISTORY_CAPTURES : List.of();
        return captures.stream().map(name -> prefix + "-" + name + ".png").toList();
    }

    private void validateScreenshot(Path png, int width, int height) throws IOException {
        BufferedImage image = ImageIO.read(png.toFile());
        require(image != null && image.getWidth() == width && image.getHeight() == height,
                "Screenshot dimensions differ from requested " + width + "x" + height + ": " + png);
        Set<Integer> colors = new HashSet<>();
        for (int y = 0; y < height; y += 3) for (int x = 0; x < width; x += 3) colors.add(image.getRGB(x, y));
        require(colors.size() >= 16, "Blank screenshot: " + png);
    }

    private void runTests(String prefix, String classes, boolean smoke, boolean reuse, int seconds, List<String> checkpoints) throws Exception {
        shell(prefix + "-stop", 60, true, "am force-stop az.tabletennis");
        // The owned userdebug emulator permits root to start instrumentation with a separate signer.
        String command = (releaseCandidate == null ? "" : "su 0 ")
                + "am instrument -w -r -e class '" + classes + "' -e capturePrefix " + prefix
                + (smoke ? " -e preflight true" : "") + (reuse ? " -e reuseSaved true" : "");
        String runner = " az.tabletennis.test/androidx.test.runner.AndroidJUnitRunner";
        String name = "tests-" + prefix;
        Set<String> missing = new LinkedHashSet<>(checkpoints);
        String output = checkpoints.isEmpty() ? shell(name, seconds, true, command + runner)
                : runWithCheckpoints(name, seconds, command, runner, missing);
        require(Pattern.compile("(?m)^OK \\([1-9][0-9]* tests?\\)").matcher(output).find(), "Installed tests did not pass with nonzero cases; see tests-" + prefix + ".log");
        result.setProperty(prefix + ".tests", output.substring(output.lastIndexOf("OK (")).strip().split("\n")[0]);
        result.setProperty(prefix + ".instrumentation", "passed");
        note("Instrumentation passed: " + prefix);
        require(missing.isEmpty(), "Missing required checkpoint screenshots: " + missing);
    }

    private String runWithCheckpoints(String name, int seconds, String command, String runner, Set<String> missing) throws Exception {
        String checkpointResult = focused || releaseCandidate != null ? "light.checkpoints" : "light.historyCheckpoint";
        result.setProperty(checkpointResult, "running");
        result.setProperty("light.screenshotCollection", "running");
        note("Screenshot collection started: light checkpoints");
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(250);
            int port = server.getLocalPort();
            adb("checkpoint-reverse", 15, true, "reverse", "tcp:" + port, "tcp:" + port);
            command += " -e checkpointPort " + port + " -e checkpointScreenshots " + String.join(",", missing);
            if (failAfterHistoryCheckpoint) command += " -e failAfterHistoryCheckpoint true";
            long until = Math.min(deadline, System.currentTimeMillis() + seconds * 1000L);
            Process tests = launch(evidence.resolve(name + ".log"), adbCommand("shell", "-n", command + runner));
            try {
                Path directory = Files.createDirectories(evidence.resolve("checkpoints/light"));
                while (!missing.isEmpty() && tests.isAlive() && System.currentTimeMillis() < until) {
                    Socket connection;
                    try { connection = server.accept(); }
                    catch (SocketTimeoutException waiting) { continue; }
                    try (var socket = connection) {
                        socket.setSoTimeout((int) Math.max(1, Math.min(15_000, until - System.currentTimeMillis())));
                        collectCheckpoint(socket, directory, missing, checkpointResult);
                    }
                }
                if (missing.isEmpty()) {
                    result.setProperty(checkpointResult, "passed");
                    note("Screenshot collection passed: light checkpoints");
                } else {
                    result.setProperty(checkpointResult + ".missing", String.join(", ", missing));
                }
                return awaitCommand(name, tests, Math.max(0, until - System.currentTimeMillis()), true);
            } finally {
                if (tests.isAlive()) kill(tests);
            }
        }
    }

    private void collectCheckpoint(Socket socket, Path directory, Set<String> missing,
                                   String checkpointResult) throws Exception {
        var input = new DataInputStream(socket.getInputStream());
        String name = input.readUTF();
        require(missing.contains(name), "Unexpected checkpoint PNG: " + name + "; awaiting " + missing);
        int size = input.readInt();
        require(size > 0 && size <= 2_000_000, "Unexpected checkpoint PNG size: " + size);
        byte[] png = input.readNBytes(size);
        require(png.length == size, "Incomplete checkpoint PNG: " + name);
        Path pending = Files.write(work.resolve(name), png);
        validateScreenshot(pending, 412, 891);
        Path rank = directory.resolve("light-rank.png");
        if (name.equals("light-points.png") && Files.isRegularFile(rank))
            require(Files.mismatch(rank, pending) != -1, "Light rating and ranking checkpoint PNGs are identical");
        Path checkpoint = Files.move(pending, directory.resolve(name));
        if (releaseCandidate != null)
            result.setProperty("light.checkpointSha256." + name, digest(checkpoint));
        missing.remove(name);
        result.setProperty("light.screenshotCollection", "partial");
        result.setProperty(checkpointResult + "." + name, "validated");
        note("Host validated checkpoint screenshot: " + name);
        socket.getOutputStream().write(1);
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

    private void connect() throws Exception {
        // The ATD guest can reach the website over Wi-Fi without a working phone service.
        shell("connect", 90, true, "cmd connectivity airplane-mode disable && svc wifi enable");
        boolean requestedWifi = false;
        while (System.currentTimeMillis() < deadline) {
            String report = shell("online-connectivity", 45, true, "dumpsys connectivity");
            var active = Pattern.compile("(?m)^Active default network: ([0-9]+)\\s*$").matcher(report);
            if (active.find()) {
                result.setProperty("liveDefaultNetwork", active.group(1));
                return;
            }
            if (!requestedWifi) {
                shell("join-android-wifi", 30, false, "cmd wifi connect-network AndroidWifi open");
                requestedWifi = true;
            }
            pause(3000);
        }
        throw new IOException("Android has no active default network for the live website journeys");
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
        return command(name, seconds, required, adbCommand(args));
    }

    private String[] adbCommand(String... args) {
        List<String> command = new ArrayList<>(List.of(adbBinary(), "-P", Integer.toString(adbPort)));
        if (!List.of("connect", "start-server").contains(args[0])) command.addAll(List.of("-s", serial));
        command.addAll(List.of(args));
        return command.toArray(String[]::new);
    }

    private String adbBinary() { return sdk.resolve("platform-tools/adb").toString(); }
    private void run(String name, int seconds, String... command) throws Exception { command(name, seconds, true, command); }

    private String command(String name, int seconds, boolean required, String... command) throws Exception {
        long milliseconds = Math.min(seconds, remaining()) * 1000;
        Process process = launch(evidence.resolve(name + ".log"), command);
        return awaitCommand(name, process, milliseconds, required);
    }

    private String awaitCommand(String name, Process process, long milliseconds, boolean required) throws Exception {
        Path log = evidence.resolve(name + ".log");
        if (!process.waitFor(milliseconds, TimeUnit.MILLISECONDS)) {
            kill(process);
            throw new IOException(name + " timeout; see " + log);
        }
        if (required && process.exitValue() != 0) throw new IOException(name + " exited " + process.exitValue() + "; see " + log);
        return Files.readString(log);
    }

    private synchronized Process launch(Path log, String... command) throws IOException {
        if (cleaned) throw new IOException("Run is shutting down");
        Process process = startProcess(log, command);
        children.add(process);
        return process;
    }

    private Process startProcess(Path log, String... command) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command).directory(source.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().clear();
        builder.environment().putAll(env);
        Process process = builder.start();
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

    private void diagnose(long until) {
        if (emulator == null || !emulator.isAlive()) return;
        note("Collecting available guest screenshots and diagnostics");
        diagnostic(until, "failure-pull", "pull", CAPTURE_FILES, evidence.resolve("failure-captures").toString());
        // Read the guest's existing thread dump before depending on a responsive system server.
        diagnostic(until, "failure-anr", "shell", "-n",
                "su 0 sh -c 'ls -lt /data/anr; trace=$(ls -1t /data/anr/anr_* 2>/dev/null | head -n 1); "
                + "if [ -n \"$trace\" ]; then head -n 160 \"$trace\"; fi'");
        diagnostic(until, "failure-logcat", "shell", "-n",
                "logcat -b main -b system -b events -d -t 4000 -s AndroidRuntime ActivityManager TestRunner Watchdog am_anr");
        diagnostic(until, "failure-window", "shell", "-n", "dumpsys window displays");
    }

    private void diagnostic(long until, String name, String... args) {
        long milliseconds = Math.min(10_000, until - System.currentTimeMillis());
        if (milliseconds <= 0) return;
        try {
            Process process = startProcess(evidence.resolve(name + ".log"), adbCommand(args));
            try { process.waitFor(milliseconds, TimeUnit.MILLISECONDS); }
            finally { if (process.isAlive()) kill(process); }
        } catch (Exception ignored) { }
    }

    private static void kill(Process process) {
        process.descendants().forEach(child -> child.destroyForcibly());
        process.destroyForcibly();
        try { process.waitFor(5, TimeUnit.SECONDS); } catch (InterruptedException stopped) { Thread.currentThread().interrupt(); }
    }

    private synchronized void cleanup() {
        if (cleaned) return;
        cleaned = true;
        result.setProperty("checksFinished", Instant.now().toString());
        result.putIfAbsent("phase", phase);
        if (result.getProperty("status") == null) {
            result.setProperty("status", "failed");
            result.setProperty("failure", "Interrupted before completion");
        }
        result.replaceAll((key, value) -> value.equals("running") ? "failed" : value);
        for (Process child : List.copyOf(children)) if (child != emulator && child.isAlive()) kill(child);
        if (result.getProperty("status").equals("failed"))
            diagnose(System.currentTimeMillis() + 40_000);
        if (emulator != null && emulator.isAlive()) kill(emulator);
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
                for (String name : List.of("lint-results-debug.xml", "lint-results-debug.html")) {
                    Path report = build.resolve("app/reports").resolve(name);
                    if (Files.isRegularFile(report)) Files.copy(report, evidence.resolve(name), StandardCopyOption.REPLACE_EXISTING);
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
        note("Result: " + result.getProperty("status") + "; evidence: " + evidence);
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
