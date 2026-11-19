import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
from urllib import error, parse, request

from release_evidence import EvidenceError, check_archived_evidence, verification_archive


SCRIPTS = Path(__file__).resolve().parent
OFFICIAL_TAG = re.compile(r'v[0-9]+\.[0-9]+\.[0-9]+')
RELEASE_ASSETS = re.compile(
    r'\n<!-- APK for ([0-9a-f]{40}): sha256:([0-9a-f]{64})(?:; evidence sha256:([0-9a-f]{64}))? -->\n\Z')


class ReleaseError(Exception):
    pass


class GitHub:
    def __init__(self):
        self.repository = f"{os.environ['GITHUB_API_URL']}/repos/{os.environ['GITHUB_REPOSITORY']}"
        self.token = os.environ['GH_TOKEN']

    def call(self, method, path, data=None, *, missing_ok=False, binary=False, content_type='application/json'):
        url = self.repository + path if path.startswith('/') else path
        if content_type == 'application/json' and data is not None:
            data = json.dumps(data).encode()
        headers = {
            'Accept': 'application/octet-stream' if binary else 'application/vnd.github+json',
            'X-GitHub-Api-Version': '2022-11-28',
            'Content-Type': content_type,
        }
        query = request.Request(url, data=data, headers=headers, method=method)
        # Release asset downloads may redirect to a signed URL on a different host.
        query.add_unredirected_header('Authorization', f'Bearer {self.token}')
        try:
            with request.urlopen(query, timeout=120) as response:
                body = response.read()
                return body if binary else json.loads(body) if body else None
        except error.HTTPError as failure:
            failure.close()
            if missing_ok and failure.code == 404:
                return None
            raise ReleaseError(f'GitHub {method} {path} failed with HTTP {failure.code}.') from failure

    def releases(self):
        page = 1
        while True:
            releases = self.call('GET', f'/releases?per_page=100&page={page}')
            yield from releases
            if len(releases) < 100:
                return
            page += 1

    def tag_commit(self, tag):
        ref = self.call('GET', f'/git/ref/tags/{parse.quote(tag, safe="")}', missing_ok=True)
        if ref is None:
            return None
        target = ref['object']
        while target['type'] == 'tag':
            target = self.call('GET', f"/git/tags/{target['sha']}")['object']
        if target['type'] != 'commit':
            raise ReleaseError(f'Tag {tag} does not point to a commit.')
        return target['sha']


def checked_commit():
    if os.environ.get('GITHUB_EVENT_NAME') != 'workflow_dispatch' or os.environ.get('GITHUB_REF') != 'refs/heads/main':
        raise ReleaseError('Dispatch the Android release workflow from main.')
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
    if commit != os.environ.get('GITHUB_SHA'):
        raise ReleaseError(f'Checked-out commit {commit} does not match the dispatch commit.')
    return commit


def check_tag(tag, tagged_commit, commit):
    if tagged_commit is not None and tagged_commit != commit:
        raise ReleaseError(f'Tag {tag} points to {tagged_commit}, expected {commit}.')


def validate(github, output, commit, *, verify_only=False):
    latest = github.call('GET', '/releases/latest', missing_ok=True)
    command = ['java', str(SCRIPTS / 'ReleaseMetadata.java'), str(output)]
    with tempfile.TemporaryDirectory() as scratch:
        if latest is None:
            if any(not release['draft'] and not release['prerelease'] for release in github.releases()):
                raise ReleaseError('GitHub has official releases but no latest release.')
        else:
            previous_tag = latest['tag_name']
            query = parse.urlencode({'ref': f'refs/tags/{previous_tag}'})
            previous = github.call('GET', f'/contents/version.properties?{query}')
            path = Path(scratch) / 'version.properties'
            path.write_bytes(base64.b64decode(previous['content']))
            command.extend([previous_tag, str(path)])
        metadata = subprocess.check_output(command, text=True)

    version = dict(line.split('=', 1) for line in metadata.splitlines())
    if not verify_only:
        tag = f"v{version['version_name']}"
        tagged_commit = github.tag_commit(tag)
        check_tag(tag, tagged_commit, commit)
        if latest is not None and latest['tag_name'] == tag and tagged_commit is None:
            raise ReleaseError(f'Published release {tag} is missing its tag.')
    return metadata, version


def verify_apk(apk, tools, version):
    env = dict(os.environ, RELEASE_VERSION_NAME=version['version_name'], RELEASE_VERSION_CODE=version['version_code'])
    output = subprocess.check_output([SCRIPTS / 'verify-release-apk', apk, tools], env=env, text=True)
    return re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)$', output, flags=re.MULTILINE)


def asset_names(tag):
    name = f'TableTennisAz-{tag[1:]}'
    return f'{name}.apk', f'{name}-verification.zip'


def release_body(notes, commit, apk_digest, evidence_digest):
    return f'{notes}\n<!-- APK for {commit}: sha256:{apk_digest}; evidence sha256:{evidence_digest} -->\n'


