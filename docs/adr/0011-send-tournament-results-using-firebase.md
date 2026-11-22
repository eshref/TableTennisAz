# Send tournament results alerts using Firebase Cloud Messaging

Use the existing [GitHub Pages catalogue workflow](0009-publish-tournament-catalogue-on-github-pages.md)
to detect tournament results publications and send Android push alerts after
the updated catalogue is live. A single Firebase Cloud Messaging topic lets
Firebase manage subscriptions to public results for both tournament formats
and deliver alerts while the app is closed. Push requires Google Play on the
phone and supports installations from GitHub Releases and Google Play.
[Firebase Android requirements](https://firebase.google.com/docs/cloud-messaging/android/get-started)
· [Firebase topic messaging](https://firebase.google.com/docs/cloud-messaging/topic-messaging)

Check for new results every 30 minutes and aim to deliver alerts within about
an hour of results becoming available on tabletennis.az. Send one alert when a
completed singles or doubles tournament first appears in the live catalogue with
results available to open. Tapping the alert opens that tournament's match
results. Alerts expire 24 hours after the catalogue publication. GitHub can
delay scheduled runs and disables the schedule after 60 days of repository
inactivity. [GitHub Actions scheduling](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#schedule)

**Tournament alerts** is one in-app opt-in for both formats. Request Android
notification permission when the user enables it, where required. Start
eligibility after permission is granted and the Firebase subscription succeeds.
Use data messages so the app can check its current preference, the publication
time, and expiry before displaying an alert. After re-enabling, apply the new
eligibility time.

Persist first publications and pending sends by Results index ID. Seed the
publication history with the tournaments already in the live catalogue when
alerts launch. Retain each ID through temporary omissions so a reappearance or
result correction shares the original publication identity. Retry pending sends
after publication until Firebase accepts them or they expire. Deduplicate
received messages by tournament ID on the device so a send retry produces at
most one alert.

Before rollout, update the published [privacy policy](../privacy-policy.html)
to describe notification processing and controls. Verify live push delivery with
a Google Play compatible test device or a Google APIs emulator.
