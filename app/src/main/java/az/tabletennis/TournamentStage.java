package az.tabletennis;

enum TournamentStage {
    GROUP(R.string.stage_group),
    ROUND_OF_32(R.string.stage_round_of_32),
    ROUND_OF_16(R.string.stage_round_of_16),
    QUARTERFINAL(R.string.stage_quarterfinal),
    SEMIFINAL(R.string.stage_semifinal),
    FINAL(R.string.stage_final),
    THIRD_PLACE(R.string.stage_third_place);

    final int labelResource;

    TournamentStage(int labelResource) { this.labelResource = labelResource; }

    static TournamentStage fromSource(String text) {
        return switch (text) {
            case "Play-in" -> GROUP;
            case "1/16" -> ROUND_OF_32;
            case "1/8" -> ROUND_OF_16;
            case "1/4" -> QUARTERFINAL;
            case "1/2" -> SEMIFINAL;
            case "Final" -> FINAL;
            case "3-cü yer oyunu" -> THIRD_PLACE;
            default -> null;
        };
    }
}
