# tools/sync-to-github.sh — Setup & Security Notes

This document explains how to set up and securely use the auto-sync helper
at `tools/sync-to-github.sh`. The script auto-stages, auto-commits, and
auto-pushes the SportStream repository to GitHub.

> ## 🔒 The PAT NEVER lives inside the repo.
>
> The rotated GitHub Personal Access Token is placed by **you** (the human)
> in `~/.sportstream-github-token` with `chmod 600`. The script reads it at
> runtime into an ephemeral credential helper file in `/tmp` that is `rm`'d
> before the script exits. The token never appears in `ps`, in any shell
> history, in any tracked file, or in the project's HTML documentation.

---

## Why this exists

Earlier in development a GitHub PAT was passed in a chat message in
plaintext. Chat transcripts are not a credential store, so the rule
going forward is:

1. **Rotate the PAT** if you haven't already
   (<https://github.com/settings/tokens>).
2. **Place the rotated PAT** in `~/.sportstream-github-token`.
3. **Use the script** for every push. The script enforces:
   - The PAT is loaded from outside the repo, never embedded.
   - The PAT is never echoed to logs (only its length is reported).
   - The PAT is written once to a chmod-700 /tmp helper file.
   - The helper is `trap`'d to auto-`rm` on the script's EXIT.
   - The script never writes the PAT to any tracked file.

---

## One-time setup (run locally on your machine)

```bash
# 1. Create the credential file. Use printf (NOT echo) so shell
#    metacharacters in the PAT — rare, but possible — are preserved verbatim.
printf '%s' '<your-rotated-PAT-here>' > ~/.sportstream-github-token

# 2. Lock the file down: 600 = owner read/write only.
chmod 600 ~/.sportstream-github-token

# 3. Sanity-check. The PAT itself should NOT echo.
ls -l ~/.sportstream-github-token
# Expected:  -rw-------  1 <user>  <group>  40  <date>  ~/.sportstream-github-token
wc -c  ~/.sportstream-github-token
# Expected:  40 <pat-name>  (classic ghp_ is 40 chars; fine-grained github_pat_ is ~90)

# 4. (Optional) Override the username. If unset, defaults to `learngermanbd`.
printf '%s' 'learngermanbd' > ~/.sportstream-github-username
chmod 600 ~/.sportstream-github-username
```

---

## Per-turn usage

From the repo root (`C:\Users\RDP\Desktop\sportstream` on Windows, mounted
as `/c/Users/RDP/Desktop/sportstream` under Git Bash):

```bash
cd /c/Users/RDP/Desktop/sportstream
tools/sync-to-github.sh "phase 5 — Step 5.5 Notice + sync automation"
```

If you forget to pass a commit message, the script generates
`Auto-sync YYYY-MM-DD HH:MM:SS`.

---

## What the script does

1. Loads PAT + username from `~/.sportstream-github-token` (and the
   optional `~/.sportstream-github-username`).
2. Warns if the credential file's permissions aren't 600.
3. Creates an ephemeral `/tmp/sportzfy-cred-helper.XXXXXX.sh` helper
   (chmod 700) that returns `username=<user>` + `password=<pat>` to git
   when invoked. The helper is `rm`'d by an `EXIT` trap before the script
   returns.
4. Refuses to run if remote `origin` isn't wired to a GitHub repo.
5. `git add -A` + `git commit -m "<msg>"`. Uses your GitHub username +
   the public no-reply email GitHub provides (`username@users.noreply.github.com`)
   for the author line — never includes the PAT.
6. `git -c credential.helper=<ephemeral-helper> push origin main`.
7. Verifies remote HEAD matches local HEAD.
8. If there's nothing to commit, exits cleanly with a no-op message.

---

## What the script does NOT do

- ❌ It does **not** write the PAT to any file in the repo (chat.html,
    TODO.html, the build plan, or anywhere else tracked by git).
- ❌ It does **not** echo the PAT in its logs (only its length).
- ❌ It does **not** include the PAT in any shell history. (Use
    `set +o history` before any manual paste if you ever need to.)
- ❌ It does **not** call `git remote set-url https://user:pat@host/repo.git`
    (the URL-with-credentials pattern leaks the PAT into `ps` and into
    git's own error messages).
- ❌ It does **not** commit or push without an explicit user gesture in
    the form of running the script (no auto-push hooks left dangling in
    the repo).

---

## What to do if the PAT leaks again

1. **Revoke it immediately** at <https://github.com/settings/tokens>.
2. **Generate a new PAT**, scoped to the smallest set of repositories +
   permissions you actually need.
   - Classic PAT: starts with `ghp_`. Grant the `repo` scope.
   - Fine-grained: starts with `github_pat_`. Grant `Contents: Read+Write`
     on `learngermanbd/sportstream` only.
3. **Run the one-time setup steps** above with the new token.
4. **Re-run `tools/sync-to-github.sh`** to push any backlog.

---

## What to do if `git push` fails with "authentication failed"

- The PAT may have expired, been revoked, or not have the right `repo`
  scope. Visit <https://github.com/settings/tokens>, regenerate (with
  the same or narrower scope), and re-run the one-time setup.
- If the credential helper is misconfigured globally (`git config
  --get credential.helper`), the script's `-c credential.helper=...`
  override still wins for the duration of the push, so a global
  credential helper shouldn't interfere.

---

## Verification that the script is safe

These are the properties verified by `code-reviewer-minimax-m3`:

| Property                                      | Mechanism                                         |
|-----------------------------------------------|---------------------------------------------------|
| PAT never in repo                              | read from `$HOME/.sportstream-github-token` only  |
| PAT never in argv                              | `printf '%s' "$TOKEN"` to the helper file          |
| PAT never in `ps`                              | helper file is chmod-700 in `/tmp`, never invoked via `sh -c "..."` |
| PAT never in shell history                     | credential file written by the human, not by `read` in a script |
| PAT never left on disk after run               | `trap 'rm -f "$HELPER"' EXIT` runs before script returns |
| PAT never in commit message                    | only the user-supplied message + Auto-sync timestamp |
| PAT never in commit author                     | GitHub no-reply email (`username@users.noreply.github.com`) |
| Credential file permissions checked            | warns on non-`600` perms via `stat` cross-platform flag pair |

---

## Files in this repo that reference this workflow

- `TODO.html` — the runtime Auto-Sync Rule section (added 2026-06-20)
  summarises the same setup in HTML.
- `chat.html` — sister log that records the assistant's actual run of
  this script on each session-end.
- `tools/sync-to-github.sh` — the script itself (chmod +x to invoke
  directly, or `bash tools/sync-to-github.sh`).
- `tools/SYNC_README.md` — this document.
