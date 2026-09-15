#!/usr/bin/env bash
#
# Common build and verification commands for this repository.
#
# Every command runs from the repository root regardless of the caller's working directory, so
# callers never need their own `cd`.
#
# Every command also runs inside the project's nix environment via `direnv exec`, so the JDK, node
# and the Playwright browsers are the ones shell.nix pins and PLAYWRIGHT_BROWSERS_PATH is set.
# Without that, a caller whose shell has not entered the directory silently gets whatever is on
# PATH - which is how the end-to-end suite ends up downloading browsers it cannot run.
#
# Usage: ./toolchain.sh <command> [args...]
#        ./toolchain.sh <group> <command> [args...]
#        ./toolchain.sh help

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND="$ROOT/frontend"
COMPOSE="$ROOT/docker/docker-compose.yml"
NODE_HEAP="--max-old-space-size=8192"

# One number for the checkout at the given path: the same on every call from it, and a different one
# for every other path. Both the compose project name and the block of ports come from it, so a
# checkout cannot end up with one checkout's name and another's ports.
checkout_hash() {
    printf '%s' "$1" | cksum | cut -d' ' -f1
}

# The compose project the end-to-end stack of the checkout at the given path runs under.
#
# Derived from the whole absolute path and not from the directory name: worktrees are named after
# what they are for, and two of them are called the same often enough. The directory name is kept in
# front of the digits anyway, so `docker ps` says which checkout a container belongs to without
# anybody having to work it out. A compose project name takes lowercase letters, digits, hyphen and
# underscore, and must start with a letter or a digit.
e2e_project_name() {
    local path="$1" slug
    slug="$(printf '%s' "${path##*/}" | tr '[:upper:]' '[:lower:]' | tr -c 'a-z0-9_-' '-')"
    printf 'lyna-e2e-%s-%s' "${slug:0:24}" "$(checkout_hash "$path")"
}

# Gives this checkout an end-to-end stack of its own, and tells the suite where it is.
#
# The ports used to be written into the compose file and the Playwright config outright. That is how
# this checkout's stack came to sit on 8899 - a port another project on this machine already
# published - and neither would start while the other was up. Deriving them from the checkout path
# removes the question instead of scheduling it: two checkouts, and two projects, never meet.
#
# Each checkout gets a block of four ports, of which two are in use. The spare two are what lets a
# third published port be added later without every checkout's block moving. The range sits above
# what a developer machine normally publishes and below 32768, where the kernel starts handing out
# ephemeral ports.
#
# Everything downstream reads the ports from here: the compose file publishes them, Playwright and
# its fixtures take the addresses. Nothing is derived twice, so nothing can disagree about which port
# the run is on.
e2e_environment() {
    local base
    base=$((24000 + ($(checkout_hash "$ROOT") % 1000) * 4))

    export COMPOSE_PROJECT_NAME
    COMPOSE_PROJECT_NAME="$(e2e_project_name "$ROOT")"
    export LYNA_E2E_API_PORT="$base"
    export LYNA_E2E_WEB_PORT="$((base + 1))"
    export E2E_BASE_URL="http://localhost:$LYNA_E2E_WEB_PORT"
    export NUXT_BACKEND_URL="http://localhost:$LYNA_E2E_API_PORT"
}

# Runs a command inside the project's direnv/nix environment, falling back to running it directly
# when direnv is not installed so the script still works on a plain checkout.
run() {
    if [ -f "$ROOT/.envrc" ] && command -v direnv >/dev/null 2>&1; then
        # A checkout direnv has not been told to trust refuses every command with its own wording,
        # which reads like a broken toolchain rather than a one-off approval. A fresh git worktree
        # is always in that state, so say what it is and what to do about it, once.
        if [ -z "${DIRENV_APPROVED:-}" ]; then
            if ! direnv exec "$ROOT" true >/dev/null 2>&1; then
                echo "toolchain: this checkout's .envrc has not been approved, so nothing runs in the" >&2
                echo "           project environment. Approve it once with:" >&2
                echo "               direnv allow $ROOT" >&2
                echo "           A fresh git worktree always needs this, even though its .envrc is" >&2
                echo "           identical to the one already approved in the main checkout." >&2
                exit 1
            fi
            DIRENV_APPROVED=1
        fi
        direnv exec "$ROOT" "$@"
    else
        "$@"
    fi
}

usage() {
    cat <<'EOF'
Usage: ./toolchain.sh <command> [args...]
       ./toolchain.sh <group> <command> [args...]

The first hyphen of a name also reads as a space, so `docker e2e` and `docker-e2e` are the same
command. `./toolchain.sh docker` lists what is in a group.

Frontend
  fe-build              Full verification: the gating linters, vue-tsc, and the production build
  fe-typecheck          vue-tsc only (silent on success)
  fe-lint               The gating linters
  fe-audit              Every linter including the advisory ones; prints the warning backlog
  fe-standalone         Re-run every linter in a sandbox shaped like the Docker image, which is what
                        catches one that reads above frontend/
  fe-lint1 <name> [args]
                        One linter, e.g. `fe-lint1 style`. Trailing arguments reach the script,
                        e.g. `fe-lint1 component-size --error=30`
  fe-dev                Dev server
  fe-install            npm install - reconciles node_modules and the lock file with package.json
  fe-prepare            Write .nuxt, the generated types the type-check needs

Frontend end-to-end
  fe-e2e [project]      The stories, default every project. Starts the e2e stack and serves the last
                        build in front of it. Every port is derived from this checkout's path, so a
                        run here takes nothing away from another checkout
  fe-e2e1 <file> [args] One story file, e.g. `fe-e2e1 wizard`
  fe-e2e-built [proj]   Rebuild the frontend first, then run the stories
  fe-e2e-fresh [proj]   Throw the e2e database away, rebuild the stack and the frontend, then run
                        them. The one to use after a backend change: a stack that is already up
                        still runs the sources it started with
  fe-e2e-list           List every story without running anything or starting a server
  fe-e2e-report         Open the last report
  fe-e2e-install        Download the Playwright browsers (once per machine)

Backend
  be-verify             Compile, then every test suite
  be-test               Every test suite, no filter
  be-test1 <pattern> [suite]
                        One test class, e.g. be-test1 '*LicenseSharingServiceTest*'. Defaults to
                        testServices. A --tests filter must target a single suite: Gradle fails any
                        suite the pattern matches nothing in.
                        Suites: testServices, testRepositories, testOther
  be-compile            Compile main and test sources
  be-build              The full Gradle build, as CI runs it

Docker
  docker-backend        Build the backend image, as CI's docker job does
  docker-frontend       Build the frontend image. Worth running when a linter learns to read
                        something outside frontend/ - the image copies only that directory
  docker-dev            Start the dev stack detached: database, backend on 8888, frontend on 3000.
                        Fixed ports on purpose, and shared by every checkout on this machine
  docker-dev-down       Stop it again. The data survives; add -v to throw it away
  docker-e2e            Start the stack the stories run against, detached: database, backend, the
                        Nexus stub they download from, and the seed. One stack per checkout, on a
                        compose project and a block of ports derived from this checkout's path, so
                        several checkouts run the stories at once without meeting. The suite starts
                        it itself when it is down, so this is for having it up in advance
  docker-e2e-down       Stop it again. Add -v to throw this checkout's e2e volumes away with it
  docker-e2e-restart    Build and start it again, which is how a backend change reaches the stories:
                        a stack that is already up keeps running the sources it started with
  docker-e2e-logs       Follow what the stack prints. Name one service to watch only it, e.g.
                        `docker-e2e-logs nexus_e2e`
  docker-e2e-prune      Take down the e2e stacks of checkouts that no longer exist, volumes and all.
                        A deleted worktree leaves a database and a gradle cache behind
  docker-e2e-ports      Print this checkout's compose project and ports, for reading a `docker ps`

Combined
  verify                be-verify then fe-build

Parallel checkouts
  The dev stack binds fixed ports and is shared, so the commands that drive it take a machine-wide
  lock and wait for each other. Everything else, the end-to-end commands included, runs in parallel
  across worktrees. LYNA_TOOLCHAIN_NO_LOCK=1 bypasses the lock.
EOF
}

fe() { cd "$FRONTEND"; }

compose() { run docker compose -f "$COMPOSE" "$@"; }

# The command names are hyphenated, and the first hyphen also reads as a group: `docker e2e` is
# accepted for `docker-e2e`, and both reach the same arm below. Naming the group alone lists what
# is in it.
COMMAND_GROUPS=(fe be docker)

is_group() {
    local candidate
    for candidate in "${COMMAND_GROUPS[@]}"; do
        [ "$1" = "$candidate" ] && return 0
    done
    return 1
}

# What is in a group, read back out of the case arms below so the listing cannot drift from what
# actually runs.
list_group() {
    sed -n 's/^    \([a-z][a-z0-9|_-]*\)).*/\1/p' "$ROOT/toolchain.sh" |
        tr '|' '\n' | sed -n "s/^$1-//p"
}

if is_group "${1:-}"; then
    if [ $# -ge 2 ]; then
        set -- "$1-$2" "${@:3}"
    else
        echo "Commands in '$1':" >&2
        list_group "$1" | sed "s|^|  $1 |" >&2
        exit 2
    fi
fi

cmd="${1:-help}"
shift || true

# The path is fixed rather than taken from TMPDIR. What is being guarded is machine-wide. A lock that
# followed TMPDIR would give every caller with its own temporary directory a lock of its own, and
# callers holding different locks do not wait for one another at all, which is a lock that reads as
# working while guarding nothing.
LOCKFILE="/tmp/lyna-toolchain.lock"

# What is left to guard is the development stack, and only that. It keeps its fixed ports on purpose,
# and every checkout on this machine aims `docker-dev` at that same one.
#
# The end-to-end commands are deliberately outside the lock. Each checkout's stack has its own
# compose project, its own network and its own block of ports, so two of them running the stories at
# the same moment never meet. Making the second one queue would cost it the first one's minutes and
# prevent nothing.
needs_lock() {
    case "$1" in
        docker-dev-logs) return 1 ;;
        docker-dev*) return 0 ;;
        *) return 1 ;;
    esac
}

