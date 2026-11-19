import base64
from concurrent.futures import ThreadPoolExecutor
import hashlib
import http.server
from io import BytesIO
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import threading
import unittest
from urllib import error, parse, request
from zipfile import ZipFile


REPO = Path(__file__).resolve().parents[2]
SCRIPTS = REPO / 'scripts/android'
REPOSITORY = '/repos/example/TableTennisAz'
NOTES = ('- Released change.\n\n### Results\n\nRead the latest results.\n\n'
         '~~~markdown\n## Example heading\n~~~\n\nOpen a match for details.\n')


def resolve(template, variables):
    def value(match):
        choices = match[1].split(' || ')
        last = choices[-1]
        default = variables.get(last, '') if last.startswith('steps.') and '.outputs.' in last else variables[last]
        return next((variables[name] for name in choices[:-1] if variables.get(name)), default)

    return re.sub(r'\$\{\{\s*([^}]+?)\s*\}\}', value, template)


class GitHub(http.server.ThreadingHTTPServer):
    def __init__(self):
        super().__init__(('127.0.0.1', 0), GitHubRequest)
        self.url = f'http://127.0.0.1:{self.server_port}'
        self.tags = {}
        self.releases = {}
        self.files = {}
        self.downloads = {}
        self.lock = threading.Lock()
        self.interruption = None
        self.publish_token = None


