# Releasing

Pushing a tag `v<version>` runs `.github/workflows/release.yml`. The workflow uploads that version to the Central Portal, which validates and publishes it without a manual step, waits until Maven Central has published it, and then creates a GitHub release whose notes are that version's section of `CHANGELOG.md`. Pushing the tag therefore publishes the version.

Before tagging, run the [UI tests](https://github.com/ysmsm/nav3-uikit/actions/workflows/ui-tests.yml) workflow on `main` and check that every failure is a known issue. In `CHANGELOG.md`, rename **Unreleased** to the new version with the release date, add an empty **Unreleased** above it, and update the comparison links at the bottom. The workflow stops before publishing anything when `CHANGELOG.md` has no section for the tagged version.

1. Set the version in the README's install snippet to the one being released and merge that change.
2. Tag the merged commit and push the tag:

   ```sh
   git tag v0.1.0-dev02
   git push origin v0.1.0-dev02
   ```

The workflow usually takes 10 to 40 minutes, most of it waiting for Maven Central. Its deployment also appears in the [Central Portal](https://central.sonatype.com/publishing/deployments).

## Versions

A version with a hyphen becomes a pre-release on GitHub. Pre-release suffixes follow the order Gradle uses to compare versions: `-dev01` for experimental builds, then `-alpha01`, `-beta01`, and `-rc01`. Gradle ranks `dev` below every other suffix, so `0.1.0-dev02` < `0.1.0-alpha01` < `0.1.0`.

A published version can never be changed or removed, so a fix needs a new version.

## When a deployment fails

A deployment that fails validation is never published, and the workflow fails without creating a GitHub release. The Portal shows the validation errors on the failed deployment; **Drop** discards it. To retry the same version after fixing the cause, move the tag to the fixed commit:

```sh
git push origin :refs/tags/v<version>
git tag -f v<version> <fixed commit>
git push origin v<version>
```

## Secrets and the signing key

The workflow reads four repository secrets:

| Secret | Value |
|---|---|
| `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD` | A Central Portal user token for the `dev.seungmin` namespace |
| `SIGNING_IN_MEMORY_KEY` | The signing key's secret key, as `gpg --export-secret-keys <key id> \| base64` or as ASCII armor |
| `SIGNING_IN_MEMORY_KEY_PASSWORD` | The signing key's passphrase |

Releases are signed with the key `5CB3 949B 3024 99F5 05CD 76C5 A2B1 D17D 04E9 7B40`. Maven Central checks signatures against the public key on `keyserver.ubuntu.com` and `keys.openpgp.org`. The key has an expiry date, and an upload signed after it fails validation. To extend it, run `gpg --quick-set-expire <fingerprint> 2y` and send the public key to both servers again; the secrets stay the same.