def release_assets(github, release, names):
    assets = {}
    for asset in github.call('GET', f"/releases/{release['id']}/assets?per_page=100"):
        name = asset['name']
        if name not in names or name in assets:
            raise ReleaseError(f"Release {release['tag_name']} contains unexpected assets.")
        assets[name] = asset
    return assets


def downloaded_asset(github, asset, name, expected_digest):
    contents = github.call('GET', f"/releases/assets/{asset['id']}", binary=True)
    check_asset(asset, name, contents)
    if hashlib.sha256(contents).hexdigest() != expected_digest:
        raise ReleaseError(f'The uploaded asset {name} does not match the recorded checksum.')
    return contents


def empty_upload(asset):
    return asset['state'] == 'starter' and asset['size'] == 0


def reuse_candidate(github, candidate, version, commit):
    tag = f'v{version}'
    release = next((release for release in github.releases() if release['tag_name'] == tag), None)
    if release is None:
        return
    names = asset_names(tag)
    apk = release_assets(github, release, names).get(names[0])
    if apk is None or (release['draft'] and empty_upload(apk)):
        return
    marker = RELEASE_ASSETS.search(release['body'] or '')
    if not marker or marker[1] != commit:
        raise ReleaseError(f'Release {tag} has no recorded APK for {commit}.')
    (candidate / names[0]).write_bytes(downloaded_asset(github, apk, names[0], marker[2]))


def publish(github, candidate, tools, fixture, upgrade, expected_digest, commit):
    with tempfile.TemporaryDirectory() as scratch:
        validated = Path(scratch)
        _, version = validate(github, validated, commit)
        notes = (validated / 'release-notes.md').read_text()
        if notes != (candidate / 'release-notes.md').read_text():
            raise ReleaseError('Candidate release notes differ from the checked-out changelog.')

        tag = f"v{version['version_name']}"
        apk_name, evidence_name = asset_names(tag)
        apk = candidate / apk_name
        candidate_apk = apk.read_bytes()
        apk_digest = hashlib.sha256(candidate_apk).hexdigest()
        if apk_digest != expected_digest:
            raise ReleaseError('Publication candidate APK SHA-256 mismatch.')
        if not verify_apk(apk, tools, version):
            raise ReleaseError(f'No signing certificates reported for {apk.name}.')

        previous = previous_official(github, tag)
        previous_tag = previous['tag_name']
        baseline = official_apk_asset(previous)
        previous_digest = baseline.get('digest')
        if not previous_digest:
            previous_digest = 'sha256:' + hashlib.sha256(download_asset(github, baseline)).hexdigest()
        evidence_identity = (commit, apk_digest, previous_tag, previous_digest.removeprefix('sha256:'))
        evidence = verification_archive(fixture, upgrade, version['version_name'], *evidence_identity)
        evidence_digest = hashlib.sha256(evidence).hexdigest()
        candidate_body = release_body(notes, commit, apk_digest, evidence_digest)
        releases = list(github.releases())
        matching = [release for release in releases if release['tag_name'] == tag]
        if len(matching) > 1:
            raise ReleaseError(f'GitHub has multiple releases for {tag}.')

        tagged_commit = github.tag_commit(tag)
        check_tag(tag, tagged_commit, commit)
        if matching:
            release = matching[0]
            release_notes = release['body'] or ''
            marker = RELEASE_ASSETS.search(release_notes)
            if marker and marker[1] == commit:
                release_notes = release_notes[:marker.start()]
            if release['prerelease'] or release_notes != notes:
                raise ReleaseError(f'Existing release {tag} does not match the official release notes.')
            if tagged_commit is None and (not release['draft'] or release['target_commitish'] != commit):
                raise ReleaseError(f'Existing release {tag} is missing its tag at {commit}.')
        else:
            for draft in releases:
                if draft['draft'] and not draft['prerelease'] and OFFICIAL_TAG.fullmatch(draft['tag_name']):
                    raise ReleaseError(f"Finish draft {draft['tag_name']} before starting release {tag}.")
            release = github.call('POST', '/releases', {
                'tag_name': tag, 'target_commitish': commit, 'name': tag, 'body': candidate_body,
                'draft': True, 'prerelease': False,
            })
            marker = RELEASE_ASSETS.search(candidate_body)

        release_id = release['id']
        assets = release_assets(github, release, (apk_name, evidence_name))
        for name, asset in assets.copy().items():
            if release['draft'] and empty_upload(asset):
                # GitHub may leave an empty placeholder after an interrupted upload.
                github.call('DELETE', f"/releases/assets/{asset['id']}")
                del assets[name]

        if apk_name in assets:
            if not marker or marker[2] != apk_digest:
                raise ReleaseError(f'Release {tag} contains an APK different from the verified candidate.')
            downloaded_asset(github, assets[apk_name], apk_name, apk_digest)
        if evidence_name in assets:
            if apk_name not in assets or not marker[3]:
                raise ReleaseError(f'Release {tag} has evidence without a recorded APK and evidence checksum.')
            uploaded_evidence = downloaded_asset(github, assets[evidence_name], evidence_name, marker[3])
            check_archived_evidence(uploaded_evidence, version['version_name'], *evidence_identity)
            # The existing evidence already covers these exact APK bytes and commit.
            candidate_body = release['body']
        if not release['draft'] and len(assets) != 2:
            raise ReleaseError(f'Published release {tag} is missing its APK or verification evidence.')

        if tagged_commit is None:
            # A draft reserves the release before the tag is created. Creating a ref
            # fails if it already exists. The workflow never updates or deletes refs.
            tagged_commit = github.tag_commit(tag)
            if tagged_commit is None:
                github.call('POST', '/git/refs', {'ref': f'refs/tags/{tag}', 'sha': commit})
            check_tag(tag, github.tag_commit(tag), commit)

        if release['draft']:
            if release['body'] != candidate_body:
                github.call('PATCH', f'/releases/{release_id}', {'body': candidate_body})
            upload_url = release['upload_url'].split('{', 1)[0]
            for name, contents, content_type in (
                (apk_name, candidate_apk, 'application/vnd.android.package-archive'),
                (evidence_name, evidence, 'application/zip'),
            ):
                if name not in assets:
                    asset = github.call('POST', f'{upload_url}?{parse.urlencode({"name": name})}', contents,
                                        content_type=content_type)
                    check_asset(asset, name, contents)
            if github.tag_commit(tag) != commit:
                raise ReleaseError(f'Tag {tag} must point to {commit} before publication.')
            release = github.call('PATCH', f'/releases/{release_id}',
                                  {'draft': False, 'body': candidate_body, 'make_latest': 'true'})
            if release['draft'] or release['body'] != candidate_body:
                raise ReleaseError(f'GitHub did not publish {tag} with the selected release notes.')
        print(f"Release {tag} is published at {release['html_url']}")