if [ -z "${LYNA_TOOLCHAIN_LOCKED:-}" ] && [ -z "${LYNA_TOOLCHAIN_NO_LOCK:-}" ] &&
    needs_lock "$cmd" && command -v flock >/dev/null 2>&1; then
    export LYNA_TOOLCHAIN_LOCKED=1
    if ! flock -n "$LOCKFILE" true 2>/dev/null; then
        echo "toolchain: another checkout is running '$cmd' or a sibling; waiting for the lock." >&2
    fi
    exec flock "$LOCKFILE" "$0" "$cmd" "$@"
fi

# Before anything reaches compose or Playwright, so that the stack a command starts and the stack the
# stories look for are the same one. Playwright starts the stack itself through its `webServer`, and
# it inherits this.
case "$cmd" in
    fe-e2e* | docker-e2e*) e2e_environment ;;
esac

case "$cmd" in
    fe-build)
        fe; NODE_OPTIONS="$NODE_HEAP" run npm run lint
        fe; NODE_OPTIONS="$NODE_HEAP" run npm run typecheck
        fe; NODE_OPTIONS="$NODE_HEAP" run npm run build
        ;;
    fe-typecheck)  fe; NODE_OPTIONS="$NODE_HEAP" run npm run typecheck ;;
    fe-lint)       fe; run npm run lint ;;
    fe-audit)      fe; run npm run lint:audit ;;
    fe-standalone) fe; run npm run lint:standalone ;;
    fe-lint1)
        [ $# -ge 1 ] || { echo "fe-lint1 needs a linter name, e.g. style" >&2; exit 2; }
        linter="$1"; shift
        fe; run node "scripts/lint-$linter.mjs" "$@"
        ;;
    fe-dev)        fe; run npm run dev "$@" ;;
    fe-install)    fe; run npm install --no-audit --no-fund "$@" ;;
    fe-prepare)    fe; run npx nuxi prepare ;;

    fe-e2e)        fe; run npx playwright test "${@:+--project=$1}" ;;
    fe-e2e1)
        [ $# -ge 1 ] || { echo "fe-e2e1 needs a story file, e.g. wizard" >&2; exit 2; }
        story="$1"; shift
        fe; run npx playwright test "$story" "$@"
        ;;
    fe-e2e-built)
        fe; NODE_OPTIONS="$NODE_HEAP" run npm run build
        fe; run npx playwright test "${@:+--project=$1}"
        ;;
    fe-e2e-fresh)
        compose --profile e2e down -v
        compose --profile e2e up -d --build
        fe; NODE_OPTIONS="$NODE_HEAP" run npm run build
        fe; run npx playwright test "${@:+--project=$1}"
        ;;
    fe-e2e-list)   fe; E2E_NO_SERVER=1 run npx playwright test --list ;;
    fe-e2e-report) fe; run npx playwright show-report e2e/report ;;
    fe-e2e-install) fe; run npx playwright install chromium firefox ;;

    be-verify)
        cd "$ROOT"; run ./gradlew compileJava compileTestJava
        cd "$ROOT"; run ./gradlew testRepositories testServices testOther
        ;;
    be-test)    cd "$ROOT"; run ./gradlew testRepositories testServices testOther "$@" ;;
    be-test1)
        [ $# -ge 1 ] || { echo "be-test1 needs a pattern, e.g. '*LicenseSharingServiceTest*'" >&2; exit 2; }
        pattern="$1"; suite="${2:-testServices}"
        cd "$ROOT"; run ./gradlew "$suite" --tests "$pattern"
        ;;
    be-compile) cd "$ROOT"; run ./gradlew compileJava compileTestJava ;;
    be-build)   cd "$ROOT"; run ./gradlew build "$@" ;;

    docker-backend)  cd "$ROOT"; run docker build -f docker/backend.Dockerfile . ;;
    docker-frontend) cd "$ROOT"; run docker build -f docker/frontend.Dockerfile . ;;
    docker-dev)      compose --profile dev up -d --build "$@" ;;
    docker-dev-down) compose --profile dev down "$@" ;;
    docker-dev-logs) compose --profile dev logs -f "$@" ;;

    docker-e2e)         compose --profile e2e up -d --build "$@" ;;
    docker-e2e-down)    compose --profile e2e down "$@" ;;
    docker-e2e-restart) compose --profile e2e up -d --build "$@" ;;
    docker-e2e-logs)    compose --profile e2e logs -f "$@" ;;
    docker-e2e-ports)
        printf 'project  %s\n' "$COMPOSE_PROJECT_NAME"
        printf 'backend  %s\n' "$NUXT_BACKEND_URL"
        printf 'frontend %s\n' "$E2E_BASE_URL"
        ;;
    docker-e2e-prune)
        # A worktree that has been deleted leaves its stack behind, and nothing else will ever take
        # it down: the project name encodes a path that no longer exists, so no checkout claims it.
        docker compose ls --all --format json |
            sed -n 's/.*"Name":"\(lyna-e2e-[^"]*\)".*/\1/p' |
            sort -u |
            while read -r project; do
                [ "$project" = "$COMPOSE_PROJECT_NAME" ] && continue
                echo "Taking down $project" >&2
                COMPOSE_PROJECT_NAME="$project" docker compose -f "$COMPOSE" --profile e2e down -v
            done
        ;;

    verify)
        "$0" be-verify
        "$0" fe-build
        ;;

    help | -h | --help) usage ;;
    *)
        echo "Unknown command: $cmd" >&2
        echo >&2
        usage >&2
        exit 2
        ;;
esac
