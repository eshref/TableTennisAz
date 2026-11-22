package az.tabletennis;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Shared production HTTPS policy for HTML, public chart JSON, and optional portraits. */
final class WebsiteClient {
    private static final String DEFAULT_PHOTO = "/assets/img/person.png";
    private final HttpUrl website;
    private final OkHttpClient client;

    WebsiteClient(HttpUrl website) { this(website, new OkHttpClient.Builder()); }

    WebsiteClient(HttpUrl website, OkHttpClient.Builder transport) {
        this.website = website;
        client = transport.connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS).build();
    }

    HttpUrl websiteUrl() { return website; }

    String photoUrl(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        HttpUrl url = website.resolve(value.trim());
        if (!allowed(url) || DEFAULT_PHOTO.equalsIgnoreCase(url.encodedPath())) return null;
        return url.toString();
    }

    byte[] image(String path) throws IOException {
        HttpUrl url = website.resolve(path);
        // Inspect each redirect before requesting it, retaining the website origin/TLS policy.
        OkHttpClient images = client.newBuilder().followRedirects(false).followSslRedirects(false).build();
        for (int redirects = 0; redirects <= 5; redirects++) {
            if (!allowed(url) || DEFAULT_PHOTO.equalsIgnoreCase(url.encodedPath())) throw new IOException("Unexpected image link");
            Request request = new Request.Builder().url(url).header("Accept", "image/*")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android) TableTennisAz/0.1").build();
            try (Response response = images.newCall(request).execute()) {
                if (response.isRedirect()) {
                    String location = response.header("Location");
                    url = location == null ? null : url.resolve(location);
                    continue;
                }
                if (!response.isSuccessful() || response.body() == null) throw new IOException("Image HTTP " + response.code());
                // Optional portraits must not consume unbounded memory, even with a chunked response.
                int limit = 5 * 1024 * 1024;
                if (response.body().contentLength() > limit) throw new IOException("Image too large");
                try (var input = response.body().byteStream(); var output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (output.size() + count > limit) throw new IOException("Image too large");
                        output.write(buffer, 0, count);
                    }
                    return output.toByteArray();
                }
            }
        }
        throw new IOException("Too many image redirects");
    }

    private boolean allowed(HttpUrl url) {
        return url != null && url.host().equals(website.host()) && url.port() == website.port()
                && url.scheme().equals(website.scheme()) && url.username().isEmpty() && url.password().isEmpty();
    }

    String get(String path) throws IOException {
        HttpUrl url = website.resolve(path);
        if (!allowed(url)) throw new IOException("Unexpected website link");
        Request request = new Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android) TableTennisAz/0.1")
                .header("Accept", "text/html, application/json")
                .header("Accept-Language", "az-AZ,az;q=0.9").build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("Website HTTP " + response.code());
            return response.body().string();
        }
    }
}
