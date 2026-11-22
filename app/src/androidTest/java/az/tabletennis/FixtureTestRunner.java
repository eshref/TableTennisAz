package az.tabletennis;

import android.app.Application;
import android.content.Context;
import androidx.test.runner.AndroidJUnitRunner;
import java.io.IOException;
import java.net.InetAddress;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;

public final class FixtureTestRunner extends AndroidJUnitRunner {
    @Override public Application newApplication(ClassLoader loader, String appClass, Context context)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        return super.newApplication(loader, FixtureApplication.class.getName(), context);
    }

    public static final class FixtureApplication extends RankingsApplication {
        volatile WebsiteClient fixtureClient;
        volatile TournamentAlerts.Topic fixtureAlertTopic;

        @Override protected TournamentAlerts.Topic tournamentAlertTopic() {
            return fixtureAlertTopic == null ? super.tournamentAlertTopic() : fixtureAlertTopic;
        }

        void hostWebsiteFixtures(MockWebServer server) throws IOException {
            HeldCertificate certificate = new HeldCertificate.Builder().addSubjectAlternativeName("127.0.0.1").build();
            HandshakeCertificates serverTls = new HandshakeCertificates.Builder().heldCertificate(certificate).build();
            HandshakeCertificates clientTls = new HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate()).build();
            server.useHttps(serverTls.sslSocketFactory(), false);
            server.start(InetAddress.getByName("127.0.0.1"), 0);
            // Use the IP identity from the certificate even when MockWebServer resolves localhost.
            HttpUrl fixtureUrl = server.url("/").newBuilder().host("127.0.0.1").build();
            fixtureClient = new WebsiteClient(fixtureUrl, new OkHttpClient.Builder()
                    .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager()));
        }

        @Override WebsiteClient websiteClient() {
            WebsiteClient fixture = fixtureClient;
            return fixture == null ? super.websiteClient() : fixture;
        }

        @Override protected WebsiteClient headToHeadIndexClient() {
            WebsiteClient fixture = fixtureClient;
            return fixture == null ? super.headToHeadIndexClient() : fixture;
        }

        @Override protected HttpUrl tournamentCatalogueUrl() {
            WebsiteClient fixture = fixtureClient;
            return fixture == null ? super.tournamentCatalogueUrl() : fixture.websiteUrl().resolve("/tournaments-v1.json");
        }

        @Override protected boolean isOnline() {
            // The loopback website remains reachable with the emulator's radios disabled.
            return fixtureClient != null || super.isOnline();
        }
    }
}
