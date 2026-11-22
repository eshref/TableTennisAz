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
    'head-to-head': 'HeadToHeadDeviceTest',
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


def verified_captures(paths, result, checksum_prefix):
    verified = {}
    for path in paths:
        checksum = result.get(checksum_prefix + path.name, '')
        if path.is_file() and SHA256.fullmatch(checksum) and file_digest(path) == checksum:
            verified[path.name] = path
    return verified


def copy_captures(captures, destination):
    if captures:
        destination.mkdir(parents=True, exist_ok=True)
        for path in captures.values():
            shutil.copy2(path, destination)


FIXTURE_CAPTURES = properties(Path(__file__).with_name('fixture-captures.properties'))


def configuration_status(configuration, paths, result, test, destination=None):
    files = list(paths)
    verified = verified_captures(files, result, f'{configuration}.screenshotSha256.')
    if destination is not None:
        copy_captures(verified, destination)

    collection = result.get(f'{configuration}.screenshotCollection', 'not-run')
    required = {f'{configuration}-{name}.png' for name in FIXTURE_CAPTURES[f'az.tabletennis.{test}'].split(',')}
    recorded = set(result.get(f'{configuration}.expectedScreenshots', '').split(','))
    collected = collection == 'passed' and recorded == required and required.issubset(verified)
    if collected:
        collection = f'passed ({len(verified)} PNGs)'
    elif collection == 'passed':
        unmatched = (required - verified.keys()) & {path.name for path in files}
        reason = 'checksum mismatch' if unmatched else 'missing'
        if required != recorded:
            reason = 'incorrect required screenshot list'
        collection = f'{reason} ({len(required & verified.keys())}/{len(required)} required PNGs verified)'

    instrumentation = result.get(f'{configuration}.instrumentation', 'not-run')
    tests = result.get(f'{configuration}.tests', '')
    passed = instrumentation == 'passed' and bool(re.fullmatch(r'OK \([1-9][0-9]* tests?\)', tests))
    if instrumentation == 'passed':
        instrumentation = tests if passed else 'invalid test count'
    return instrumentation, collection, passed and collected, verified


def file_digest(path):
    with path.open('rb') as contents:
        return hashlib.file_digest(contents, 'sha256').hexdigest()


def bundle_pngs(directory, configuration):
    if directory.is_dir():
        return (path for path in directory.iterdir()
                if path.name.startswith(f'{configuration}-') and path.name.endswith('.png'))
    return ()


def matches_candidate(result, test, commit, digest):
    return (bool(SHA256.fullmatch(digest)) and result.get('apk.sha256') == digest
            and result.get('source.commit') == commit and result.get('apk.variant') == 'release'
            and result.get('api') == '36' and result.get('tests') == f'az.tabletennis.{test}')


def summarize_journey(job, result_path, captures, test, journey, commit, digest, bundle=None):
    installed = properties(job).get('installed', 'missing')
    result = properties(result_path) if result_path else {}
    matches = matches_candidate(result, test, commit, digest)
    identity = 'matched' if matches else 'mismatch' if result.get('apk.sha256') else 'missing'
    rows = []
    covered = 0
    destination = bundle / 'screenshots' / journey if bundle else None
    if matches and bundle:
        evidence = bundle / 'journeys' / journey
        evidence.mkdir(parents=True)
        shutil.copy2(result_path, evidence)
        if job.is_file():
            shutil.copy2(job, evidence)
        checkpoints = (result_path.parent / 'checkpoints/light').glob('light-*.png')
        copy_captures(verified_captures(checkpoints, result, 'light.checkpointSha256.'), destination / 'light-checkpoints')
    verified = []
    for configuration in CONFIGURATIONS:
        instrumentation = result.get(f'{configuration}.instrumentation', 'not-run')
        if matches:
            directory = captures / configuration
            paths = directory.rglob(f'{configuration}-*.png') if bundle else bundle_pngs(directory, configuration)
            instrumentation, screenshots, passed, files = configuration_status(
                configuration, paths, result, test, destination / configuration if destination else None)
            covered += passed
            verified.extend(files.values())
        else:
            screenshots = 'unverified' if result.get(f'{configuration}.screenshotCollection') else 'not-run'
        rows.append(f'| {journey} | {configuration} | {instrumentation} | {screenshots} |')
    matrix = result.get('matrix', 'not-run')
    preflight = result.get('preflight', 'not-run')
    coverage = f'{covered}/{len(CONFIGURATIONS)}'
    summary = f'| {journey} | {identity} | {installed} | {preflight} | {matrix} | {coverage} |'
    complete = (installed == 'success' and result.get('status') == matrix == preflight == 'passed'
                and covered == len(CONFIGURATIONS) and SHA256.fullmatch(result.get('testApk.sha256', '')))
    return summary, rows, covered, bool(complete), verified


def report(artifacts, bundle):
    bundle.mkdir(parents=True, exist_ok=True)
    commit = os.environ['GITHUB_SHA']
    digest = os.environ['CANDIDATE_SHA256']
    version = os.environ['CANDIDATE_VERSION']
    job_result = os.environ['FIXTURE_RESULT']
    journeys = []
    configurations = []
    total = 0
    complete = job_result == 'success'
    for journey, test in JOURNEYS.items():
        artifact = artifacts / f'android-signed-api36-{journey}'
        results = list(artifact.glob('run-*/result.properties'))
        result = results[0] if len(results) == 1 else None
        summary, rows, covered, passed, _ = summarize_journey(
            artifact / 'job.properties', result, result.parent / 'captures' if result else None,
            test, journey, commit, digest, bundle)
        journeys.append(summary)
        configurations.extend(rows)
        total += covered
        complete &= passed
    summary = matrix_summary(version, commit, digest, job_result, total, complete, journeys, configurations)
    (bundle / 'verification.md').write_text(summary)
    print(summary)
    return 0 if complete else 1


def matrix_summary(version, commit, digest, job_result, total, complete, journeys, configurations):
    run_url = f"{os.environ['GITHUB_SERVER_URL']}/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}"
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
    return '\n'.join(lines)


if __name__ == '__main__':
    if sys.argv[1:] == ['plan']:
        print('matrix=' + json.dumps({'include': [{'journey': name, 'test': test} for name, test in JOURNEYS.items()]}))
    else:
        artifacts, bundle = sys.argv[1:]
        sys.exit(report(Path(artifacts), Path(bundle)))
