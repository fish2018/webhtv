# Evidence and Research Standard

Use this reference for every upstream merge candidate and every material new player/dependency requirement. It is the evidence standard for claims about correctness, best practice, compatibility, performance, security, or maintainability; a stage may omit a source category only with a recorded inapplicability reason.

## Contents

1. Research question
2. Evidence hierarchy
3. Search procedure
4. Best-practice evaluation
5. Performance evidence
6. Research stop conditions
7. Evidence record template
8. Foundational sources

## 1. Start with a decision-shaped question

Do not search for “everything about” a commit. State the question that could change the WebHTV decision, for example:

- Does this parser change fix an input WebHTV can receive, and does the current fork already cover it?
- Is the upstream Android Surface lifecycle safe under WebHTV's dual-Surface and rapid-recreate behavior?
- Does a claimed decoder optimization improve startup or frame delivery without reducing steady-state quality?
- Does a dependency upgrade preserve ABI, license, supply-chain provenance, and both ARM artifact sets?

Record the current hypothesis, the counter-hypothesis, and what evidence would distinguish them.

## 2. Grade evidence by authority and reproducibility

Use the strongest available evidence; lower tiers may identify questions but should not overrule stronger direct evidence.

| Grade | Evidence | Typical use |
| --- | --- | --- |
| A | Applicable specification/platform documentation; exact upstream/local source and tests; reproducible WebHTV experiment with retained raw data | Establish behavior, contract, compatibility, or measured result |
| B | Maintainer commit/PR/issue explanation, release note linked to code, mature related-project implementation | Explain intent, known constraints, and real-world design tradeoffs |
| C | Reproducible third-party report, benchmark with method/data, independently corroborated technical post | Expand device/input coverage or expose edge cases |
| D | Single blog, forum post, social post, or anecdote without reproducible evidence | Lead for further verification only |
| E | Unsupported assertion, generated summary, commit title alone, or “newer is better” | Do not use as a decision basis |

For a high-risk native, concurrency, security, ABI, or performance stage, seek at least one Grade A source plus independent code/test or reproducible project evidence. If unavailable, label the stage experimental or blocked on evidence.

## 3. Search in risk-driven layers

### Layer 1: exact code and history

- Inspect the full commit, parent, surrounding history, merge/revert status, and final target tree.
- Compare local code, later local fixes, patch-id where useful, and all direct/indirect consumers.
- Read changed tests and build configuration. Confirm the compiled path actually enables the code.
- For force-pushed or rebased histories, record old/new hashes and compare patch identity and tree behavior instead of assuming ancestry.

### Layer 2: primary project evidence

- Search PRs, issues, mailing lists, release notes, developer guides, and maintainer discussion using exact symbols, error strings, commit hashes, format names, and device/vendor names.
- Check open follow-up bugs and later fix/revert commits. A merged commit can still be unsuitable or incomplete.
- Inspect related repositories when behavior spans FFmpeg, Media3, mpv, libplacebo, JNI, build tooling, or the App layer.

### Layer 3: platform, standards, and supply chain

- Check Android, codec/container, graphics, audio, network, and language/toolchain documentation relevant to the changed contract.
- Check dependency provenance, pinned inputs, build reproducibility, licenses, vulnerability/fuzzing history, and artifact identity.
- Search papers for algorithmic, concurrency, scheduling, rendering, security, or performance claims where academic evidence can materially change the design. Confirm the paper's workload and assumptions match Android playback.

### Layer 4: field evidence

- Search high-quality posts, bug reports, device forums, and downstream project code for device-specific failures and operational constraints.
- Treat snippets and anecdotes as hypotheses. Reproduce them or corroborate them with stronger sources before changing production behavior.

### Useful query families

- Exact full commit hash and shortened hash
- Changed function/class/symbol plus `bug`, `regression`, `crash`, `performance`, `Android`, device/GPU/codec name
- Error/log string plus upstream repository
- Feature/format plus `issue`, `pull request`, `revert`, `fix`, `benchmark`, `fuzz`, `CVE`
- Platform API plus lifecycle/thread/ownership terms
- Competing implementation plus the same input or failure mode

## 4. Decide whether upstream is best for WebHTV

“Best practice” is contextual. Score or explain each alternative against:

- Correctness for WebHTV's actual inputs and devices
- Preservation of existing public and implicit contracts
- Failure isolation and diagnosability
- Lifecycle, concurrency, memory, and resource ownership
- Performance, power, thermal behavior, and quality
- Security, malformed-input handling, and fuzzability
- ABI/API compatibility and binary/linker namespace safety
- Build reproducibility, source provenance, license, and artifact traceability
- Maintainability and future upstream alignment
- Rollout observability and rollback cost

Always include the no-change alternative. Prefer a narrow adapted solution when it retains upstream correctness while preserving proven WebHTV safeguards. Do not call a custom solution superior without direct code reasoning and evidence.

## 5. Performance evidence requirements

Before claiming improvement or no regression, define:

- Hypothesis and metric: startup, seek, rebuffer, dropped frames, render lateness, CPU, memory, allocations, I/O, network throughput, battery, thermal state, or another observable
- Baseline/candidate commits and artifact hashes
- Same device, OS, power/thermal/network conditions, player path, settings, and sample/input
- Warm-up policy and repeated comparable runs; use at least three when device noise matters and add runs if variance overlaps the acceptance threshold
- Median/percentile and spread, not only the best run
- Product-relevant acceptance threshold chosen before seeing the result
- Raw logs/traces and known confounders

If visual/audio quality changes, performance numbers alone are insufficient. Record quality, sync, recovery behavior, and user-visible fallback.

## 6. Stop research deliberately

Continue research while an unresolved fact could change adoption, design, stage grouping, validation, or rollback. Stop when:

- Primary documentation/code and local reproducible evidence agree;
- Additional results only repeat the same claim without increasing authority or applicability; or
- The remaining uncertainty requires a specific sample, device, maintainer answer, or user decision.

When stopping on uncertainty, record it as a gate. Do not fill the gap with speculation. This prevents both shallow conclusions and unbounded browsing.

## 7. Evidence record template

```markdown
### Evidence: <claim/question>

- Source type/grade:
- URL or repository path:
- Accessed/revision/commit:
- Supported claim:
- Relevant excerpt or code location:
- Applicability to WebHTV:
- Caveats/conflicts:
- Resulting decision impact:
```

For each stage, keep an evidence table with `claim`, `source`, `grade`, `WebHTV applicability`, and `decision impact`. Clearly label inference rather than presenting it as sourced fact.

## 8. Foundational sources reviewed for this workflow

These sources establish the baseline methodology. Re-check current revisions when a material decision depends on them.

| Source | URL | Applied principle |
| --- | --- | --- |
| Git tag documentation | https://git-scm.com/docs/git-tag | Annotated tags represent meaningful release/milestone objects; do not silently move published tags |
| Git bisect documentation | https://git-scm.com/docs/git-bisect | Preserve known-good/known-bad anchors and testable intermediate commits |
| Git worktree documentation | https://git-scm.com/docs/git-worktree | Isolate integration work without disturbing the main worktree |
| GitHub release management | https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository | Connect immutable release/tag records to notes and artifacts |
| FFmpeg developer guide | https://ffmpeg.org/developer.html | Explain what/why, cite discussions, split logical changes, avoid breaking code, and test adequately |
| FFmpeg FATE | https://ffmpeg.org/fate.html | Use deterministic regression coverage for codec/container behavior |
| mpv contribution guide | https://github.com/mpv-player/mpv/blob/master/DOCS/contribute.md | Match project conventions and justify/test behavioral changes |
| AndroidX Media README | https://github.com/androidx/media/blob/release/README.md | Keep module versions consistent and opt into unstable APIs deliberately |
| Android testing guide | https://developer.android.com/studio/test | Choose local/instrumented/device tests according to behavior and environment |
| Linux kernel patch guidance | https://kernel.org/doc/html/latest/process/submitting-patches.html | One logical change per patch, buildable intermediate states, quantified optimization claims, provenance and testing records |
| SLSA v1.0 | https://slsa.dev/spec/v1.0/ | Retain verifiable build inputs and provenance for binary artifacts |
| OpenSSF Scorecard checks | https://raw.githubusercontent.com/ossf/scorecard/main/docs/checks.md | Pin dependencies, protect review/history, verify CI and binary/source integrity |
| OSS-Fuzz documentation | https://google.github.io/oss-fuzz/ | Treat fuzzing and malformed-input coverage as important parser/decoder security evidence |

Research baseline date: 2026-08-21.
