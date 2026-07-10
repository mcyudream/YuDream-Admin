# Release Workflow

Use this checklist when the user asks to release contract packages or to sync downstream dependencies after a release.

## 1. Decide The Target Versions

1. Read the live current versions from:
   - `yudream-plugins/yudream-plugin-spi/pom.xml`
   - `pom.xml` -> `yudream.plugin.spi.version`
   - `yudream-frontend/packages/plugin-sdk/package.json`
   - `yudream-frontend/packages/components/package.json`
2. Confirm which packages must move for the requested change.
3. Decide whether the release is:
   - snapshot-to-snapshot maintenance;
   - first stable cut;
   - stable patch/minor/major bump.
4. Do not invent a post-release snapshot bump unless the user explicitly asks for it.

## 2. Update Versions In The Core Repo

Edit only the contract package versions that are part of the release. When releasing SPI, update both its artifact version and the core root `yudream.plugin.spi.version` consumer property to the same value.

Typical locations:

- SPI: `yudream-plugins/yudream-plugin-spi/pom.xml`
- SDK: `yudream-frontend/packages/plugin-sdk/package.json`
- Components: `yudream-frontend/packages/components/package.json`

Then run targeted checks.

### Suggested Core Validation Commands

```powershell
mvn -s ci/maven-settings-nexus.xml -f yudream-plugins/yudream-plugin-spi/pom.xml -DskipTests package -B -ntp
```

```powershell
cd yudream-frontend
pnpm install --frozen-lockfile
pnpm --filter @yudream/plugin-sdk run build
pnpm --filter @yudream/components run build
```

Optional broader contract checks:

```powershell
sh ci/verify-contract-packages.sh
sh ci/verify-contract-package-tarballs.sh
sh ci/verify-core-contract-publish-pipeline.sh
```

## 3. Commit And Tag The Core Release

1. Commit the version change in the core repo.
2. Push the branch.
3. Create the tag in the form `vX.Y.Z` unless the project has explicitly chosen a different tag convention.
4. Push the tag so GitLab runs the publish pipeline.

Recommended report items:

- core commit SHA;
- pushed tag name;
- Nexus Maven and npm repository paths used by the release.

## 4. Wait For Package Publishing And Verification

Watch the tag pipeline and confirm the relevant jobs:

- SPI publish: `publish:maven-plugin-spi`
- Nexus npm publish: `publish:npm-plugin-sdk`, `publish:npm-components`
- SPI verify: `verify:maven-plugin-spi`
- npm verify: `verify:npm-contracts`

Do not update `yudream-admin-plugins` until the needed packages are verifiably available.

## 5. Sync The Downstream Plugin Repo

In `D:/code/yudream-admin-plugins`:

1. Update `pom.xml` property `yudream.plugin.spi.version`.
2. Update `yudream-frontend/pnpm-workspace.yaml` catalog entries for:
   - `@yudream/plugin-sdk`
   - `@yudream/components`
3. Refresh the plugin repo lockfile:

```powershell
cd yudream-frontend
pnpm install
```

4. Run the downstream verification script:

```powershell
sh ci/verify-plugin-repo-readiness.sh
```

5. Commit and push the downstream sync separately from the core release commit.

## 6. Common Failure Checks

- Missing Nexus publish permission: confirm `NEXUS_USERNAME`, `NEXUS_PASSWORD`, and repository privileges.
- Plugin repo still resolves old versions: ensure `pnpm-workspace.yaml` changed and `pnpm-lock.yaml` was regenerated.
- SPI consumers still resolve old artifacts: confirm the root property in the plugin repo `pom.xml` changed and the target version exists in Nexus Maven.

## 7. Completion Standard

Treat the task as fully complete only when you can report:

- exact released contract versions;
- the tag that published them;
- which registry each package was published to;
- whether the downstream plugin repo was updated, validated, and pushed.
