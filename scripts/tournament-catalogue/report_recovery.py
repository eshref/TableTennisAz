"""Compare a recovery build with the previous and historically verified catalogues."""
import argparse
from collections import Counter
import json
from pathlib import Path

from generate import DOUBLES_PODIUM_STATE, catalogue_tournaments, doubles_podiums, placement_withheld


def medal_ids(tournaments):
    return {
        event_id: {medal: tuple(sorted(player["id"] for player in players)) for medal, players in podium.items()}
        for event_id, podium in doubles_podiums(tournaments).items()
    }


def report(previous, snapshot, candidate):
    previous_tournaments = catalogue_tournaments(previous)
    candidate_tournaments = catalogue_tournaments(candidate)
    before = medal_ids(previous_tournaments)
    after = medal_ids(candidate_tournaments)
    historical = medal_ids(catalogue_tournaments(snapshot))
    state = candidate.get(DOUBLES_PODIUM_STATE, {})
    outcomes = Counter()
    differences = []
    absent = []
    for event_id, medals in historical.items():
        if event_id not in after:
            outcomes["Tournament absent from the verified catalogue"] += len(medals)
            absent.append(str(event_id))
            continue
        for medal, expected in medals.items():
            actual = after[event_id].get(medal)
            if actual == expected:
                if before.get(event_id, {}).get(medal) == expected:
                    outcomes["Already present"] += 1
                else:
                    outcomes["Restored"] += 1
            elif actual:
                outcomes["Published with a different pair"] += 1
                differences.append(f"- Tournament {event_id} {medal} changed from {expected} to {actual}.")
            elif placement_withheld(state.get(str(event_id), {}), medal):
                outcomes["Withheld after a confirmed pair change"] += 1
                differences.append(f"- Tournament {event_id} {medal} withheld after a change from {expected}.")
            else:
                raise ValueError(f"Event {event_id} lost its verified {medal} placement without a correction")

    print("## Tournament catalogue recovery")
    print()
    print("| Format | Previous tournaments | Previous podiums | Repaired tournaments | Repaired podiums |")
    print("| --- | ---: | ---: | ---: | ---: |")
    for competition in ("singles", "doubles"):
        counts = []
        for tournaments in (previous_tournaments, candidate_tournaments):
            rows = [row for row in tournaments if row["format"] == competition]
            counts.extend((len(rows), sum(bool(row.get("podium")) for row in rows)))
        print(f"| {competition} | {' | '.join(map(str, counts))} |")

    print()
    print(f"Compared {outcomes.total()} historically verified doubles placements.")
    for outcome, count in outcomes.items():
        print(f"- {outcome} — {count}")
    if absent:
        print(f"- Historical tournaments absent from the verified catalogue — {', '.join(absent)}")
    for difference in differences:
        print(difference)
    medals = ", ".join(f"{medal} {first}/{second}" for medal, (first, second) in after.get(941, {}).items())
    print(f"- Tournament 941 medalist IDs — {medals or 'no published podium'}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("previous", type=Path)
    parser.add_argument("snapshot", type=Path)
    parser.add_argument("candidate", type=Path)
    args = parser.parse_args()
    report(*(json.loads(path.read_bytes()) for path in (args.previous, args.snapshot, args.candidate)))


if __name__ == "__main__":
    main()
