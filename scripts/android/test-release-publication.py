import base64
from concurrent.futures import ThreadPoolExecutor
import hashlib
import http.server
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


REPO = Path(__file__).resolve().parents[2]
SCRIPTS = REPO / 'scripts/android'
REPOSITORY = '/repos/example/TableTennisAz'
NOTES = ('- Released change.\n\n### Results\n\nRead the latest results.\n\n'
         '~~~markdown\n## Example heading\n~~~\n\nOpen a match for details.\n')


def resolve(template, variables):
    def value(match):
        choices = match[1].split(' || ')
        return next((variables[name] for name in choices[:-1] if variables.get(name)), variables[choices[-1]])

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
                if server.interruption == 'upload':
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
    mkdir -p app/build/outputs/apk/release
    cp "$FIXTURE_APK" app/build/outputs/apk/release/app-release.apk
    ;;
  *) exit 2 ;;
esac
''')
        wrapper.chmod(0o755)

    def use_gradle_project(self):
        directories = ('app', 'gradle')
        files = ('build.gradle', 'gradle.properties', 'gradlew', 'settings.gradle')
        for directory in directories:
            shutil.copytree(REPO / directory, self.source / directory, ignore=shutil.ignore_patterns('build', '.gradle'))
        for file in files:
            shutil.copy2(REPO / file, self.source / file)
        self.git('add', *directories, *files)

    def apk(self, version='0.4.0', variant='original', signed=True):
        out = Path(self.scratch.name) / f'{version}-{variant}'
        out.mkdir()
        manifest = out / 'AndroidManifest.xml'
        manifest.write_text(f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
                            f'package="az.tabletennis" android:versionCode="4" android:versionName="{version}">'
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
        env = dict(self.env, GITHUB_SHA=sha or self.env['GITHUB_SHA'], FIXTURE_APK=str(apk),
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
            conditions = {'inputs.verify_only': verify_only, 'inputs.verify_only == false': not verify_only,
                          'always() && inputs.verify_only': verify_only}
            for job_id, job in self.workflow['jobs'].items():
                if 'if' in job and not conditions[job['if']]:
                    continue
                needs = job.get('needs', [])
                self.assertTrue(all(dependency in completed for dependency in ([needs] if isinstance(needs, str) else needs)))
                strategy = job.get('strategy')
                rows = [{}]
                if strategy:
                    self.assertIs(strategy.get('fail-fast'), False)
                    matrix = re.fullmatch(r'\$\{\{ fromJSON\((.+)\) \}\}', strategy['matrix'])[1]
                    rows = json.loads(variables[matrix])['include']
                for index, row in enumerate(rows):
                    job_vars = variables | {f'matrix.{name}': value for name, value in row.items()}
                    result = self.run_job(job, job_vars, Path(runner) / f'{job_id}-{index}', source, env, artifacts)
                    if result.returncode and job_id not in ('verify-signed', 'fixture-summary'):
                        return result
                    completed.setdefault(job_id, 'success')
                    if result.returncode:
                        completed[job_id] = 'failure'
                    for name, value in job.get('outputs', {}).items():
                        variables[f'needs.{job_id}.outputs.{name}'] = resolve(value, job_vars)
                variables[f'needs.{job_id}.result'] = completed[job_id]
            if evidence is not None:
                shutil.copytree(artifacts, evidence)
        return result

    def run_job(self, job, variables, job_dir, source, env, artifacts):
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
        self.assertEqual(release['body'], NOTES)
        self.assertEqual(self.api(f'/git/ref/tags/{tag}')['object']['sha'], sha)
        assets = self.api(f"/releases/{release['id']}/assets")
        self.assertEqual(len(assets), 1)
        asset = assets[0]
        self.assertEqual(asset['name'], f'TableTennisAz-{tag[1:]}.apk')
        with request.urlopen(f"{self.github.url}{REPOSITORY}/releases/assets/{asset['id']}", timeout=5) as response:
            self.assertEqual(response.read(), apk.read_bytes())
        return asset

    def install_fixture_verifier(self):
        screenshots = self.source / 'fixture-screenshots'
        screenshots.mkdir()
        for configuration in ('light', 'dark-offline', 'narrow-large', 'landscape-large'):
            name = f'{configuration}-501.png'
            shutil.copy2(REPO / 'docs/verification/android-workflow/screenshots' / name, screenshots)
        verifier = self.source / 'scripts/android-verify'
        verifier.write_text('''#!/usr/bin/env bash
