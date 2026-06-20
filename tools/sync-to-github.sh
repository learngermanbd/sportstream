#!/usr/bin/env bash
# ==============================================================================
# tools/sync-to-github.sh
# ------------------------------------------------------------------------------
# Auto-sync helper for the SportStream workspace.
#
# Flow:
#   1. Load rotated GitHub PAT from ~/.sportstream-github-token (chmod 600,
#      OUTSIDE the repo) and the GitHub username from ~/.sportstream-github-username
#      (optional; defaults to "learngermanbd" if the file is absent).
#   2. Sanity-check the credential file's permissions (warn if not 600).
#   3. Create a fresh /tmp git credential-helper script (chmod 700) that echoes
#      username + PAT when git queries for credentials; the helper is `trap`'d
#      to auto-rm on script exit so the PAT is never left on disk.
#   4. `git add -A`, commit with the supplied message (or auto-generated
#      "Auto-sync TIMESTAMP"), and push to origin/main via the ephemeral helper.
#      The token never appears in ps(1), in argv, or in any tracked file.
#   5. Verify `origin/main` HEAD == local HEAD so we never silently fail.
#
# Usage:
#   tools/sync-to-github.sh "phase 5 — Step 5.5 Notice + sync automation"
#   tools/sync-to-github.sh                       # auto-generated message
#
# Safety properties (verified by code-reviewer-minimax-m3):
#   - PAT loaded from outside repo into an in-memory variable — never echoed.
#   - PAT written to a chmod-700 /tmp helper via a printf '%s\n' template
#     inside the helper; the helper outputs each value via SINGLE-QUOTED
#     printf args so bash never re-evaluates the PAT bytes (defense against
#     $(cmd) or backticks that might appear inside the credential bytes).
#   - Helper is `rm`'d by EXIT trap before script returns.
#   - Script refuses to run if credential file is missing or empty.
#   - Script warns (does not fail) if credential file perms are not 600.
#   - Never writes the PAT to any tracked file. Never echoes the PAT bytes
#     (length only: ${#GITHUB_TOKEN}).
#   - No-ops cleanly when there's nothing to commit (exits 0).
#   - Uses git's `-c credential.helper=<file>` extension so the user's
#     global credential.helper config is untouched.
#
# See tools/SYNC_README.md for how to set up the credential file.
# ==============================================================================

set -euo pipefail

# ---------- Configuration (NO secrets below this line) ----------
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

TOKEN_FILE="${HOME}/.sportstream-github-token"
USERNAME_FILE="${HOME}/.sportstream-github-username"
DEFAULT_USERNAME="learngermanbd"
DEFAULT_REMOTE="origin"
DEFAULT_BRANCH="main"

# Pretty output (falls back to plain text when stdout isn't a TTY).
if [[ -t 1 ]]; then
    RED='\033[0;31m'; GRN='\033[0;32m'; YLW='\033[1;33m'; CYA='\033[0;36m'; RST='\033[0m'
else
    RED=''; GRN=''; YLW=''; CYA=''; RST=''
fi

fail() { printf "${RED}FAIL:${RST} %s\n" "$*" >&2; exit 1; }
info() { printf "${CYA}[sync]${RST} %s\n" "$*"; }
ok()   { printf "${GRN}[sync ✓]${RST} %s\n" "$*"; }
warn() { printf "${YLW}[sync]${RST} %s\n" "$*"; }

# ---------- Step 1 — load credentials (read-only; never echo) ----------
if [[ ! -f "$TOKEN_FILE" ]]; then
    fail "Credential file not found: $TOKEN_FILE
  Place the rotated GitHub PAT there with chmod 600 (see tools/SYNC_README.md).
  Example (replace the placeholder with a real rotated PAT — never paste into chat):
      printf '%s' '<rotated-PAT-from-github.com/settings/tokens>' > \"$TOKEN_FILE\"
      chmod 600 \"$TOKEN_FILE\""
fi

# Permissions check — warn if world/group has read. Don't fail (perms vary
# across platforms; the absence of a prompt for the password is the important
# guarantee from git's credential helper protocol).
PERMS="$(stat -c '%a' "$TOKEN_FILE" 2>/dev/null || stat -f '%Lp' "$TOKEN_FILE" 2>/dev/null || echo "???")"
if [[ "$PERMS" != "600" ]]; then
    warn "Credential file $TOKEN_FILE has mode $PERMS (recommended 600). Re-run with: chmod 600 \"$TOKEN_FILE\""
fi

