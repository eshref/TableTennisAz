import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

class ReleaseMetadata {
    private static final Pattern PUBLIC_VERSION = Pattern.compile("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)");
    private static final Pattern CHANGELOG_SECTION = Pattern.compile("^ {0,3}##(?:[ \\t]+.*)?$");
    private static final Pattern CODE_FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})(.*)$");
    private static final Version PHONE_REVIEW = new Version("0.3.0", 3);

    private record Version(String name, int code) {}

    public static void main(String[] args) {
        try {
            Version candidate = readVersion(Path.of("version.properties"));
            Version previous = args.length == 1 ? PHONE_REVIEW : readPreviousVersion(args[1], Path.of(args[2]));
            validateNextVersion(candidate, previous);
            List<String> notes = readNotes(Path.of("CHANGELOG.md"), candidate.name());

            Path output = Path.of(args[0]);
            Files.createDirectories(output);
            Files.write(output.resolve("release-notes.md"), notes);
            System.out.printf("version_name=%s%nversion_code=%d%n", candidate.name(), candidate.code());
        } catch (IOException | IllegalArgumentException error) {
            System.err.println(error.getMessage());
            System.exit(1);
        }
    }

    private static Version readPreviousVersion(String tag, Path properties) throws IOException {
        Version previous = readVersion(properties);
        if (!tag.equals("v" + previous.name())) {
            throw new IllegalArgumentException("Latest release tag " + tag + " does not match its versionName " + previous.name() + ".");
        }
        return previous;
    }

    private static Version readVersion(Path path) throws IOException {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(path)) {
            properties.load(reader);
        }
        String name = properties.getProperty("versionName", "");
        if (!PUBLIC_VERSION.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid versionName in " + path + ". Expected MAJOR.MINOR.PATCH without leading zeros.");
        }
        String code = properties.getProperty("versionCode", "");
        try {
            if (code.matches("[1-9][0-9]*")) return new Version(name, Integer.parseInt(code));
        } catch (NumberFormatException ignored) {
            // The Android version code must fit in an integer.
        }
        throw new IllegalArgumentException("Invalid versionCode in " + path + ". Expected a positive Android integer.");
    }

    private static void validateNextVersion(Version candidate, Version previous) {
        if (compareNames(candidate.name(), previous.name()) <= 0) {
            throw new IllegalArgumentException("versionName must be greater than " + previous.name() + ".");
        }
        long expectedCode = (long) previous.code() + 1;
        if (candidate.code() != expectedCode) {
            throw new IllegalArgumentException("versionCode must be " + expectedCode + " after " + previous.name() + ".");
        }
    }

    private static int compareNames(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        for (int index = 0; index < leftParts.length; index++) {
            int comparison = new BigInteger(leftParts[index]).compareTo(new BigInteger(rightParts[index]));
            if (comparison != 0) return comparison;
        }
        return 0;
    }

    private static List<String> readNotes(Path changelog, String version) throws IOException {
        Pattern heading = Pattern.compile("^ {0,3}##[ \\t]+" + Pattern.quote(version) + "[ \\t]+-[ \\t]+(\\S+)[ \\t]*$");
        List<String> lines = Files.readAllLines(changelog);
        List<Integer> headings = sectionHeadings(lines);
        List<String> notes = null;
        for (int section = 0; section + 1 < headings.size(); section++) {
            int headingLine = headings.get(section);
            var match = heading.matcher(lines.get(headingLine));
            if (!match.matches()) continue;
            if (notes != null) throw new IllegalArgumentException("Duplicate dated changelog sections for " + version + ".");

            validateDate(match.group(1), version);
            int start = headingLine + 1;
            int end = headings.get(section + 1);
            while (start < end && lines.get(start).isBlank()) start++;
            while (end > start && lines.get(end - 1).isBlank()) end--;
            notes = lines.subList(start, end);
        }
        if (notes == null) throw new IllegalArgumentException("Missing dated changelog section for " + version + ".");
        if (notes.isEmpty()) throw new IllegalArgumentException("The dated changelog section for " + version + " is empty.");
        return notes;
    }

    private static List<Integer> sectionHeadings(List<String> lines) {
        List<Integer> headings = new ArrayList<>();
        String openFence = null;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            var fence = CODE_FENCE.matcher(line);
            if (fence.matches()) {
                String marker = fence.group(1);
                String suffix = fence.group(2);
                if (openFence == null) {
                    if (marker.charAt(0) == '~' || !suffix.contains("`")) openFence = marker;
                } else if (marker.startsWith(openFence) && suffix.isBlank()) {
                    openFence = null;
                }
            } else if (openFence == null && CHANGELOG_SECTION.matcher(line).matches()) {
                headings.add(index);
            }
        }
        headings.add(lines.size());
        return headings;
    }

    private static void validateDate(String date, String version) {
        try {
            if (date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                LocalDate.parse(date);
                return;
            }
        } catch (DateTimeParseException ignored) {
            // Surface the changelog heading that needs correction.
        }
        throw new IllegalArgumentException("Invalid changelog date for " + version + ". Expected a calendar date in YYYY-MM-DD format.");
    }
}
