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

        @Override WebsiteClient websiteClient() {
            WebsiteClient fixture = fixtureClient;
            return fixture == null ? super.websiteClient() : fixture;
        }

        @Override protected HttpUrl tournamentCatalogueUrl() {
            WebsiteClient fixture = fixtureClient;
            return fixture == null ? super.tournamentCatalogueUrl() : fixture.websiteUrl().resolve("/tournaments-v1.json");
        }
    }
}
