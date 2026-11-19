"""Validate downloaded installed evidence and package it for GitHub Releases."""

from io import BytesIO
import re
from zipfile import BadZipFile, Path as ZipPath, ZIP_DEFLATED, ZipFile

from release_matrix import (CONFIGURATIONS, JOURNEYS, SHA256, bundle_pngs, configuration_status, file_digest,
                            matches_candidate, matrix_summary, properties, summarize_journey)


class EvidenceError(Exception):
    pass


def fixture_evidence(fixture, commit, digest):
    journeys = []
    configurations = []
    files = {}
    for journey, test in JOURNEYS.items():
        evidence = fixture / 'journeys' / journey
        result = evidence / 'result.properties'
        job = evidence / 'job.properties'
        summary, rows, _, passed, captures = summarize_journey(
            job, result, fixture / 'screenshots' / journey, test, journey, commit, digest)
        if not passed:
            raise EvidenceError(f'Fixture evidence for {journey} does not verify the candidate.\n' + '\n'.join(rows))
        journeys.append(summary)
        configurations.extend(rows)
        files[f'journeys/{journey}/result.properties'] = result
        files[f'journeys/{journey}/job.properties'] = job
        files.update((f'screenshots/{journey}/{png.parent.name}/{png.name}', png) for png in captures)
    return files, journeys, configurations


def verification_archive(fixture, upgrade, version, commit, digest, previous_tag, previous_digest):
    files, journeys, configurations = fixture_evidence(fixture, commit, digest)
    results = list(upgrade.glob('run-*/result.properties'))
    if len(results) != 1:
        raise EvidenceError('In-place upgrade evidence is missing or incomplete.')
    result = results[0]
    job = upgrade / 'job.properties'
    if file_digest(result.parent / 'previous-official.apk') != previous_digest:
        raise EvidenceError('In-place upgrade evidence contains a different previous official APK.')
    upgrade_rows, captures = verified_upgrade(job, result, result.parent / 'captures', commit, digest,
                                               previous_tag, previous_digest, raw=True)
    files['upgrade/result.properties'] = result
    files['upgrade/job.properties'] = job
    files.update((f'screenshots/{phase}/{png.name}', png) for phase, png in captures)
    summary = release_summary(version, commit, digest, journeys, configurations, previous_tag, previous_digest, upgrade_rows)
    output = BytesIO()
    with ZipFile(output, 'w', compression=ZIP_DEFLATED) as archive:
        archive.writestr('verification.md', summary)
        for name, path in sorted(files.items()):
            archive.write(path, name)
    return output.getvalue()


def release_summary(version, commit, digest, journeys, configurations, previous_tag, previous_digest, upgrade_rows):
    matrix = matrix_summary(version, commit, digest, 'success', len(JOURNEYS) * len(CONFIGURATIONS),
                            True, journeys, configurations)
    return '\n'.join([
        '# Android release verification', '', matrix, '## In-place Android upgrade', '',
        f'**Passed** · Official APK `{previous_tag}` · SHA-256 `{previous_digest}`', '',
        'Saved rating and ranking history remained visible after the in-place update and offline restart.', '',
        '| Phase | Instrumentation | Screenshots |', '| --- | --- | --- |', *upgrade_rows, '',
    ])


def check_archived_evidence(contents, version, commit, digest, previous_tag, previous_digest):
    try:
        with ZipFile(BytesIO(contents)) as archive:
            names = archive.namelist()
            if len(set(names)) != len(names):
                raise EvidenceError('The uploaded verification archive contains duplicate paths.')
            root = ZipPath(archive)
            _, journeys, configurations = fixture_evidence(root, commit, digest)
            rows, _ = verified_upgrade(root / 'upgrade/job.properties', root / 'upgrade/result.properties',
                                       root / 'screenshots', commit, digest, previous_tag, previous_digest)
            summary = root / 'verification.md'
            expected = release_summary(version, commit, digest, journeys, configurations, previous_tag, previous_digest, rows)
            # A retry can retain evidence from an earlier Actions run.
            run_link = re.compile(r'^\[Run and artifacts\]\(https?://[^)\n]+/actions/runs/[0-9]+\)', re.MULTILINE)
            if not summary.is_file() or run_link.sub('', summary.read_text()) != run_link.sub('', expected):
                raise EvidenceError('The uploaded verification summary does not match the installed evidence.')
    except BadZipFile as failure:
        raise EvidenceError('The uploaded verification archive is invalid.') from failure


def verified_upgrade(job, record, screenshots, commit, digest, previous_tag, previous_digest, *, raw=False):
    if properties(job).get('installed') != 'success':
        raise EvidenceError('In-place upgrade evidence is missing or incomplete.')
    result = properties(record)
    test = 'SavedHistoryUpgradeDeviceTest'
    if not (matches_candidate(result, test, commit, digest)
            and result.get('status') == result.get('upgrade') == 'passed'
            and result.get('previous.tag') == previous_tag
            and SHA256.fullmatch(result.get('testApk.sha256', ''))
            and result.get('previous.sha256') == previous_digest
            and result.get('upgrade-after.offline') == 'confirmed'):
        raise EvidenceError('In-place upgrade evidence does not verify the candidate and previous official APK.')

    rows = []
    captures = []
    for phase, installed_digest in (('upgrade-before', previous_digest), ('upgrade-after', digest)):
        directory = screenshots / phase
        paths = directory.rglob(f'{phase}-*.png') if raw else bundle_pngs(directory, phase)
        tests, collection, passed, files = configuration_status(phase, paths, result, test)
        if not (passed
                and result.get(f'{phase}.install') == 'passed'
                and result.get(f'{phase}.installedSha256') == installed_digest):
            raise EvidenceError(f'In-place upgrade evidence for {phase} failed validation ({tests}, {collection}).')
        rows.append(f'| {phase} | {tests} | {collection} |')
        captures.extend((phase, png) for png in files.values())
    return rows, captures
