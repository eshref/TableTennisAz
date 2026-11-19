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


SCRIPTS = Path(__file__).resolve().parent
OFFICIAL_TAG = re.compile(r'v[0-9]+\.[0-9]+\.[0-9]+')
DRAFT_APK = re.compile(r'\n<!-- APK for ([0-9a-f]{40}): sha256:[0-9a-f]{64} -->\n\Z')


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


def draft_body(notes, commit, contents):
    digest = hashlib.sha256(contents).hexdigest()
    return f'{notes}\n<!-- APK for {commit}: sha256:{digest} -->\n'


def publish(github, candidate, tools, commit):
    with tempfile.TemporaryDirectory() as scratch:
        validated = Path(scratch)
        _, version = validate(github, validated, commit)
        notes = (validated / 'release-notes.md').read_text()
        if notes != (candidate / 'release-notes.md').read_text():
            raise ReleaseError('Candidate release notes differ from the checked-out changelog.')

        tag = f"v{version['version_name']}"
        apk = candidate / f"TableTennisAz-{version['version_name']}.apk"
        signers = verify_apk(apk, tools, version)
        if not signers:
            raise ReleaseError(f'No signing certificates reported for {apk.name}.')

        candidate_apk = apk.read_bytes()
        candidate_body = draft_body(notes, commit, candidate_apk)
        releases = list(github.releases())
        matching = [release for release in releases if release['tag_name'] == tag]
        if len(matching) > 1:
            raise ReleaseError(f'GitHub has multiple releases for {tag}.')

        tagged_commit = github.tag_commit(tag)
        check_tag(tag, tagged_commit, commit)
        if matching:
            release = matching[0]
            release_notes = release['body'] or ''
            marker = DRAFT_APK.search(release_notes) if release['draft'] else None
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

        if tagged_commit is None:
            # A draft reserves the release before the tag is created. Creating a ref
            # fails if it already exists. The workflow never updates or deletes refs.
            tagged_commit = github.tag_commit(tag)
            if tagged_commit is None:
                github.call('POST', '/git/refs', {'ref': f'refs/tags/{tag}', 'sha': commit})
            check_tag(tag, github.tag_commit(tag), commit)

        release_id = release['id']
        assets = github.call('GET', f'/releases/{release_id}/assets?per_page=100')
        if len(assets) > 1 or (assets and assets[0]['name'] != apk.name):
            raise ReleaseError(f'Release {tag} contains unexpected assets.')
        if assets and release['draft'] and assets[0]['state'] == 'starter' and assets[0]['size'] == 0:
            # GitHub can leave an empty placeholder when an upload fails with HTTP 502.
            github.call('DELETE', f"/releases/assets/{assets[0]['id']}")
            assets = []

        if assets:
            asset = assets[0]
            uploaded = github.call('GET', f"/releases/assets/{asset['id']}", binary=True)
            check_asset(asset, apk.name, uploaded)
            if release['draft'] and release['body'] != draft_body(notes, commit, uploaded):
                raise ReleaseError(f'The uploaded APK does not match the APK recorded for the draft {tag}.')
            existing = validated / apk.name
            existing.write_bytes(uploaded)
            if verify_apk(existing, tools, version) != signers:
                raise ReleaseError(f'Release {tag} contains an APK signed by a different key.')
        elif release['draft']:
            # A rebuilt candidate can differ in bytes on retry. Record its digest
            # only when the draft has no uploaded APK.
            if release['body'] != candidate_body:
                github.call('PATCH', f'/releases/{release_id}', {'body': candidate_body})
            upload_url = release['upload_url'].split('{', 1)[0]
            asset = github.call('POST', f'{upload_url}?{parse.urlencode({"name": apk.name})}', candidate_apk,
                                content_type='application/vnd.android.package-archive')
            check_asset(asset, apk.name, candidate_apk)
        else:
            raise ReleaseError(f'Published release {tag} is missing {apk.name}.')

        if release['draft']:
            if github.tag_commit(tag) != commit:
                raise ReleaseError(f'Tag {tag} must point to {commit} before publication.')
            release = github.call('PATCH', f'/releases/{release_id}',
                                  {'draft': False, 'body': notes, 'make_latest': 'true'})
            if release['draft'] or release['body'] != notes:
                raise ReleaseError(f'GitHub did not publish {tag} with the selected release notes.')
        print(f"Release {tag} is published at {release['html_url']}")


def check_asset(asset, name, contents):
    digest = 'sha256:' + hashlib.sha256(contents).hexdigest()
    if (asset['name'] != name or asset['state'] != 'uploaded' or not contents or asset['size'] != len(contents)
            or (asset.get('digest') and asset['digest'] != digest)):
        raise ReleaseError(f'GitHub did not confirm the complete APK {name}.')


def download_previous(github, output):
    release = github.call('GET', '/releases/latest', missing_ok=True)
    if release is None or release['draft'] or release['prerelease'] or not OFFICIAL_TAG.fullmatch(release['tag_name']):
        raise ReleaseError('No previous official Android release is available for upgrade verification.')
    tag = release['tag_name']
    name = f'TableTennisAz-{tag[1:]}.apk'
    assets = [asset for asset in release['assets'] if asset['name'] == name and asset['state'] == 'uploaded']
    if len(assets) != 1:
        raise ReleaseError(f'Expected one uploaded {name} in the official release {tag}.')
    asset = assets[0]
    contents = github.call('GET', f"/releases/assets/{asset['id']}", binary=True)
    check_asset(asset, name, contents)
    output.write_bytes(contents)
    print(f'tag={tag}')


def main():
    parser = argparse.ArgumentParser()
    actions = parser.add_subparsers(dest='action', required=True)
    validate_command = actions.add_parser('validate')
    validate_command.add_argument('output', type=Path)
    validate_command.add_argument('--verify-only', action='store_true')
    previous_command = actions.add_parser('download-previous')
    previous_command.add_argument('output', type=Path)
    publish_command = actions.add_parser('publish')
    publish_command.add_argument('candidate', type=Path)
    publish_command.add_argument('tools', type=Path)
    args = parser.parse_args()
    try:
        commit = checked_commit()
        github = GitHub()
        if args.action == 'validate':
            metadata, _ = validate(github, args.output, commit, verify_only=args.verify_only)
            print(metadata, end='')
        elif args.action == 'download-previous':
            download_previous(github, args.output)
        else:
            publish(github, args.candidate, args.tools, commit)
    except (ReleaseError, OSError, KeyError, subprocess.CalledProcessError) as failure:
        print(failure, file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
