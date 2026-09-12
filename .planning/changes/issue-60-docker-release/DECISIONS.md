# Issue 60 Docker Release Decisions

## DR-ISSUE-60-01 — Build identity is explicit

`BUILD_COMMIT` is the release identity passed to Docker Compose. Docker does not
read `.git` from the build context, which keeps repository history and local Git
configuration out of images. CI and operators derive the value with
`git rev-parse HEAD` before starting Compose.

## DR-ISSUE-60-02 — Existing migration history stays immutable

The release fix does not edit any `V*` migration. Development fixtures use one
repeatable migration with stable keys and conditional inserts. Flyway checksum
validation remains enabled for upgrades.

The release repeatable migration has its own location. Acceptance can create
the pre-Issue-60 baseline using the unchanged versioned and development
locations, then start the release location against that same named volume.

## DR-ISSUE-60-03 — Web build identity uses static HTML metadata

Vite replaces a build-time value in `index.html`. The marker is machine-readable
and does not add a visible console component or alter page layout.

The Docker acceptance job checks out the pull-request head SHA, rather than a
synthetic merge commit. The verifier rejects a `BUILD_COMMIT` that differs from
the exact checked-out commit.

## DR-ISSUE-60-04 — Local Docker is not acceptance evidence

The managed implementation container has no Docker daemon, Compose plugin, or
Chrome executable. The pull-request CI job owns the Docker and Chrome acceptance
run on the exact pushed commit. Local Java, Node, and static checks remain
separate evidence.

## DR-ISSUE-60-05 — Verification owns isolated resources

The verifier generates fresh and upgrade Compose project names internally,
refuses pre-existing resources with either label, binds ports to localhost, and
removes only those two owned projects. A caller-supplied `COMPOSE_PROJECT_NAME`
cannot redirect cleanup at an operator project.
