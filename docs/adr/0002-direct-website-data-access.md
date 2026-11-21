# Fetch website data directly from Android

The website backend's source code is unavailable, and the inspected public
interfaces provide essential ratings, profile, and result data primarily as
HTML, with supplementary JSON endpoints. For the first release, fetch and parse
these interfaces directly in the Android app to avoid hosting and operating a
separate service. A hosted adapter could centralize parsing fixes and reduce
payloads, but would add hosting and maintenance.

Website markup changes can require an app update. Large HTML responses also
make loading and caching behavior important. Offline access to previously loaded
data, with visible update times, is already part of the agreed scope.

The tournament lists use a [generated catalogue on GitHub Pages](0009-publish-tournament-catalogue-on-github-pages.md).
