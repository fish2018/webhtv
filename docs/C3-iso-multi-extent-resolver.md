# C3 ISO multi-extent App resolver

C3 is implemented and rolled back together with E7-2 because the App resolver consumes Media3 `IsoFileEntry`. The authoritative joint decision, implementation history, verification, commit/tag, risks, and next action are maintained in [E7-2-exo-iso-multi-extent-api.md](E7-2-exo-iso-multi-extent-api.md).

Scope specific to C3: update `IsoTrackMetadataResolver` to read MPLS/CLPI metadata across all logical extents while preserving the existing 8 MiB cap, short-read/EOF handling, cancellation behavior, and MPV metadata semantics. It does not modify MPV native code.

Implementation status: complete in `5f7d834bfdd00f215609df7b41c2ea7cadc2cd4f`, anchored by `recovery/E7-2-C3/20260827193629-5f7d834bfdd0`, and App-compile verified on 2026-08-27. Recorded extents are read in logical order, unrecorded extents are zero-filled, and a short extent still raises `IOException`. Mobile and Leanback arm64 Debug Java compilation passed against the published E7-2 AARs. Tests and real split MPLS/CLPI playback were omitted by user direction and remain residual validation risk.
