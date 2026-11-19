"""Plan the signed fixture jobs and summarize the evidence they upload."""

import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sys


JOURNEYS = {
    'match': 'MatchDeviceTest',
    'profile': 'ProfileDeviceTest',
    'rating-history': 'RatingHistoryDeviceTest',
    'results': 'ResultsDeviceTest',
    'doubles-profile': 'DoublesProfileDeviceTest',
    'native-acceptance': 'NativeAcceptanceDeviceTest',
}
CONFIGURATIONS = {
    'light': 'Standard light, 412×891, 100% text',
    'dark-offline': 'Dark offline, 412×891, 100% text',
    'narrow-large': 'Narrow light offline, 320×568, 150% text',
    'landscape-large': 'Landscape dark offline, 891×320, 150% text',
}
SHA256 = re.compile(r'[0-9a-f]{64}')


def properties(path):
    if not path.is_file():
        return {}
    return dict(line.split('=', 1) for line in path.read_text().splitlines() if '=' in line and not line.startswith('#'))


def copy_validated_captures(paths, destination, result, checksum_prefix):
    copied = set()
    for path in paths:
        checksum = result.get(checksum_prefix + path.name, '')
        if not path.is_file() or not SHA256.fullmatch(checksum):
            continue
        with path.open('rb') as png:
            if hashlib.file_digest(png, 'sha256').hexdigest() != checksum:
                continue
        destination.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, destination)
        copied.add(path.name)
    return copied


def collect_screenshots(configuration, run, destination, result):
    files = list((run / 'captures' / configuration).rglob(f'{configuration}-*.png'))
    copied = copy_validated_captures(files, destination, result, f'{configuration}.screenshotSha256.')
    collection = result.get(f'{configuration}.screenshotCollection', 'not-run')
    if collection != 'passed':
        return collection, False
    expected = set(result.get(f'{configuration}.expectedScreenshots', '').split(',')) - {''}
    if not expected or not expected.issubset(copied):
        unmatched = (expected - copied) & {path.name for path in files}
        reason = 'checksum mismatch' if unmatched else 'missing'
        return f'{reason} ({len(expected & copied)}/{len(expected)} required PNGs verified)', False
    return f'passed ({len(copied)} PNGs)', True


def summarize_journey(artifact, bundle, journey, test, commit, digest):
    installed = properties(artifact / 'job.properties').get('installed', 'missing')
    results = list(artifact.glob('run-*/result.properties'))
    result = properties(results[0]) if len(results) == 1 else {}
    matches = bool(SHA256.fullmatch(digest) and result.get('apk.sha256') == digest
                   and result.get('source.commit') == commit and result.get('apk.variant') == 'release'
                   and result.get('api') == '36' and result.get('tests') == f'az.tabletennis.{test}')
    identity = 'matched' if matches else 'mismatch' if result.get('apk.sha256') else 'missing'
    rows = []
    covered = 0
    destination = bundle / 'screenshots' / journey
    if matches:
        run = results[0].parent
        evidence = bundle / 'journeys' / journey
        evidence.mkdir(parents=True)
        shutil.copy2(results[0], evidence)
        checkpoints = (run / 'checkpoints/light').glob('light-*.png')
        copy_validated_captures(checkpoints, destination / 'light-checkpoints', result, 'light.checkpointSha256.')
    for configuration in CONFIGURATIONS:
        instrumentation = result.get(f'{configuration}.instrumentation', 'not-run')
        tests = result.get(f'{configuration}.tests', '')
        passed = instrumentation == 'passed' and re.fullmatch(r'OK \([1-9][0-9]* tests?\)', tests)
        if instrumentation == 'passed':
            instrumentation = tests if passed else 'invalid test count'
        if matches:
            screenshots, collected = collect_screenshots(configuration, run, destination / configuration, result)
        else:
            screenshots = 'unverified' if result.get(f'{configuration}.screenshotCollection') else 'not-run'
            collected = False
        covered += bool(passed and collected)
        rows.append(f'| {journey} | {configuration} | {instrumentation} | {screenshots} |')
    matrix = result.get('matrix', 'not-run')
    preflight = result.get('preflight', 'not-run')
    coverage = f'{covered}/{len(CONFIGURATIONS)}'
    summary = f'| {journey} | {identity} | {installed} | {preflight} | {matrix} | {coverage} |'
    complete = (installed == 'success' and result.get('status') == matrix == preflight == 'passed'
                and covered == len(CONFIGURATIONS) and SHA256.fullmatch(result.get('testApk.sha256', '')))
    return summary, rows, covered, bool(complete)


def report(artifacts, bundle):
    bundle.mkdir(parents=True, exist_ok=True)
    commit = os.environ['GITHUB_SHA']
    digest = os.environ['CANDIDATE_SHA256']
    version = os.environ['CANDIDATE_VERSION']
    job_result = os.environ['FIXTURE_RESULT']
    run_url = f"{os.environ['GITHUB_SERVER_URL']}/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}"
    journeys = []
    configurations = []
    total = 0
    complete = job_result == 'success'
    for journey, test in JOURNEYS.items():
        summary, rows, covered, passed = summarize_journey(
            artifacts / f'android-signed-api36-{journey}', bundle, journey, test, commit, digest)
        journeys.append(summary)
        configurations.extend(rows)
        total += covered
        complete &= passed
    label = 'passed' if complete else 'failed or incomplete'
    lines = [
        '## Signed Android fixture matrix', '',
        f'Candidate `TableTennisAz-{version}.apk`' if version else 'Candidate unavailable',
        f'Commit `{commit}` · APK SHA-256 `{digest or "unavailable"}` · Android API 36', '',
        f'**{label}** · {total}/{len(JOURNEYS) * len(CONFIGURATIONS)} configurations verified · Fixture jobs **{job_result}**', '',
        f'[Run and artifacts]({run_url}) · Screenshots and summary in `android-signed-fixture-matrix`.',
        'Download `android-signed-api36-<journey>` for APKs, logs, and available failure diagnostics.', '',
        '| Journey | Candidate | Installed step | Preflight | Matrix | Configurations |',
        '| --- | --- | --- | --- | --- | --- |', *journeys, '',
        *(f'- `{name}` · {description}' for name, description in CONFIGURATIONS.items()), '',
        '| Journey | Configuration | Instrumentation | Screenshots |',
        '| --- | --- | --- | --- |', *configurations, '',
    ]
    summary = '\n'.join(lines)
    (bundle / 'verification.md').write_text(summary)
    print(summary)
    return 0 if complete else 1


if __name__ == '__main__':
    if sys.argv[1:] == ['plan']:
        print('matrix=' + json.dumps({'include': [{'journey': name, 'test': test} for name, test in JOURNEYS.items()]}))
    else:
        artifacts, bundle = sys.argv[1:]
        sys.exit(report(Path(artifacts), Path(bundle)))
