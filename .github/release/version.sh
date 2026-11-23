#!/usr/bin/env bash
set -euo pipefail
version_name="$(sed -n 's/^[[:space:]]*versionName = "\([^"]*\)"/\1/p' app/build.gradle.kts)"
version_code="$(git rev-list --count HEAD)"
test "$TAG" = "v$version_name" || { echo 'Tag must match versionName'; exit 1; }
gh api --paginate "repos/$GH_REPO/releases?per_page=100" > "$RUNNER_TEMP/thor-releases.json"
python3 - "$RUNNER_TEMP/thor-releases.json" "$TAG" "$version_code" <<'PYCHECK'
import json, re, sys
# --paginate emits consecutive arrays. Decode each page rather than assuming one.
s = open(sys.argv[1]).read(); releases = []; decoder = json.JSONDecoder()
while s.strip():
    page, end = decoder.raw_decode(s.lstrip()); releases.extend(page); s = s.lstrip()[end:]
code = int(sys.argv[3])
for release in releases:
    if release['draft']: continue
    match = re.search(r'^versionCode: (\d+)\s*$', release.get('body') or '', re.M)
    if not match: raise SystemExit('Published release is missing versionCode; resolve before publishing')
    previous = int(match[1])
    if release['tag_name'] == sys.argv[2]:
        if previous != code: raise SystemExit('Existing tag has another versionCode')
    elif code <= previous: raise SystemExit('versionCode must exceed every earlier release')
PYCHECK
printf 'VERSION_NAME=%s\nVERSION_CODE=%s\n' "$version_name" "$version_code" >> "$GITHUB_ENV"
