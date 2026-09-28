# caseware

CaseWare Interview Test.

## Git Flow

The repository uses Git Flow with two long-lived branches:

| Branch | Purpose | Merge target |
| --- | --- | --- |
| `main` | Stable, released versions | — |
| `develop` | Integration branch for ongoing development | `main` through a release |
| `feature/<name>` | New work, created from `develop` | `develop` |
| `bugfix/<name>` | Fixes for unreleased work, created from `develop` | `develop` |
| `release/<version>` | Release preparation, created from `develop` | `main` and `develop` |
| `hotfix/<name>` | Urgent fixes to a released version, created from `main` | `main` and `develop` |

Temporary branches are created when needed and deleted after their changes have been merged. Use pull requests for integration and preserve merge commits when finishing releases or hotfixes. Published versions use tags such as `v1.0.0`.

### Start a feature

```sh
git switch develop
git pull --ff-only origin develop
git switch -c feature/template-publish-worker
# Implement and commit the changes.
git push -u origin feature/template-publish-worker
```

Open a pull request targeting `develop`.

### Prepare a release

1. Update `develop` and create `release/1.0.0` from it.
2. Commit only release preparation and stabilization changes on that branch.
3. Push the branch and open a pull request targeting `main`.
4. After merging, tag the release merge commit on `main` as `v1.0.0` and push the tag.
5. Merge the release branch into `develop` through a pull request before deleting it.

### Apply a hotfix

1. Update `main` and create `hotfix/<name>` from it.
2. Implement, validate, commit, and push the fix.
3. Open a pull request targeting `main`.
4. After merging, tag the release merge commit with the next patch version.
5. Merge the hotfix branch into `develop` through a pull request before deleting it. If a release branch is active, incorporate the fix there too.

### Optional Git Flow extension

Standard Git commands are sufficient. If you use the Git Flow extension, configure each new clone with:

```sh
git config gitflow.branch.master main
git config gitflow.branch.develop develop
git config gitflow.prefix.feature feature/
git config gitflow.prefix.bugfix bugfix/
git config gitflow.prefix.release release/
git config gitflow.prefix.hotfix hotfix/
git config gitflow.prefix.support support/
git config gitflow.prefix.versiontag v
```

These settings are local to each clone. GitHub branch protection is configured separately; the branch structure itself does not enforce pull requests or checks.
