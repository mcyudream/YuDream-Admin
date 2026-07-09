# Contract File Map

Read this file before editing versions. Always inspect the live files instead of trusting remembered version numbers.

## Core Repo

- SPI version source: `yudream-plugins/yudream-plugin-spi/pom.xml`
- SDK version source: `yudream-frontend/packages/plugin-sdk/package.json`
- Components version source: `yudream-frontend/packages/components/package.json`
- Core release pipeline: `.gitlab-ci.yml`
- GitLab private package notes: `docs/plugin-system/gitlab-private-packages.md`
- npmjs public package notes: `docs/plugin-system/npmjs-public-packages.md`

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
- `publish:npmjs-plugin-sdk`
- `publish:npmjs-components`

Tag-triggered verify jobs:

- `verify:maven-plugin-spi`
- `verify:gitlab-npm-contracts`
- `verify:npmjs-contracts`

Validation jobs worth running before release:

- `validate:contract-packages`
- `validate:contract-package-tarballs`
- `validate:contract-publish-pipeline`

## Registry Selection

- GitLab Maven for SPI is always part of the release flow.
- GitLab npm publish path is enabled by `GITLAB_NPM_PUBLISH_ENABLED=true`.
- npmjs publish path is enabled by `NPMJS_PUBLISH_ENABLED=true` and requires `NPM_TOKEN`.
- If both npm publish flags are enabled on the tag pipeline, both publish paths may run. Confirm that this is intended before tagging.