set -euo pipefail
[[ "$*" == '--require-kvm --device-only --tests az.tabletennis.'*' --release-candidate '* ]]
[[ -z "${ANDROID_RELEASE_KEYSTORE_BASE64:-}" ]]
actual=$(sha256sum "$6")
[[ "${actual%% *}" == "$7" ]]
run="$ANDROID_VERIFY_OUTPUT/run-fixture"
mkdir -p "$run"
cp "$6" "$run/app-release.apk"
printf 'apk.sha256=%s\\nsource.commit=%s\\napi=36\\ntests=%s\\napk.variant=release\\n' "$7" "$GITHUB_SHA" "$4" > "$run/result.properties"
printf 'testApk.sha256=%064d\\npreflight=passed\\n' 1 >> "$run/result.properties"
for configuration in light dark-offline narrow-large landscape-large; do
  capture="$configuration-screen.png"
  fixture="fixture-screenshots/$configuration-501.png"
  printf '%s.expectedScreenshots=%s\\n' "$configuration" "$capture" >> "$run/result.properties"
  if [[ "$JOURNEY/$configuration" == "results/landscape-large" && "$FIXTURE_FAILURE" == instrumentation ]]; then
    printf 'landscape-large.instrumentation=failed\\nlandscape-large.screenshotCollection=not-run\\nstatus=failed\\nmatrix=failed\\n' >> "$run/result.properties"
    printf 'Fixture instrumentation failed\\n' > "$run/tests-landscape-large.log"
    exit 1
  fi
  printf '%s.instrumentation=passed\\n%s.tests=OK (2 tests)\\n%s.screenshotCollection=passed\\n' \\
    "$configuration" "$configuration" "$configuration" >> "$run/result.properties"
  checksum=$(sha256sum "$fixture")
  printf '%s.screenshotSha256.%s=%s\\n' "$configuration" "$capture" "${checksum%% *}" >> "$run/result.properties"
  if [[ "$JOURNEY/$configuration" == "results/landscape-large" && "$FIXTURE_FAILURE" == missing-png ]]; then
    continue
  fi
  mkdir -p "$run/captures/$configuration/files"
  if [[ "$JOURNEY/$configuration" == "results/landscape-large" && "$FIXTURE_FAILURE" == corrupt-png ]]; then
    fixture=fixture-screenshots/light-501.png
  fi
  cp "$fixture" "$run/captures/$configuration/files/$capture"
