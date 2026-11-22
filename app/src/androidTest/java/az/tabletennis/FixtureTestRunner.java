package az.tabletennis;

import android.app.Application;
import android.content.Context;
import androidx.test.runner.AndroidJUnitRunner;
import okhttp3.HttpUrl;

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