def check_asset(asset, name, contents):
    digest = 'sha256:' + hashlib.sha256(contents).hexdigest()
    if (asset['name'] != name or asset['state'] != 'uploaded' or not contents or asset['size'] != len(contents)
            or (asset.get('digest') and asset['digest'] != digest)):
        raise ReleaseError(f'GitHub did not confirm the complete asset {name}.')


def previous_official(github, excluded_tag=None):
    release = github.call('GET', '/releases/latest', missing_ok=True)
    if release and release['tag_name'] == excluded_tag:
        # A completed publication may be retried. Use the official version below it.
        versions = {tuple(map(int, entry['tag_name'][1:].split('.'))): entry for entry in github.releases()
                    if not entry['draft'] and not entry['prerelease'] and OFFICIAL_TAG.fullmatch(entry['tag_name'])}
        current = tuple(map(int, excluded_tag[1:].split('.')))
        previous = max((version for version in versions if version < current), default=None)
        release = versions.get(previous)
    if release is None or release['draft'] or release['prerelease'] or not OFFICIAL_TAG.fullmatch(release['tag_name']):
        raise ReleaseError('No previous official Android release is available for upgrade verification.')
    return release


def official_apk_asset(release):
    tag = release['tag_name']
    name = f'TableTennisAz-{tag[1:]}.apk'
    assets = [asset for asset in release['assets'] if asset['name'] == name and asset['state'] == 'uploaded']
    if len(assets) != 1:
        raise ReleaseError(f'Expected one uploaded {name} in the official release {tag}.')
    return assets[0]


def download_asset(github, asset):
    contents = github.call('GET', f"/releases/assets/{asset['id']}", binary=True)
    check_asset(asset, asset['name'], contents)
    return contents


def download_previous(github, output, excluded_tag):
    release = previous_official(github, excluded_tag)
    output.write_bytes(download_asset(github, official_apk_asset(release)))
    print(f"tag={release['tag_name']}")


def main():
    parser = argparse.ArgumentParser()
    actions = parser.add_subparsers(dest='action', required=True)
    validate_command = actions.add_parser('validate')
    validate_command.add_argument('output', type=Path)
    validate_command.add_argument('--verify-only', action='store_true')
    previous_command = actions.add_parser('download-previous')
    previous_command.add_argument('output', type=Path)
    previous_command.add_argument('--exclude-tag')
    reuse_command = actions.add_parser('reuse-candidate')
    reuse_command.add_argument('candidate', type=Path)
    reuse_command.add_argument('version')
    publish_command = actions.add_parser('publish')
    publish_command.add_argument('candidate', type=Path)
    publish_command.add_argument('tools', type=Path)
    publish_command.add_argument('fixture', type=Path)
    publish_command.add_argument('upgrade', type=Path)
    publish_command.add_argument('expected_digest')
    args = parser.parse_args()
    try:
        commit = checked_commit()
        github = GitHub()
        if args.action == 'validate':
            metadata, _ = validate(github, args.output, commit, verify_only=args.verify_only)
            print(metadata, end='')
        elif args.action == 'download-previous':
            download_previous(github, args.output, args.exclude_tag)
        elif args.action == 'reuse-candidate':
            reuse_candidate(github, args.candidate, args.version, commit)
        else:
            publish(github, args.candidate, args.tools, args.fixture, args.upgrade, args.expected_digest, commit)
    except (EvidenceError, ReleaseError, OSError, KeyError, subprocess.CalledProcessError) as failure:
        print(failure, file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
