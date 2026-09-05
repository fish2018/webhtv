#!/usr/bin/env bash
set -euo pipefail

dist_dir="${DIST_DIR:-dist}"
metadata_file="${OCI_METADATA_FILE:-build/oci-metadata.json}"
release_tag="${RELEASE_TAG:-}"
repository_input="${OCI_REPOSITORY:-}"
username="${OCI_USERNAME:-}"
token="${OCI_TOKEN:-}"

mkdir -p "$(dirname "$metadata_file")"
printf '{}\n' > "$metadata_file"

if [[ -z "$repository_input" || -z "$username" || -z "$token" || -z "$release_tag" ]]; then
  echo "OCI publishing requested but vars.OCI_REPOSITORY, OCI credentials, or RELEASE_TAG are missing." >&2
  exit 1
fi

if ! command -v oras >/dev/null 2>&1; then
  echo "oras CLI is required" >&2
  exit 1
fi

repository_input="${repository_input#https://}"
repository_input="${repository_input#http://}"
repository_input="${repository_input%/}"
first_segment="${repository_input%%/*}"
if [[ "$repository_input" != */* ]]; then
  login_registry="docker.io"
  api_registry="registry-1.docker.io"
  repository="$repository_input"
elif [[ "$first_segment" == *.* || "$first_segment" == *:* || "$first_segment" == "localhost" ]]; then
  login_registry="$first_segment"
  api_registry="$first_segment"
  repository="${repository_input#*/}"
  if [[ "$login_registry" == "docker.io" || "$login_registry" == "registry-1.docker.io" ]]; then
    login_registry="docker.io"
    api_registry="registry-1.docker.io"
  fi
else
  login_registry="docker.io"
  api_registry="registry-1.docker.io"
  repository="$repository_input"
fi

if [[ ! "$repository" =~ ^[a-z0-9]+([._-][a-z0-9]+)*(/[a-z0-9]+([._-][a-z0-9]+)*)*$ ]]; then
  echo "Invalid OCI repository: $repository" >&2
  exit 1
fi

printf '%s' "$token" | oras login "$login_registry" --username "$username" --password-stdin

records="$(mktemp)"
trap 'rm -f "$records"' EXIT

shopt -s nullglob
apks=("$dist_dir"/*.apk)
if [[ ${#apks[@]} -eq 0 ]]; then
  echo "No APK files found in $dist_dir" >&2
  exit 1
fi

for apk in "${apks[@]}"; do
  filename="$(basename "$apk")"
  base="${filename%.apk}"
  tag="${release_tag#v}-${base}"
  reference="$login_registry/$repository:$tag"
  layer_digest="sha256:$(sha256sum "$apk" | awk '{print $1}')"
  size="$(stat -c '%s' "$apk")"
  manifest_digest="$(oras push \
    --artifact-type application/vnd.webhtv.apk.v1 \
    --annotation "org.opencontainers.image.title=$filename" \
    --annotation "org.opencontainers.image.version=$release_tag" \
    --format go-template='{{.digest}}' \
    "$reference" \
    "$apk:application/vnd.android.package-archive")"
  verified_digest="$(oras manifest fetch --descriptor "$reference" | python3 -c 'import json, sys; print(json.load(sys.stdin)["digest"])')"
  if [[ "$manifest_digest" != "$verified_digest" || ! "$manifest_digest" =~ ^sha256:[0-9a-f]{64}$ ]]; then
    echo "OCI manifest verification failed for $filename" >&2
    exit 1
  fi
  printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$base" "$api_registry" "$repository" "$tag" "$manifest_digest" "$layer_digest:$size" >> "$records"
done

python3 - "$records" "$metadata_file" <<'PY'
import json
import sys

records, output = sys.argv[1:]
data = {}
with open(records, encoding="utf-8") as fh:
    for line in fh:
        base, registry, repository, reference, manifest_digest, layer = line.rstrip("\n").split("\t")
        layer_digest, size = layer.rsplit(":", 1)
        data[base] = {
            "registry": registry,
            "repository": repository,
            "reference": reference,
            "manifestDigest": manifest_digest,
            "layerDigest": layer_digest,
            "size": int(size),
        }
with open(output, "w", encoding="utf-8") as fh:
    json.dump(data, fh, ensure_ascii=False, indent=2)
    fh.write("\n")
PY

echo "Published ${#apks[@]} APK artifacts to $login_registry/$repository"
