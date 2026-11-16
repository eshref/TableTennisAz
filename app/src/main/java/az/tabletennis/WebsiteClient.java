package az.tabletennis;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** The same production HTTPS policy is used for HTML and public chart JSON. */
final class WebsiteClient {
    private final HttpUrl website;
    private final OkHttpClient client = new OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build();

    WebsiteClient(HttpUrl website) { this.website = website; }

    String get(String path) throws IOException {
        HttpUrl url = website.resolve(path);
        if (url == null || !url.host().equals(website.host()) || url.port() != website.port()
                || !url.scheme().equals(website.scheme())) throw new IOException("Unexpected website link");
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
