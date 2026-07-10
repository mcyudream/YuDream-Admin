---
name: yudream-contract-release
description: Release and synchronize YuDream plugin contract packages through Nexus, including yudream-plugin-spi, @yudream/plugin-sdk, and @yudream/components.
---

# YuDream Contract Release

## Mandatory First Steps

1. Confirm whether the user wants one of these scopes:
   - prepare versions only;
   - publish a new contract release;
   - sync downstream plugin dependencies after a release;
   - complete the full release plus downstream sync flow.
2. Read `references/file-map.md` before editing any versions. It is the source of truth for which files and CI jobs belong to the contract release flow.
3. Read `references/release-workflow.md` before tagging or publishing. It contains the exact release order and validation points.
4. Inspect both repositories before changing versions:
   - core repo: current workspace;
   - plugin repo: sibling `yudream-admin-plugins` workspace if present.
5. Do not overwrite unrelated local changes. If either repo is dirty, limit edits to the contract release files only.

## Decide What To Bump

- Bump `yudream-plugin-spi` when Java plugin interfaces, DTOs, lifecycle contracts, framework service ports, or plugin runtime contracts change.
- Bump `@yudream/plugin-sdk` when plugin frontend API clients, TypeScript contracts, host-plugin frontend runtime APIs, or shared SDK exports change.
- Bump `@yudream/components` when shared plugin-facing UI components or component exports change, or when the SDK release depends on a new components surface.
- If the change crosses backend and frontend contracts, prefer releasing all affected contract packages together so the plugin repo can move in one sync.

## Standard Workflow

1. Use `references/file-map.md` to locate the version source files in the core and plugin repos.
2. Update the core package versions first. Do not update downstream consumers yet.
3. Run targeted validation in the core repo for every package being released.
4. Commit and push the core version changes before creating the release tag.
5. Publish through the tag pipeline to `nexus.yudream.online` unless the user explicitly asks for a local/manual publish.
6. Wait for publish and verify jobs to pass before touching `yudream-admin-plugins`.
7. Update downstream dependency versions in `yudream-admin-plugins`, refresh lockfiles, and run plugin repo verification.
8. Commit and push the downstream sync separately so release and consumer updates stay easy to audit.

## Guardrails

- Derive the current version from the actual source files, never from memory or stale notes.
- Do not mass-edit each plugin package `package.json` in the plugin repo. Shared frontend contract versions come from `yudream-frontend/pnpm-workspace.yaml`.
- Do not update the plugin repo to a version that has not been verified as published.
- Do not assume a post-release snapshot bump is wanted. Only do it when the user explicitly asks.
- When the user asks only for dependency sync, skip the publish steps but still verify that the target versions already exist in the package registries.
- When the user asks only for package publishing, still mention whether the downstream plugin repo remains unsynced afterward.

## Validation Expectations

- For SPI work, run a targeted Maven package/verify step before tagging.
- For SDK/components work, run the package builds from `yudream-frontend` and refresh lockfiles if version references changed.
- For downstream sync, run the plugin repo verification script after updating versions.
- In the final report, include:
  - released version numbers;
  - the pushed tag name;
  - which registry path was used;
  - whether `yudream-admin-plugins` was updated and validated.

## References

- Read `references/file-map.md` first for exact file paths, CI job names, and version ownership.
- Read `references/release-workflow.md` for the ordered release checklist and common commands.