class GitHubRequest(http.server.BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def do_GET(self):
        self.respond()

    def do_POST(self):
        self.respond()

    def do_PATCH(self):
        self.respond()

    def do_DELETE(self):
        self.respond()

    def respond(self):
        path = parse.urlsplit(self.path)
        raw = self.rfile.read(int(self.headers.get('Content-Length', 0)))
        with self.server.lock:
            status, response = self.route(path.path.removeprefix(REPOSITORY), parse.parse_qs(path.query), raw)
        self.send_response(status)
        if isinstance(response, bytes):
            self.end_headers()
            self.wfile.write(response)
        else:
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            if status != 204:
                self.wfile.write(json.dumps(response).encode())

    def route(self, path, query, raw):
        server = self.server
        if self.command != 'GET' and server.publish_token is not None:
            if self.headers.get('Authorization') != f'Bearer {server.publish_token}':
                return 403, {'message': 'Workflows permission is required'}
        if self.command == 'GET':
            if path == '/releases':
                return 200, list(reversed(server.releases.values()))
            if path == '/releases/latest':
                for release in reversed(server.releases.values()):
                    if not release['draft'] and not release['prerelease']:
                        return 200, release
            if path.startswith('/git/ref/'):
                ref = path.removeprefix('/git/ref/')
                if ref in server.tags:
                    return 200, {'object': {'type': 'commit', 'sha': server.tags[ref]}}
            if path == '/contents/version.properties':
                ref = query['ref'][0]
                sha = server.tags.get(ref.removeprefix('refs/'), ref)
                if sha in server.files:
                    return 200, {'content': base64.b64encode(server.files[sha]).decode()}
            if path.startswith('/releases/assets/'):
                asset_id = int(path.split('/')[-1])
                return 200, server.downloads[asset_id]
            if path.startswith('/releases/') and path.endswith('/assets'):
                release_id = int(path.split('/')[-2])
                return 200, server.releases[release_id]['assets']
        if self.command == 'POST':
            if path == '/git/refs':
                ref = json.loads(raw)
                name = ref['ref'].removeprefix('refs/')
                if name in server.tags:
                    return 422, {'message': 'Reference already exists'}
                server.tags[name] = ref['sha']
                return 201, ref
            if path == '/releases':
                release = json.loads(raw)
                release_id = len(server.releases) + 1
                release.update(id=release_id, assets=[], html_url=f'{server.url}/release/{release_id}',
                               upload_url=f'{server.url}/uploads/{release_id}{{?name,label}}')
                server.releases[release_id] = release
                return 201, release
            if path.startswith('/uploads/'):
                release_id = int(path.split('/')[-1])
                assets = server.releases[release_id]['assets']
                name = query['name'][0]
                if any(asset['name'] == name for asset in assets):
                    return 422, {'message': 'Asset already exists'}
                asset_id = len(server.downloads) + 1
                if server.interruption == f'upload:{name}':
                    server.interruption = None
                    assets.append({'id': asset_id, 'name': name, 'state': 'starter', 'size': 0})
                    return 502, {'message': 'Interrupted upload'}
                server.downloads[asset_id] = raw
                asset = {'id': asset_id, 'name': name, 'state': 'uploaded', 'size': len(raw),
                         'digest': 'sha256:' + hashlib.sha256(raw).hexdigest()}
                assets.append(asset)
                return 201, asset
        if self.command == 'PATCH' and path.startswith('/releases/'):
            changes = json.loads(raw)
            if changes.get('draft') is False and server.interruption == 'publish':
                server.interruption = None
                return 503, {'message': 'Interrupted publication'}
            release = server.releases[int(path.split('/')[-1])]
            if changes.get('draft') is False:
                tag = release['tag_name']
                # GitHub can create the tag when an untagged draft is published.
                server.tags.setdefault(f'tags/{tag}', release['target_commitish'])
                release['draft'] = False
            if 'body' in changes:
                release['body'] = changes['body']
            return 200, release
        if self.command == 'DELETE' and path.startswith('/releases/assets/'):
            asset_id = int(path.split('/')[-1])
            for release in server.releases.values():
                release['assets'] = [asset for asset in release['assets'] if asset['id'] != asset_id]
            server.downloads.pop(asset_id, None)
            return 204, None
        return 404, {'message': 'Not Found'}


class ReleaseFixture(unittest.TestCase):
    def setUp(self):
        self.scratch = tempfile.TemporaryDirectory(prefix='android-publication-')
        self.addCleanup(self.scratch.cleanup)
        self.source = Path(self.scratch.name) / 'source'
        self.source.mkdir()
        self.github = GitHub()
        thread = threading.Thread(target=self.github.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(self.github.server_close)
        self.addCleanup(self.github.shutdown)
        self.output = Path(self.scratch.name) / 'candidate'
        self.env = dict(os.environ, GITHUB_API_URL=self.github.url, GITHUB_REPOSITORY='example/TableTennisAz',
                        GITHUB_EVENT_NAME='workflow_dispatch', GITHUB_REF='refs/heads/main', GH_TOKEN='fixture')
        self.git('init', '-q', '-b', 'main')
        self.git('config', 'user.name', 'Release test')
        self.git('config', 'user.email', 'release-test@example.org')

    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.source, text=True).strip()

    def commit(self, version='0.4.0', code=4, heading=None):
        properties = self.source / 'version.properties'
        properties.write_text(f'versionName={version}\nversionCode={code}\n')
        (self.source / 'CHANGELOG.md').write_text(
            f'# Changelog\n\n## Unreleased\n\n- Future change.\n\n'
            f'{heading or f"## {version} - 2026-11-19"}\n\n- Released change.\n\n'
            '### Results\n\nRead the latest results.\n\n~~~markdown\n## Example heading\n~~~\n\n'
            'Open a match for details.\n\n## 0.3.0 - 2026-11-18\n\n- Earlier change.\n')
        self.git('add', 'version.properties', 'CHANGELOG.md')
        self.git('commit', '-q', '-m', f'Prepare {version}', '--allow-empty')
        sha = self.git('rev-parse', 'HEAD')
        self.env['GITHUB_SHA'] = sha
        self.github.files[sha] = properties.read_bytes()
        return sha

    def run_script(self, script, *args):
        return subprocess.run([SCRIPTS / script, *args], cwd=self.source, env=self.env, text=True,
                              capture_output=True, timeout=60)

    def api(self, path):
        try:
            with request.urlopen(f'{self.github.url}{REPOSITORY}{path}', timeout=5) as response:
                return json.load(response)
        except error.HTTPError as failure:
            failure.close()
            raise


class MetadataTest(ReleaseFixture):
    def test_selects_notes_for_first_and_subsequent_versions(self):
        for previous, version, code in ((None, '0.4.0', 4), ('0.9.0', '0.10.0', 9)):
            with self.subTest(version=version):
                # arrange
                if previous is not None:
                    sha = self.commit(previous, code - 1)
                    tag = f'v{previous}'
                    self.github.tags[f'tags/{tag}'] = sha
                    self.github.releases[1] = {'tag_name': tag, 'draft': False, 'prerelease': False}
                self.commit(version, code)

                # act
                result = self.run_script('validate-release-candidate', self.output)

                # assert
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(result.stdout, f'version_name={version}\nversion_code={code}\n')
                self.assertEqual((self.output / 'release-notes.md').read_text(), NOTES)

    def test_rejects_invalid_version_and_changelog(self):
        # arrange
        previous = self.commit('0.9.0', 8)
        self.github.tags['tags/v0.9.0'] = previous
        self.github.releases[1] = {'tag_name': 'v0.9.0', 'draft': False, 'prerelease': False}
        cases = (
            ('0.010.0', 9, None, 'Expected MAJOR.MINOR.PATCH'),
            ('0.8.1', 9, None, 'versionName must be greater than 0.9.0'),
            ('0.10.0', 10, None, 'versionCode must be 9 after 0.9.0'),
            ('0.10.0', 9, '## 0.10.0', 'Missing dated changelog section for 0.10.0'),
            ('0.10.0', 9, '## 0.10.1 - 2026-11-20', 'Missing dated changelog section for 0.10.0'),
            ('0.10.0', 9, '## 0.10.0 - 2026-02-30', 'Invalid changelog date for 0.10.0'),
        )
        for version, code, heading, message in cases:
            with self.subTest(version=version, code=code, heading=heading):
                self.commit(version, code, heading)

                # act
                result = self.run_script('validate-release-candidate', self.output)

                # assert
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(message, result.stderr)
                self.assertEqual(result.stdout, '')
                self.assertFalse((self.output / 'release-notes.md').exists())

    def test_conflicting_tag_fails_validation(self):
        # arrange
        sha = self.commit()
        self.github.tags['tags/v0.4.0'] = 'a' * 40

        # act
        result = self.run_script('validate-release-candidate', self.output)

        # assert
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(f'Tag v0.4.0 points to {"a" * 40}, expected {sha}.', result.stderr)
        self.assertEqual(self.api('/releases'), [])
        self.assertEqual(self.api('/git/ref/tags/v0.4.0')['object']['sha'], 'a' * 40)


class WorkflowTest(ReleaseFixture):
    def setUp(self):
        super().setUp()
        from yaml import safe_load

        self.workflow = safe_load((REPO / '.github/workflows/android-release.yml').read_text())
        self.workflow_slots = {}
        self.sdk = Path(os.environ['ANDROID_HOME'])
        self.tools = self.sdk / 'build-tools/36.0.0'
        self.key = Path(self.scratch.name) / 'signing.p12'
        password = os.urandom(16).hex()
        self.env.update(ANDROID_RELEASE_KEY_ALIAS='publication-test', ANDROID_RELEASE_KEYSTORE_PASSWORD=password,
                        ANDROID_RELEASE_KEY_PASSWORD=password)
        self.android_command('keytool', '-genkeypair', '-storetype', 'PKCS12', '-keyalg', 'RSA', '-keysize', '2048',
                             '-validity', '2', '-keystore', self.key, '-storepass:env', 'ANDROID_RELEASE_KEYSTORE_PASSWORD',
                             '-alias', 'publication-test', '-dname', 'CN=Publication test')
        self.env['ANDROID_RELEASE_KEYSTORE_BASE64'] = base64.b64encode(self.key.read_bytes()).decode()
        self.configure_source(self.source)

    def android_command(self, *args):
        result = subprocess.run(args, env=self.env, text=True, capture_output=True, timeout=60)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def configure_source(self, source):
        if not (source / 'scripts').exists():
            shutil.copytree(SCRIPTS, source / 'scripts/android')
        wrapper = source / 'gradlew'
        wrapper.write_text('''#!/usr/bin/env bash
set -euo pipefail
case "$*" in
  ":app:testDebugUnitTest :app:lintDebug") exit "${FIXTURE_CHECK_STATUS:-0}" ;;
  ":app:assembleRelease --no-configuration-cache")
    if [[ -z "$FIXTURE_APK" ]]; then
      exec ./gradlew-real "$@"
    fi
    mkdir -p app/build/outputs/apk/release
    cp "$FIXTURE_APK" app/build/outputs/apk/release/app-release.apk
    ;;
  *) exit 2 ;;
esac
''')
        wrapper.chmod(0o755)

    def use_gradle_project(self):
        directories = ('app', 'gradle')
        files = ('build.gradle', 'gradle.properties', 'settings.gradle')
        for directory in directories:
            shutil.copytree(REPO / directory, self.source / directory, ignore=shutil.ignore_patterns('build', '.gradle'))
        for file in files:
            shutil.copy2(REPO / file, self.source / file)
        shutil.copy2(REPO / 'gradlew', self.source / 'gradlew-real')
        self.git('add', *directories, *files, 'gradlew-real')

    def apk(self, version='0.4.0', variant='original', signed=True, code=4):
        out = Path(self.scratch.name) / f'{version}-{variant}'
        out.mkdir()
        manifest = out / 'AndroidManifest.xml'
        manifest.write_text(f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
                            f'package="az.tabletennis" android:versionCode="{code}" android:versionName="{version}">'
                            '<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="36"/>'
                            f'<application android:label="{variant}"/></manifest>')
        unsigned = out / 'unsigned.apk'
        self.android_command(self.tools / 'aapt2', 'link', '-I', self.sdk / 'platforms/android-36/android.jar',
                             '--manifest', manifest, '-o', unsigned)
        if not signed:
            return unsigned
        apk = out / 'signed.apk'
        self.android_command(self.tools / 'apksigner', 'sign', '--ks', self.key, '--ks-key-alias', 'publication-test',
                             '--ks-pass', 'env:ANDROID_RELEASE_KEYSTORE_PASSWORD', '--out', apk, unsigned)
        return apk

    def run_workflow(self, apk=None, *, source=None, sha=None, check_status=0, verify_only=None,
                     fixture_failure='', evidence=None):
        source = source or self.source
        if verify_only is None:
            triggers = self.workflow.get('on', self.workflow.get(True))  # PyYAML reads `on` as true.
            verify_only = triggers['workflow_dispatch']['inputs']['verify_only']['default']
        env = dict(self.env, GITHUB_SHA=sha or self.env['GITHUB_SHA'], FIXTURE_APK=str(apk) if apk else '',
                   FIXTURE_CHECK_STATUS=str(check_status), FIXTURE_FAILURE=fixture_failure,
                   GITHUB_SERVER_URL='https://github.example', GITHUB_REPOSITORY='example/TableTennisAz',
                   GITHUB_RUN_ID='42')
        variables = {'github.token': env['GH_TOKEN'], 'github.ref': env['GITHUB_REF'], 'github.sha': env['GITHUB_SHA']}
        variables['github.workspace'] = str(source)
        variables['inputs.verify_only'] = 'true' if verify_only else 'false'
        variables.update({f'secrets.{name}': value for name, value in env.items() if name.startswith('ANDROID_RELEASE_')})
        # Run the workflow's shell steps with local per-ref scheduling and artifact transfer.
        concurrency = self.workflow['concurrency']
        self.assertEqual(concurrency['cancel-in-progress'], False)
        group = resolve(concurrency['group'], variables)
        slot = self.workflow_slots.setdefault(group, threading.Lock())
        with slot, tempfile.TemporaryDirectory(dir=self.scratch.name) as runner:
            artifacts = Path(runner) / 'artifacts'
            completed = {}
            verification_failure = None
            for job_id, job in self.workflow['jobs'].items():
                needs = job.get('needs', [])
                if isinstance(needs, str):
                    needs = [needs]
                dependencies_passed = all(completed[dependency] == 'success' for dependency in needs)
                conditions = {
                    None: dependencies_passed,
                    'inputs.verify_only': verify_only and dependencies_passed,
                    'inputs.verify_only == false': not verify_only and dependencies_passed,
                    'always()': True,
                    'always() && inputs.verify_only': verify_only,
                }
                if not conditions[job.get('if')]:
                    completed[job_id] = 'skipped'
                    variables[f'needs.{job_id}.result'] = 'skipped'
                    continue
                strategy = job.get('strategy')
                rows = [{}]
                if strategy:
                    self.assertIs(strategy.get('fail-fast'), False)
                    matrix = re.fullmatch(r'\$\{\{ fromJSON\((.+)\) \}\}', strategy['matrix'])[1]
                    rows = json.loads(variables[matrix])['include']
                for index, row in enumerate(rows):
                    job_vars = variables | {f'matrix.{name}': value for name, value in row.items()}
                    result = self.run_job(job_id, job, job_vars, Path(runner) / f'{job_id}-{index}', source, env, artifacts)
                    completed.setdefault(job_id, 'success')
                    if result.returncode:
                        if job_id not in ('verify-signed', 'fixture-summary', 'verify-upgrade'):
                            return result
                        verification_failure = verification_failure or result
                        completed[job_id] = 'failure'
                    for name, value in job.get('outputs', {}).items():
                        variables[f'needs.{job_id}.outputs.{name}'] = resolve(value, job_vars)
                variables[f'needs.{job_id}.result'] = completed[job_id]
            if evidence is not None:
                shutil.copytree(artifacts, evidence)
        return verification_failure or result

    def run_job(self, job_id, job, variables, job_dir, source, env, artifacts):
        job_dir.mkdir()
        job_output = job_dir / 'outputs'
        variables['runner.temp'] = str(job_dir)
        workflow_env = self.workflow.get('env', {}) | job.get('env', {})
        job_env = {name: val for name, val in env.items() if not name.startswith('ANDROID_RELEASE_')}
        job_env.update({name: resolve(value, variables) for name, value in workflow_env.items()})
        job_env.update(RUNNER_TEMP=str(job_dir), GITHUB_OUTPUT=str(job_output), GITHUB_STEP_SUMMARY=str(job_dir / 'summary'))
        variables.update({f'env.{name}': job_env[name] for name in workflow_env})
        failure = None
        for step in job['steps']:
            if step.get('id') == 'kvm':
                continue  # The local fixture substitutes installed verification.
            if step.get('id'):
                variables[f"steps.{step['id']}.outcome"] = 'skipped'
            if failure and step.get('if') != 'always()':
                continue
            if step.get('if') == 'inputs.verify_only == false' and variables['inputs.verify_only'] == 'true':
                continue
            action = step.get('uses', '').partition('@')[0]
            inputs = step.get('with', {})
            if action == 'actions/checkout':
                self.assertEqual(resolve(inputs['ref'], variables), env['GITHUB_SHA'])
            elif action in ('actions/upload-artifact', 'actions/download-artifact'):
                path = Path(resolve(inputs['path'], variables))
                if 'pattern' in inputs:
                    for artifact in artifacts.glob(resolve(inputs['pattern'], variables)):
                        shutil.copytree(artifact, path / artifact.name)
                else:
                    artifact = artifacts / resolve(inputs['name'], variables)
                    if action == 'actions/upload-artifact':
                        shutil.copytree(path, artifact)
                    else:
                        shutil.copytree(artifact, path)
                        if job_id == 'publish' and env['FIXTURE_FAILURE'] == 'different-candidate' and artifact.name.endswith('-candidate'):
                            apk, = path.glob('*.apk')
                            apk.write_bytes(self.replacement.read_bytes())
                        if job_id == 'publish' and env['FIXTURE_FAILURE'] == 'missing-bundled-png' and artifact.name == 'android-signed-fixture-matrix':
                            (path / 'screenshots/match/light/light-502.png').unlink()
            elif 'run' in step:
                step_env = dict(job_env, **{name: resolve(value, variables) for name, value in step.get('env', {}).items()})
                result = subprocess.run(['bash', '-e', '-o', 'pipefail', '-c', step['run']],
                                        env=step_env, cwd=source, text=True, capture_output=True, timeout=600)
                if step.get('id'):
                    variables[f"steps.{step['id']}.outcome"] = 'failure' if result.returncode else 'success'
                    if job_output.exists():
                        for line in job_output.read_text().splitlines():
                            key, value = line.split('=', 1)
                            variables[f"steps.{step['id']}.outputs.{key}"] = value
                if result.returncode and failure is None:
                    failure = result
        return failure or result

    def assert_release(self, release, tag, sha, apk):
        self.assertFalse(release['draft'])
        self.assertFalse(release['prerelease'])
        self.assertEqual(release['tag_name'], tag)
        self.assertEqual(self.api(f'/git/ref/tags/{tag}')['object']['sha'], sha)
        assets = self.api(f"/releases/{release['id']}/assets")
        name = f'TableTennisAz-{tag[1:]}'
        self.assertEqual({asset['name'] for asset in assets}, {f'{name}.apk', f'{name}-verification.zip'})
        files = {}
        for asset in assets:
            with request.urlopen(f"{self.github.url}{REPOSITORY}/releases/assets/{asset['id']}", timeout=5) as response:
                files[asset['name']] = response.read()
        self.assertEqual(files[f'{name}.apk'], apk.read_bytes())
        apk_digest = hashlib.sha256(apk.read_bytes()).hexdigest()
        evidence = files[f'{name}-verification.zip']
        self.assertEqual(release['body'],
                         f'{NOTES}\n<!-- APK for {sha}: sha256:{apk_digest}; evidence sha256:{hashlib.sha256(evidence).hexdigest()} -->\n')
        with ZipFile(BytesIO(evidence)) as archive:
            summary = archive.read('verification.md').decode()
            for detail in (f'`{name}.apk`', sha, apk_digest, '24/24', 'In-place Android upgrade', '`v0.4.0`'):
                self.assertIn(detail, summary)
            first_capture = {'match': '501', 'profile': 'profile', 'rating-history': 'chart', 'results': 'results',
                             'doubles-profile': 'profile', 'native-acceptance': 'players'}
            for journey, screen in first_capture.items():
                for mode in ('light', 'dark-offline', 'narrow-large', 'landscape-large'):
                    fixture = self.source / 'fixture-screenshots' / f'{mode}-501.png'
                    self.assertEqual(archive.read(f'screenshots/{journey}/{mode}/{mode}-{screen}.png'), fixture.read_bytes())
            for phase in ('upgrade-before', 'upgrade-after'):
                for screen in ('rating', 'ranking'):
                    self.assertIn(f'screenshots/{phase}/{phase}-{screen}.png', archive.namelist())
        return assets

    def install_fixture_verifier(self):
        screenshots = self.source / 'fixture-screenshots'
        screenshots.mkdir()
        for configuration in ('light', 'dark-offline', 'narrow-large', 'landscape-large'):
            name = f'{configuration}-501.png'
            shutil.copy2(REPO / 'docs/verification/android-workflow/screenshots' / name, screenshots)
        verifier = self.source / 'scripts/android-verify'
        verifier.write_text('''#!/usr/bin/env bash
set -euo pipefail
[[ -z "${ANDROID_RELEASE_KEYSTORE_BASE64:-}" ]]
run="$ANDROID_VERIFY_OUTPUT/run-fixture"
mkdir -p "$run"
if [[ "$1 $2" == '--require-kvm --release-candidate' ]]; then
  [[ "$5 $7" == '--upgrade-from v0.4.0' ]]
  actual=$(sha256sum "$3")
  [[ "${actual%% *}" == "$4" ]]
  cp "$3" "$run/app-release.apk"
  cp "$6" "$run/previous-official.apk"
  if [[ "$FIXTURE_FAILURE" == upgrade-wrong-baseline ]]; then cp "$3" "$run/previous-official.apk"; fi
  previous=$(sha256sum "$run/previous-official.apk")
  printf 'apk.sha256=%s\\nsource.commit=%s\\nprevious.tag=%s\\nprevious.sha256=%s\\n' \\
    "$4" "$GITHUB_SHA" "$7" "${previous%% *}" > "$run/result.properties"
  printf 'testApk.sha256=%064d\\napi=36\\napk.variant=release\\ntests=az.tabletennis.SavedHistoryUpgradeDeviceTest\\n' 2 >> "$run/result.properties"
  for phase in upgrade-before upgrade-after; do
    if [[ "$phase" == upgrade-after && "$FIXTURE_FAILURE" == upgrade-incomplete ]]; then exit 0; fi
    digest="$4"
    if [[ "$phase" == upgrade-before ]]; then digest="${previous%% *}"; fi
    if [[ "$phase" == upgrade-after && "$FIXTURE_FAILURE" == upgrade-wrong-apk ]]; then digest="${previous%% *}"; fi
    printf '%s.install=passed\\n%s.installedSha256=%s\\n%s.instrumentation=passed\\n%s.tests=OK (1 test)\\n%s.screenshotCollection=passed\\n' \\
      "$phase" "$phase" "$digest" "$phase" "$phase" "$phase" >> "$run/result.properties"
    printf '%s.expectedScreenshots=%s-rating.png,%s-ranking.png\\n' "$phase" "$phase" "$phase" >> "$run/result.properties"
    mkdir -p "$run/captures/$phase/files"
    for screen in rating ranking; do
      png="$phase-$screen.png"
      fixture=fixture-screenshots/light-501.png
      capture=$(sha256sum "$fixture")
      printf '%s.screenshotSha256.%s=%s\\n' "$phase" "$png" "${capture%% *}" >> "$run/result.properties"
      if [[ "$phase/$screen" == upgrade-after/ranking && "$FIXTURE_FAILURE" == upgrade-missing-png ]]; then continue; fi
      cp "$fixture" "$run/captures/$phase/files/$png"
    done
  done
  printf 'upgrade-after.offline=confirmed\\nstatus=passed\\nupgrade=passed\\n' >> "$run/result.properties"
  exit 0
fi
[[ "$*" == '--require-kvm --device-only --tests az.tabletennis.'*' --release-candidate '* ]]
actual=$(sha256sum "$6")
[[ "${actual%% *}" == "$7" ]]
cp "$6" "$run/app-release.apk"
printf 'apk.sha256=%s\\nsource.commit=%s\\napi=36\\ntests=%s\\napk.variant=release\\n' "$7" "$GITHUB_SHA" "$4" > "$run/result.properties"
printf 'testApk.sha256=%064d\\npreflight=passed\\n' 1 >> "$run/result.properties"
screens=$(grep -F "$4=" scripts/android/fixture-captures.properties | cut -d= -f2)
IFS=, read -ra screens <<< "$screens"
for configuration in light dark-offline narrow-large landscape-large; do
  fixture="fixture-screenshots/$configuration-501.png"
  captures=()
  for screen in "${screens[@]}"; do
    captures+=("$configuration-$screen.png")
  done
  if [[ "$JOURNEY/$configuration" == "results/landscape-large" && "$FIXTURE_FAILURE" == reduced-screenshot-list ]]; then
    captures=("${captures[0]}")
  fi
  expected=$(IFS=,; printf '%s' "${captures[*]}")
  printf '%s.expectedScreenshots=%s\\n' "$configuration" "$expected" >> "$run/result.properties"
  if [[ "$JOURNEY/$configuration" == "results/landscape-large" && "$FIXTURE_FAILURE" == instrumentation ]]; then
    printf 'landscape-large.instrumentation=failed\\nlandscape-large.screenshotCollection=not-run\\nstatus=failed\\nmatrix=failed\\n' >> "$run/result.properties"
    printf 'Fixture instrumentation failed\\n' > "$run/tests-landscape-large.log"
    exit 1
  fi
  printf '%s.instrumentation=passed\\n%s.tests=OK (2 tests)\\n%s.screenshotCollection=passed\\n' \\
    "$configuration" "$configuration" "$configuration" >> "$run/result.properties"
  checksum=$(sha256sum "$fixture")
  mkdir -p "$run/captures/$configuration/files"
  for capture in "${captures[@]}"; do
    printf '%s.screenshotSha256.%s=%s\\n' "$configuration" "$capture" "${checksum%% *}" >> "$run/result.properties"
    if [[ "$JOURNEY/$capture" == "results/landscape-large-empty.png" ]]; then
      if [[ "$FIXTURE_FAILURE" == missing-png ]]; then continue; fi
      if [[ "$FIXTURE_FAILURE" == corrupt-png ]]; then
        cp fixture-screenshots/light-501.png "$run/captures/$configuration/files/$capture"
        continue
      fi
    fi
    cp "$fixture" "$run/captures/$configuration/files/$capture"
  done
done
printf 'status=passed\\nmatrix=passed\\n' >> "$run/result.properties"
''')
        verifier.chmod(0o755)

    def given_official_apk(self, released_commit):
        official_apk = self.apk(variant='official')
        contents = official_apk.read_bytes()
        asset = {'id': 1, 'name': 'TableTennisAz-0.4.0.apk', 'state': 'uploaded', 'size': len(contents),
                 'digest': 'sha256:' + hashlib.sha256(contents).hexdigest()}
        release = {'id': 1, 'tag_name': 'v0.4.0', 'draft': False, 'prerelease': False, 'assets': [asset]}
        self.github.tags['tags/v0.4.0'] = released_commit
        self.github.releases[1] = dict(release)
        self.github.downloads[1] = contents
        return release, official_apk

    def test_verify_only_covers_the_full_matrix_and_upgrade_without_changing_the_existing_release(self):
        # arrange
        released_commit = self.commit()
        released_version, official_apk = self.given_official_apk(released_commit)
        sha = self.commit()
        apk = self.apk()
        self.install_fixture_verifier()
        evidence = Path(self.scratch.name) / 'evidence'

        # act
        result = self.run_workflow(apk, evidence=evidence)

        # assert
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        journeys = {'match': ('Match', '501'), 'profile': ('Profile', 'profile'),
                    'rating-history': ('RatingHistory', 'chart'), 'results': ('Results', 'results'),
                    'doubles-profile': ('DoublesProfile', 'profile'), 'native-acceptance': ('NativeAcceptance', 'players')}
        bundle = evidence / 'android-signed-fixture-matrix'
        summary = (bundle / 'verification.md').read_text()
        self.assertIn(f'TableTennisAz-0.4.0.apk', summary)
        self.assertIn(sha, summary)
        self.assertIn(hashlib.sha256(apk.read_bytes()).hexdigest(), summary)
        self.assertIn('24/24', summary)
        for journey, (class_prefix, screen) in journeys.items():
            run = evidence / f'android-signed-api36-{journey}' / 'run-fixture'
            self.assertEqual((run / 'app-release.apk').read_bytes(), apk.read_bytes())
            self.assertIn(f'tests=az.tabletennis.{class_prefix}DeviceTest', (run / 'result.properties').read_text())
            for configuration in ('light', 'dark-offline', 'narrow-large', 'landscape-large'):
                self.assertTrue((bundle / 'screenshots' / journey / configuration / f'{configuration}-{screen}.png').is_file())
        upgrade = evidence / 'android-signed-upgrade'
        upgrade_run = upgrade / 'run-fixture'
        self.assertEqual((upgrade_run / 'app-release.apk').read_bytes(), apk.read_bytes())
        self.assertEqual((upgrade_run / 'previous-official.apk').read_bytes(), official_apk.read_bytes())
        upgrade_summary = (upgrade / 'verification.md').read_text()
        self.assertIn('upgrade=passed', upgrade_summary)
        self.assertIn('upgrade-before.instrumentation=passed', upgrade_summary)
        self.assertIn('upgrade-after.instrumentation=passed', upgrade_summary)
        self.assertEqual(self.api('/releases'), [released_version])
        self.assertEqual(self.api('/git/ref/tags/v0.4.0')['object']['sha'], released_commit)

    def test_incomplete_matrix_preserves_available_evidence(self):
        # arrange
        released_version, _ = self.given_official_apk(self.commit())
        self.commit('0.5.0', 5)
        apk = self.apk('0.5.0', code=5)
        self.install_fixture_verifier()
        for failure, outcome in (('instrumentation', 'failed'), ('missing-png', 'missing'), ('corrupt-png', 'mismatch'),
                                 ('reduced-screenshot-list', 'incorrect required screenshot list')):
            with self.subTest(failure=failure):
                evidence = Path(self.scratch.name) / failure

                # act
                result = self.run_workflow(apk, verify_only=False, fixture_failure=failure, evidence=evidence)

                # assert
                self.assertNotEqual(result.returncode, 0)
                bundle = evidence / 'android-signed-fixture-matrix'
                summary = (bundle / 'verification.md').read_text()
                self.assertIn('23/24', summary)
                self.assertRegex(summary, rf'(?m)^\| results \| landscape-large \| .*{outcome}')
                self.assertTrue((bundle / 'screenshots/results/light/light-results.png').is_file())
                self.assertFalse((bundle / 'screenshots/results/landscape-large/landscape-large-empty.png').exists())
                self.assertTrue((bundle / 'screenshots/native-acceptance/landscape-large/landscape-large-players.png').is_file())
                self.assertEqual(self.api('/releases'), [released_version])
                with self.assertRaisesRegex(error.HTTPError, '404'):
                    self.api('/git/ref/tags/v0.5.0')

    def test_incomplete_upgrade_fails_and_preserves_before_upgrade_evidence(self):
        # arrange
        released_version, _ = self.given_official_apk(self.commit())
        self.commit('0.5.0', 5)
        apk = self.apk('0.5.0', code=5)
        self.install_fixture_verifier()
        evidence = Path(self.scratch.name) / 'evidence'

        # act
        result = self.run_workflow(apk, verify_only=False, fixture_failure='upgrade-incomplete', evidence=evidence)

        # assert
        self.assertNotEqual(result.returncode, 0)
        upgrade = evidence / 'android-signed-upgrade'
        self.assertIn('installed=failure', (upgrade / 'job.properties').read_text())
        self.assertIn('upgrade-before.instrumentation=passed', (upgrade / 'verification.md').read_text())
        self.assertIn('24/24', (evidence / 'android-signed-fixture-matrix' / 'verification.md').read_text())
        self.assertEqual(self.api('/releases'), [released_version])
        with self.assertRaisesRegex(error.HTTPError, '404'):
            self.api('/git/ref/tags/v0.5.0')

    def test_publication_revalidates_the_downloaded_apk_and_evidence(self):
        # arrange
        released_version, _ = self.given_official_apk(self.commit())
        self.commit('0.5.0', 5)
        apk = self.apk('0.5.0', code=5)
        self.replacement = self.apk('0.5.0', code=5, variant='untested')
        self.install_fixture_verifier()
        cases = (
            ('different-candidate', 'Publication candidate APK SHA-256 mismatch'),
            ('missing-bundled-png', 'Fixture evidence for match does not verify the candidate'),
            ('upgrade-wrong-baseline', 'In-place upgrade evidence contains a different previous official APK'),
            ('upgrade-wrong-apk', 'In-place upgrade evidence for upgrade-after failed validation'),
            ('upgrade-missing-png', 'In-place upgrade evidence for upgrade-after failed validation'),
        )
        for failure, message in cases:
            with self.subTest(failure=failure):
                # act
                result = self.run_workflow(apk, verify_only=False, fixture_failure=failure)

                # assert
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(message, result.stderr)
                self.assertEqual(self.api('/releases'), [released_version])
                with self.assertRaisesRegex(error.HTTPError, '404'):
                    self.api('/git/ref/tags/v0.5.0')

    def test_missing_official_apk_blocks_upgrade_and_preserves_summary(self):
        # arrange
        released_version, _ = self.given_official_apk(self.commit())
        released_version['assets'].clear()
        apk = self.apk()
        self.install_fixture_verifier()
        evidence = Path(self.scratch.name) / 'evidence'

        # act
        result = self.run_workflow(apk, evidence=evidence)

        # assert
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Expected one uploaded TableTennisAz-0.4.0.apk', result.stderr)
        upgrade = evidence / 'android-signed-upgrade'
        self.assertIn('installed=skipped', (upgrade / 'job.properties').read_text())
        self.assertIn('Official release `unavailable`', (upgrade / 'verification.md').read_text())
        self.assertEqual(self.api('/releases'), [released_version])

    def test_publishes_and_preserves_a_completed_release(self):
        # arrange
        self.given_official_apk(self.commit())
        self.use_gradle_project()
        sha = self.commit('0.5.0', 5)
        self.install_fixture_verifier()
        built_apk = self.source / 'app/build/outputs/apk/release/app-release.apk'

        # act
        result = self.run_workflow(verify_only=False)

        # assert
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        release = self.api('/releases/latest')
        assets = self.assert_release(release, 'v0.5.0', sha, built_apk)

        # arrange
        original_apk = Path(self.scratch.name) / 'first-release.apk'
        shutil.copyfile(built_apk, original_apk)
        self.configure_source(self.source)
        rebuilt_apk = self.apk('0.5.0', code=5, variant='rebuilt')

        # act
        retry = self.run_workflow(rebuilt_apk, verify_only=False)

        # assert
        self.assertEqual(retry.returncode, 0, retry.stderr)
        self.assertEqual(self.assert_release(self.api('/releases/latest'), 'v0.5.0', sha, original_apk), assets)
        self.assertEqual(len(self.api('/releases')), 2)

    def test_failed_gates_leave_no_release_or_tag(self):
        # arrange
        signed = self.apk()
        unsigned = self.apk(variant='unsigned', signed=False)
        cases = ((5, signed, 0, 'versionCode must be 4'), (4, signed, 12, ''), (4, unsigned, 0, 'DOES NOT VERIFY'))
        for code, apk, host_status, message in cases:
            with self.subTest(code=code, host_status=host_status, apk=apk):
                self.commit(code=code)

                # act
                result = self.run_workflow(apk, check_status=host_status, verify_only=False)

                # assert
                self.assertNotEqual(result.returncode, 0)
                if host_status:
                    self.assertEqual(result.returncode, host_status)
                self.assertIn(message, result.stderr)
                self.assertEqual(self.api('/releases'), [])
                with self.assertRaisesRegex(error.HTTPError, '404'):
                    self.api('/git/ref/tags/v0.4.0')

    def test_concurrent_versions_cannot_reuse_an_android_version_code(self):
        # arrange
        self.given_official_apk(self.commit())
        first_sha = self.commit('0.5.0', 5)
        first_apk = self.apk('0.5.0', code=5)
        second_sha = self.commit('0.6.0', 5)
        second_apk = self.apk('0.6.0', code=5)
        self.install_fixture_verifier()
        first_source = Path(self.scratch.name) / 'first'
        self.git('worktree', 'add', '-q', '--detach', str(first_source), first_sha)
        self.configure_source(first_source)
        shutil.copy2(self.source / 'scripts/android-verify', first_source / 'scripts/android-verify')
        shutil.copytree(self.source / 'fixture-screenshots', first_source / 'fixture-screenshots')
        candidates = (('v0.5.0', first_sha, first_apk, first_source), ('v0.6.0', second_sha, second_apk, self.source))
        start = threading.Barrier(len(candidates))

        def attempt(candidate):
            _, sha, apk, source = candidate
            start.wait(timeout=5)
            return self.run_workflow(apk, source=source, sha=sha, verify_only=False)

        # act
        with ThreadPoolExecutor(max_workers=len(candidates)) as threads:
            results = list(threads.map(attempt, candidates))

        # assert
        self.assertEqual(sorted(result.returncode for result in results), [0, 1])
        winner = results[0].returncode
        tag, sha, apk, _ = candidates[winner]
        self.assert_release(self.api('/releases/latest'), tag, sha, apk)
        self.assertEqual(len(self.api('/releases')), 2)
        with self.assertRaisesRegex(error.HTTPError, '404'):
            self.api(f'/git/ref/tags/{candidates[1 - winner][0]}')

    def test_retry_recovers_partial_publication(self):
        # arrange
        previous_commit = self.commit()
        previous, _ = self.given_official_apk(previous_commit)
        self.install_fixture_verifier()
        cases = (
            ('upload:TableTennisAz-0.5.0.apk', ()),
            ('upload:TableTennisAz-0.5.0-verification.zip', ('TableTennisAz-0.5.0.apk',)),
            ('publish', ('TableTennisAz-0.5.0.apk', 'TableTennisAz-0.5.0-verification.zip')),
        )
        for index, (interruption, uploaded_names) in enumerate(cases):
            with self.subTest(interruption=interruption):
                # arrange
                self.github.releases = {1: dict(previous)}
                self.github.tags = {'tags/v0.4.0': previous_commit}
                self.github.interruption = interruption
                sha = self.commit('0.5.0', 5)
                apk = self.apk('0.5.0', code=5, variant=f'interrupted-{index}')
                retry_apk = self.apk('0.5.0', code=5, variant=f'retry-{index}')

                # act
                result = self.run_workflow(apk, verify_only=False)

                # assert
                self.assertNotEqual(result.returncode, 0)
                draft, official = self.api('/releases')
                self.assertTrue(draft['draft'])
                self.assertEqual(official, previous)
                self.assertEqual(self.api('/releases/latest'), previous)
                self.assertEqual(self.api('/git/ref/tags/v0.5.0')['object']['sha'], sha)
                uploaded = {asset['name']: asset for asset in draft['assets'] if asset['state'] == 'uploaded'}
                self.assertEqual(set(uploaded), set(uploaded_names))

                # arrange
                if interruption == 'publish':
                    other_apk = self.apk('0.6.0', code=5)
                    self.commit('0.6.0', 5)

                    # act
                    other_attempt = self.run_workflow(other_apk, verify_only=False)

                    # assert
                    self.assertNotEqual(other_attempt.returncode, 0)
                    self.assertIn('Finish draft v0.5.0', other_attempt.stderr)
                    self.assertEqual(self.api('/releases'), [draft, previous])

                    # arrange
                    self.git('checkout', '-q', '--detach', sha)
                    self.env['GITHUB_SHA'] = sha
                    self.github.publish_token = 'fixture-workflows-write'
                    self.env['ANDROID_RELEASE_GITHUB_TOKEN'] = self.github.publish_token

                # act
                retry = self.run_workflow(retry_apk, verify_only=False)

                # assert
                self.assertEqual(retry.returncode, 0, retry.stderr)
                release = self.api('/releases/latest')
                self.assertEqual(release['id'], draft['id'])
                assets = self.assert_release(release, 'v0.5.0', sha, apk if uploaded_names else retry_apk)
                self.assertEqual({asset['name']: asset for asset in assets if asset['name'] in uploaded}, uploaded)

    def test_retry_rejects_replaced_draft_assets(self):
        # arrange
        self.given_official_apk(self.commit())
        self.commit('0.5.0', 5)
        self.install_fixture_verifier()
        original = self.apk('0.5.0', code=5)
        substitute_apk = self.apk('0.5.0', code=5, variant='substitute').read_bytes()
        self.github.interruption = 'publish'
        attempt = self.run_workflow(original, verify_only=False)
        self.assertNotEqual(attempt.returncode, 0)
        draft = self.github.releases[2]
        for asset in draft['assets']:
            name = asset['name']
            with self.subTest(asset=name):
                # arrange
                uploaded = self.github.downloads[asset['id']]
                metadata = asset.copy()
                body = draft['body']
                if name.endswith('.apk'):
                    substitute = substitute_apk
                    message = f'uploaded asset {name} does not match the recorded checksum'
                else:
                    replacement = BytesIO()
                    with ZipFile(BytesIO(uploaded)) as verified, ZipFile(replacement, 'w') as incomplete:
                        for member in verified.namelist():
                            if member != 'screenshots/match/light/light-502.png':
                                incomplete.writestr(member, verified.read(member))
                    substitute = replacement.getvalue()
                    draft['body'] = body.replace(hashlib.sha256(uploaded).hexdigest(),
                                                 hashlib.sha256(substitute).hexdigest())
                    message = 'Fixture evidence for match does not verify the candidate'
                self.github.downloads[asset['id']] = substitute
                asset.update(size=len(substitute), digest='sha256:' + hashlib.sha256(substitute).hexdigest())

                # act
                try:
                    retry = self.run_workflow(original, verify_only=False)
                finally:
                    self.github.downloads[asset['id']] = uploaded
                    asset.update(metadata)
                    draft['body'] = body

                # assert
                self.assertNotEqual(retry.returncode, 0)
                self.assertIn(message, retry.stderr)
                self.assertTrue(self.api('/releases')[0]['draft'])

        # arrange
        replacement_key = self.key.with_name('replacement.p12')
        self.android_command('keytool', '-genkeypair', '-storetype', 'PKCS12', '-keyalg', 'RSA', '-keysize', '2048',
                             '-validity', '2', '-keystore', replacement_key, '-storepass:env', 'ANDROID_RELEASE_KEYSTORE_PASSWORD',
                             '-alias', 'publication-test', '-dname', 'CN=Replacement key')
        self.env['ANDROID_RELEASE_KEYSTORE_BASE64'] = base64.b64encode(replacement_key.read_bytes()).decode()

        # act
        retry = self.run_workflow(original, verify_only=False)

        # assert
        self.assertNotEqual(retry.returncode, 0)
        self.assertIn('staged APK was signed by a different release key', retry.stderr)
        self.assertTrue(self.api('/releases')[0]['draft'])


if __name__ == '__main__':
    unittest.main()
