package az.tabletennis;

import java.io.Serializable;

final class DoublesPair implements Serializable {
    private static final long serialVersionUID = 1L;
    final long id;
    final MatchDetails.PlayerLink first;
    final MatchDetails.PlayerLink second;
    final String firstPhotoUrl;
    final String secondPhotoUrl;
    final boolean photoMetadataResolved;

    DoublesPair(long id, MatchDetails.PlayerLink first, MatchDetails.PlayerLink second) {
        this(id, first, second, null, null, false);
    }

    DoublesPair(long id, MatchDetails.PlayerLink first, MatchDetails.PlayerLink second,
            String firstPhotoUrl, String secondPhotoUrl, boolean photoMetadataResolved) {
        this.id = id;
        this.first = first;
        this.second = second;
        this.firstPhotoUrl = firstPhotoUrl;
        this.secondPhotoUrl = secondPhotoUrl;
        this.photoMetadataResolved = photoMetadataResolved;
    }

    String names() { return first.name + " / " + second.name; }

    boolean hasSamePlayers(DoublesPair other) {
        return (first.id == other.first.id && second.id == other.second.id)
                || (first.id == other.second.id && second.id == other.first.id);
    }
}
