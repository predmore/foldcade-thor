#!/usr/bin/env bash
set -euo pipefail
apk="$1"
notes="$RUNNER_TEMP/thor-release-notes.md"
printf 'Foldcade Thor %s\n\nversionCode: %s\n' "$VERSION_NAME" "$VERSION_CODE" > "$notes"
if ! gh release view "$TAG" >/dev/null 2>&1; then
  gh release create "$TAG" --verify-tag --draft --title "Foldcade Thor $VERSION_NAME" --notes-file "$notes"
fi
# Keep exactly the documented APK asset, including on a rerun of a draft.
gh release view "$TAG" --json assets --jq '.assets[] | select(.name != "foldcade-thor.apk") | .name' > "$RUNNER_TEMP/thor-extra-assets.txt"
while IFS= read -r asset; do
  test -z "$asset" || gh release delete-asset "$TAG" "$asset" --yes
done < "$RUNNER_TEMP/thor-extra-assets.txt"
gh release upload "$TAG" "$apk#foldcade-thor.apk" --clobber
gh release edit "$TAG" --draft=false --prerelease=false --latest --title "Foldcade Thor $VERSION_NAME" --notes-file "$notes"
gh release view "$TAG" --json isDraft,isPrerelease,assets,body > "$RUNNER_TEMP/thor-published.json"
python3 - "$RUNNER_TEMP/thor-published.json" "$VERSION_CODE" <<'PYCHECK'
import json, sys
r = json.load(open(sys.argv[1]))
assert not r['isDraft'] and not r['isPrerelease']
assert [a['name'] for a in r['assets']] == ['foldcade-thor.apk']
assert f'versionCode: {sys.argv[2]}' in r['body'].splitlines()
PYCHECK
latest="$(gh api "repos/$GH_REPO/releases/latest" --jq .tag_name)"
test "$latest" = "$TAG"
