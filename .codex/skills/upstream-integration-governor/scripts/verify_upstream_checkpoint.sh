#!/usr/bin/env bash

set -euo pipefail

usage() {
  printf '%s\n' "Usage: $0 [--strict] [--require-commit FULL_HASH] [assessment-document]"
  printf '%s\n' "Read-only verification of an upstream assessment checkpoint and repository state."
}

strict=0
assessment="docs/upstream-player-dependency-merge-assessment-2026-08-20.md"
required_commits=""

while (($# > 0)); do
  case "$1" in
    --strict)
      strict=1
      shift
      ;;
    --require-commit)
      if (($# < 2)); then
        usage >&2
        exit 2
      fi
      if [[ -n "$required_commits" ]]; then
        required_commits="${required_commits}"$'\n'
      fi
      required_commits="${required_commits}$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --*)
      printf 'Unknown option: %s\n' "$1" >&2
      usage >&2
      exit 2
      ;;
    *)
      assessment="$1"
      shift
      ;;
  esac
done

repo_root="$(git rev-parse --show-toplevel 2>/dev/null)" || {
  printf '%s\n' "ERROR: not inside a Git worktree" >&2
  exit 1
}
cd "$repo_root"

errors=0
warnings=0

pass() {
  printf 'PASS: %s\n' "$1"
}

warn() {
  warnings=$((warnings + 1))
  printf 'WARN: %s\n' "$1" >&2
}

fail() {
  errors=$((errors + 1))
  printf 'FAIL: %s\n' "$1" >&2
}

printf 'Repository: %s\n' "$repo_root"
printf 'Branch: %s\n' "$(git branch --show-current 2>/dev/null || true)"
printf 'HEAD: %s\n' "$(git rev-parse HEAD)"
printf 'Assessment: %s\n' "$assessment"

for required_file in \
  AGENTS.md \
  .codex/skills/upstream-integration-governor/SKILL.md \
  .codex/skills/upstream-integration-governor/references/integration-workflow.md \
  .codex/skills/upstream-integration-governor/references/evidence-and-research.md \
  .codex/skills/upstream-integration-governor/references/webhtv-player-gates.md; do
  if [[ -s "$required_file" ]]; then
    pass "$required_file exists"
  else
    fail "$required_file is missing or empty"
  fi
done

if [[ ! -s "$assessment" ]]; then
  fail "$assessment is missing or empty"
else
  pass "$assessment exists"

  latest_checkpoint="$(rg '^## (检查点|Checkpoint) ' "$assessment" | tail -n 1 || true)"
  if [[ -n "$latest_checkpoint" ]]; then
    printf 'Latest checkpoint: %s\n' "$latest_checkpoint"
  else
    fail "no checkpoint heading found in $assessment"
  fi

  if rg -q '恢复锚点|Recovery anchor|Rollback anchor' "$assessment"; then
    pass "recovery/rollback anchor is present"
  else
    fail "no recovery/rollback anchor found in $assessment"
  fi

  if rg -q '下一步|Next action' "$assessment"; then
    pass "next action is present"
  else
    fail "no next action found in $assessment"
  fi

  full_commit_count="$( { rg -o '[0-9a-fA-F]{40}' "$assessment" || true; } | tr 'A-F' 'a-f' | sort -u | wc -l | tr -d ' ')"
  printf 'Unique full commit IDs recorded: %s\n' "$full_commit_count"
  if [[ "$full_commit_count" == "0" ]]; then
    fail "assessment contains no full 40-character commit IDs"
  fi

  if [[ -n "$required_commits" ]]; then
    while IFS= read -r commit; do
      if [[ ! "$commit" =~ ^[0-9a-fA-F]{40}$ ]]; then
        fail "required commit is not a full 40-character hash: $commit"
      elif rg -q --fixed-strings "$commit" "$assessment"; then
        pass "required commit is recorded: $commit"
      else
        fail "required commit is missing from $assessment: $commit"
      fi
    done <<< "$required_commits"
  fi
fi

worktree_diff_check_output=""
if worktree_diff_check_output="$(git diff --check)"; then
  pass "git diff --check (worktree)"
else
  fail "git diff --check reports worktree whitespace errors"
  printf '%s\n' "$worktree_diff_check_output" >&2
fi

cached_diff_check_output=""
if cached_diff_check_output="$(git diff --cached --check)"; then
  pass "git diff --check (index)"
else
  fail "git diff --cached --check reports staged whitespace errors"
  printf '%s\n' "$cached_diff_check_output" >&2
fi

text_whitespace_output="$(rg -n '[[:blank:]]+$' \
  AGENTS.md \
  .codex/skills/upstream-integration-governor/SKILL.md \
  .codex/skills/upstream-integration-governor/agents/openai.yaml \
  .codex/skills/upstream-integration-governor/references \
  .codex/skills/upstream-integration-governor/scripts \
  "$assessment" || true)"
if [[ -z "$text_whitespace_output" ]]; then
  pass "governance and assessment text has no trailing whitespace"
else
  fail "governance or assessment text has trailing whitespace"
  printf '%s\n' "$text_whitespace_output" >&2
fi

status_output="$(git status --short)"
printf '%s\n' "Worktree status:"
if [[ -n "$status_output" ]]; then
  printf '%s\n' "$status_output"
else
  printf '%s\n' "(clean)"
fi

protected_status="$(git status --short -- \
  'third_party/*lock*.json' \
  'third_party/patches' \
  'app/src/*/assets/mpv-libs' \
  'app/src/main/jniLibs' \
  '*.aar' \
  '*.so' || true)"

if [[ -n "$protected_status" ]]; then
  warn "protected dependency/binary paths have changes; reconcile ownership before implementation"
  printf '%s\n' "$protected_status" >&2
else
  pass "no changes in protected dependency/binary paths"
fi

printf 'Summary: %d error(s), %d warning(s)\n' "$errors" "$warnings"

if ((errors > 0)); then
  exit 1
fi
if ((strict > 0 && warnings > 0)); then
  exit 1
fi
exit 0