# Read PAT into a shell variable; never echo it, never pass as argv.
GITHUB_TOKEN="$(cat "$TOKEN_FILE")"
[[ -n "$GITHUB_TOKEN" ]] || fail "Credential file $TOKEN_FILE is empty — paste a real PAT into it."

# Username file is optional; default is the repo owner.
GITHUB_USERNAME="$(cat "$USERNAME_FILE" 2>/dev/null || true)"
[[ -n "$GITHUB_USERNAME" ]] || GITHUB_USERNAME="$DEFAULT_USERNAME"

# ---------- Step 2 — ephemeral git credential helper ----------
# The helper is generated fresh in /tmp on every invocation. The username
# and PAT values are emitted inside the helper via:
#       printf 'password=%s\n' '<value>'
# with SINGLE-QUOTED printf arguments, so when git later executes this
# helper bash does NOT re-interpret the PAT bytes — defense against a
# credential that may contain shell metacharacters (parens / backticks).
# GitHub PATs are alphanumeric so a literal single quote cannot appear;
# that is the one byte-class that would still terminate a single-quoted
# string in bash (there is no way to write a safer single-line printf for
# every possible byte sequence, so a non-alphanumeric PAT would need a
# different protocol — e.g. write the value to a sidecar file and read it).
HELPER="$(mktemp -p /tmp sportzfy-cred-helper.XXXXXX.sh)"
chmod 700 "$HELPER"
# Unquoted heredoc: bash expands $GITHUB_USERNAME + $GITHUB_TOKEN once at
# heredoc-generation time; the substituted bytes are written verbatim into
# the helper file (no second-pass parser at outer-script time). The single
# quotes inside the helper body then protect those bytes from any further
# shell interpretation when git invokes the helper.
cat > "$HELPER" <<HELPER_BODY
#!/bin/bash
# git credential helper — output key=value lines on stdout for `get`.
# Safety: every credential value is printed via a SINGLE-QUOTED printf
# argument so the shell never re-interprets its bytes. GitHub PATs are
# purely alphanumeric, so single-quote escaping is unnecessary here.
printf 'username=%s\n' '$GITHUB_USERNAME'
printf 'password=%s\n' '$GITHUB_TOKEN'
HELPER_BODY
trap 'rm -f "$HELPER"' EXIT

# Report length only — never the bytes themselves.
ok "Loaded credentials for $GITHUB_USERNAME (PAT length=${#GITHUB_TOKEN}, never echoed to log)"

# ---------- Step 3 — sanity-check the remote is wired to the target repo ----------
REMOTE_URL="$(git remote get-url "$DEFAULT_REMOTE" 2>/dev/null || true)"
if [[ -z "$REMOTE_URL" ]]; then
    fail "Remote '$DEFAULT_REMOTE' not configured. Run:
      git remote add $DEFAULT_REMOTE https://github.com/$GITHUB_USERNAME/sportstream.git"
fi
info "Tracking remote: $REMOTE_URL"

# ---------- Step 4 — stage + commit (auto-generated if no message supplied) ----------
COMMIT_MSG="${1:-Auto-sync $(date '+%Y-%m-%d %H:%M:%S')}"
git add -A
if git diff --cached --quiet; then
    ok "No working-tree changes; nothing to commit. Exiting cleanly."
    exit 0
fi

# Author identity for the commit. Uses the GitHub username + the public
# no-reply email GitHub provides to every user. NEVER includes the PAT.
git -c user.name="$GITHUB_USERNAME" \
    -c user.email="$GITHUB_USERNAME@users.noreply.github.com" \
    commit -m "$COMMIT_MSG"
ok "Committed: $COMMIT_MSG"

# ---------- Step 5 — push via the ephemeral credential helper, then verify ----------
info "Pushing to $DEFAULT_REMOTE/$DEFAULT_BRANCH via ephemeral helper..."
git -c credential.helper="$HELPER" push "$DEFAULT_REMOTE" "$DEFAULT_BRANCH"

LOCAL_HEAD="$(git rev-parse HEAD)"
REMOTE_HEAD="$(git -c credential.helper="$HELPER" rev-parse "$DEFAULT_REMOTE/$DEFAULT_BRANCH")"
if [[ "$LOCAL_HEAD" == "$REMOTE_HEAD" ]]; then
    ok "Verified: $DEFAULT_REMOTE/$DEFAULT_BRANCH at $LOCAL_HEAD (matches local HEAD)"
else
    fail "Push succeeded but HEAD mismatch:
       local  : $LOCAL_HEAD
       remote : $REMOTE_HEAD
    Try: git fetch origin --quiet && git status && git log --oneline -3"
fi

ok "Sync complete. EXIT trap will rm -f the credential helper."
