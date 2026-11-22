# Publish the head-to-head index on GitHub Pages

Üz-üzə checks the published match history page by page on each phone. That
search took about five minutes for a reported singles matchup and
doubles matchup. Publish a prebuilt index daily on the project's GitHub Pages
site so Android can fetch the portion it needs for each matchup.

Build the index from published player histories, tournament results, individual
games within team fixtures, and duels. Use website profile IDs to verify the
players on each side and match IDs to count each result once. Show the last
successful source refresh in Üz-üzə. The completed list and **Qələbə sayı**
cover the published history as of that refresh. Newly published results and
corrections appear after the next successful refresh.

When Android cannot fetch the index, show the latest completed result saved for
the same matchup with its original source refresh date. If there is no saved
result for that matchup, show **Axtarış tamamlanmadı** and **Yenidən cəhd et**.
When offline, autocomplete uses the player names and profile IDs saved with
completed searches so those matchups can be reopened after an app restart.

The acceptance target is five seconds from **Axtar** to the final **Qələbə sayı**
on a normal connection, including a first search after a fresh install. Measure
it for Əşrəf Səfərov against Etibar Yunusov and for Elçin Mustafayev / Erhan
Əliyev against Xəyal Həşimli / Əlixan Əlizadə.
