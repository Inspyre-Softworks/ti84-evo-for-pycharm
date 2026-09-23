# Repository instructions for coding agents

## Version and release notes are part of the change

When changing shipped plugin or CLI behavior, including bug fixes, update the
root `VERSION` and `CHANGELOG.md` in the same task. Do this before building or
handing over an installable artifact; do not wait for the user to remind you.

- `VERSION` is the single source of truth. Do not hard-code a separate version
  in Gradle, plugin metadata, or the CLI.
- Use a patch bump for fixes, a minor bump for backward-compatible features,
  and a major bump for breaking changes, unless the user specifies a version.
- Add an exact `## <version>` changelog heading with concise user-facing release
  notes. An `Unreleased` entry alone does not satisfy this requirement.
- Use one bump per logical release. Follow-up edits to the same unpublished
  change belong under that version; do not bump again for every edit or build.
- Documentation-only, test-only, and repository-instruction-only changes do
  not require a bump unless the user requests one.

## Before handing off

1. Verify that behavior changes include the version bump and matching notes.
2. Run the checks appropriate to the change. For version changes, run the
   packaged-version test (`EvoBuildInfoTest`) and build the deliverables.
3. Build installable artifacts only after updating release metadata. Use
   `buildPlugin cliDistZip` when providing a release build, and link the ZIPs
   for the current version rather than a stale build.
4. Report what was verified and distinguish host tests from calculator tests.

See `docs/development.rst` for the full build and release workflow. Updating
`VERSION` locally does not authorize pushing, publishing, or deploying a release.
