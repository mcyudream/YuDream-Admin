# Contract File Map

Read this file before editing versions. Always inspect the live files instead of trusting remembered version numbers.

## Core Repo

- SPI artifact version source: `yudream-plugins/yudream-plugin-spi/pom.xml`
- Core SPI consumer version: `pom.xml` -> `yudream.plugin.spi.version` (must match the SPI artifact version)
- SDK version source: `yudream-frontend/packages/plugin-sdk/package.json`
- Components version source: `yudream-frontend/packages/components/package.json`
- Core release pipeline: `.gitlab-ci.yml`
- Nexus package notes: `docs/plugin-system/gitlab-private-packages.md`

## Downstream Plugin Repo

Default sibling repo: `D:/code/yudream-admin-plugins`

- Shared SPI version property: `pom.xml` -> `yudream.plugin.spi.version`
- Shared frontend contract versions: `yudream-frontend/pnpm-workspace.yaml`
- Downstream readiness check: `ci/verify-plugin-repo-readiness.sh`

Important ownership rules:

- Backend plugins inherit the SPI version from the root Maven property. Update that property instead of every plugin module.
- Frontend plugin packages consume `@yudream/plugin-sdk` and `@yudream/components` through the workspace catalog. Update `pnpm-workspace.yaml`, then refresh `pnpm-lock.yaml` with `pnpm install`.

## Core CI Jobs

Tag-triggered publish jobs:

- `publish:maven-plugin-spi`
- `publish:npm-plugin-sdk`
- `publish:npm-components`

Tag-triggered verify jobs:

- `verify:maven-plugin-spi`
- `verify:npm-contracts`

Validation jobs worth running before release:

- `validate:contract-packages`
- `validate:contract-package-tarballs`
- `validate:contract-publish-pipeline`

## Registry Selection

- All contract packages publish only to `nexus.yudream.online`.
- Maven resolves from `maven-public` and deploys to `maven-releases` or `maven-snapshots`.
- The `@yudream` npm scope publishes to and resolves from `npm-public`.
- Publishing and authenticated verification require `NEXUS_USERNAME` and `NEXUS_PASSWORD`.