done
printf 'status=passed\\nmatrix=passed\\n' >> "$run/result.properties"
''')
        verifier.chmod(0o755)

    def test_verify_only_covers_the_full_matrix_without_changing_the_existing_release(self):
        # arrange
        released_commit = self.commit()
        self.github.tags['tags/v0.4.0'] = released_commit
        released_version = {'id': 1, 'tag_name': 'v0.4.0', 'draft': False, 'prerelease': False, 'assets': []}
        self.github.releases[1] = dict(released_version)
        sha = self.commit()
        apk = self.apk()
        self.install_fixture_verifier()
        evidence = Path(self.scratch.name) / 'evidence'

        # act
        result = self.run_workflow(apk, evidence=evidence)

        # assert
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        journeys = {'match': 'Match', 'profile': 'Profile', 'rating-history': 'RatingHistory', 'results': 'Results',
                    'doubles-profile': 'DoublesProfile', 'native-acceptance': 'NativeAcceptance'}
        bundle = evidence / 'android-signed-fixture-matrix'
        summary = (bundle / 'verification.md').read_text()
        self.assertIn(f'TableTennisAz-0.4.0.apk', summary)
        self.assertIn(sha, summary)
        self.assertIn(hashlib.sha256(apk.read_bytes()).hexdigest(), summary)
        self.assertIn('24/24', summary)
        for journey, class_prefix in journeys.items():
            run = evidence / f'android-signed-api36-{journey}' / 'run-fixture'
            self.assertEqual((run / 'app-release.apk').read_bytes(), apk.read_bytes())
            self.assertIn(f'tests=az.tabletennis.{class_prefix}DeviceTest', (run / 'result.properties').read_text())
            for configuration in ('light', 'dark-offline', 'narrow-large', 'landscape-large'):
                self.assertTrue((bundle / 'screenshots' / journey / configuration / f'{configuration}-screen.png').is_file())
        self.assertEqual(self.api('/releases'), [released_version])
        self.assertEqual(self.api('/git/ref/tags/v0.4.0')['object']['sha'], released_commit)

    def test_incomplete_matrix_preserves_available_evidence(self):
        # arrange
        self.commit()
        apk = self.apk()
        self.install_fixture_verifier()
        for failure, outcome in (('instrumentation', 'failed'), ('missing-png', 'missing'), ('corrupt-png', 'mismatch')):
            with self.subTest(failure=failure):
                evidence = Path(self.scratch.name) / failure

                # act
                result = self.run_workflow(apk, fixture_failure=failure, evidence=evidence)

                # assert
                self.assertNotEqual(result.returncode, 0)
                bundle = evidence / 'android-signed-fixture-matrix'
                summary = (bundle / 'verification.md').read_text()
                self.assertIn('23/24', summary)
                self.assertRegex(summary, rf'(?m)^\| results \| landscape-large \| .*{outcome}')
                self.assertTrue((bundle / 'screenshots/results/light/light-screen.png').is_file())
                self.assertFalse((bundle / 'screenshots/results/landscape-large/landscape-large-screen.png').exists())
                self.assertTrue((bundle / 'screenshots/native-acceptance/landscape-large/landscape-large-screen.png').is_file())
                self.assertEqual(self.api('/releases'), [])

    def test_publishes_and_preserves_a_completed_release(self):
        # arrange
        self.use_gradle_project()
        sha = self.commit()
        built_apk = self.source / 'app/build/outputs/apk/release/app-release.apk'

        # act
        result = self.run_workflow(verify_only=False)

        # assert
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        release = self.api('/releases/latest')
        asset = self.assert_release(release, 'v0.4.0', sha, built_apk)

        # arrange
        original_apk = Path(self.scratch.name) / 'first-release.apk'
        shutil.copyfile(built_apk, original_apk)
        self.configure_source(self.source)
        rebuilt_apk = self.apk(variant='rebuilt')

        # act
        retry = self.run_workflow(rebuilt_apk, verify_only=False)

        # assert
        self.assertEqual(retry.returncode, 0, retry.stderr)
        self.assertEqual(self.assert_release(self.api('/releases/latest'), 'v0.4.0', sha, original_apk), asset)
        self.assertEqual(len(self.api('/releases')), 1)

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
        first_sha = self.commit()
        first_apk = self.apk()
        second_sha = self.commit('0.5.0')
        second_apk = self.apk('0.5.0')
        first_source = Path(self.scratch.name) / 'first'
        self.git('worktree', 'add', '-q', '--detach', str(first_source), first_sha)
        self.configure_source(first_source)
        candidates = (('v0.4.0', first_sha, first_apk, first_source), ('v0.5.0', second_sha, second_apk, self.source))
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
        self.assertEqual(len(self.api('/releases')), 1)
        with self.assertRaisesRegex(error.HTTPError, '404'):
            self.api(f'/git/ref/tags/{candidates[1 - winner][0]}')

    def test_retry_recovers_partial_publication(self):
        for interruption in ('upload', 'publish'):
            with self.subTest(interruption=interruption):
                # arrange
                self.github.releases.clear()
                self.github.tags.clear()
                self.github.interruption = interruption
                sha = self.commit()
                apk = self.apk(variant=interruption)
                retry_apk = self.apk(variant=f'{interruption}-retry')

                # act
                result = self.run_workflow(apk, verify_only=False)

                # assert
                self.assertNotEqual(result.returncode, 0)
                draft, = self.api('/releases')
                self.assertTrue(draft['draft'])
                with self.assertRaisesRegex(error.HTTPError, '404'):
                    self.api('/releases/latest')
                self.assertEqual(self.api('/git/ref/tags/v0.4.0')['object']['sha'], sha)

                # arrange
                if interruption == 'publish':
                    other_apk = self.apk('0.5.0')
                    self.commit('0.5.0')

                    # act
                    other_attempt = self.run_workflow(other_apk, verify_only=False)

                    # assert
                    self.assertNotEqual(other_attempt.returncode, 0)
                    self.assertIn('Finish draft v0.4.0', other_attempt.stderr)
                    self.assertEqual(self.api('/releases'), [draft])

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
                if interruption == 'publish':
                    self.assertEqual(self.assert_release(release, 'v0.4.0', sha, apk), draft['assets'][0])
                else:
                    self.assert_release(release, 'v0.4.0', sha, retry_apk)

    def test_retry_rejects_a_draft_asset_that_was_not_uploaded_by_the_workflow(self):
        # arrange
        self.commit()
        original = self.apk()
        substitute = self.apk(variant='substitute').read_bytes()
        self.github.interruption = 'publish'
        attempt = self.run_workflow(original, verify_only=False)
        self.assertNotEqual(attempt.returncode, 0)
        release_id, = self.github.releases
        asset, = self.github.releases[release_id]['assets']
        self.github.downloads[asset['id']] = substitute
        asset.update(size=len(substitute), digest='sha256:' + hashlib.sha256(substitute).hexdigest())

        # act
        retry = self.run_workflow(original, verify_only=False)

        # assert
        self.assertNotEqual(retry.returncode, 0)
        self.assertIn('does not match the APK recorded for the draft', retry.stderr)
        self.assertTrue(self.api('/releases')[0]['draft'])


if __name__ == '__main__':
    unittest.main()
