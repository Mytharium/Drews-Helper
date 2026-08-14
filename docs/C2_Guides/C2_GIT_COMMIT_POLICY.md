# C2 Git Commit Policy

Last updated: 2026-08-14.

This file records Myth's standing commit policy for C2 work in Drew's Helper.

## Normal Mode

1. Commit each completed C2 change-set on mythpc in this repo.
2. Use the repo's configured Git identity. Do not override author or committer.
3. Never push. Myth pushes his repo himself.
4. Report the commit ledger line as `myth-git: committed <shorthash> on mythpc`.

## Fallback Mode

Use fallback mode only when mythpc SSH is unreachable.

1. First re-probe mythpc SSH with a cheap `echo ok` command.
2. If SSH is still unreachable, commit in the container-side mirror under C2's identity.
3. Prefix the local fallback commit subject with `[c2-local]`.
4. On the next commit attempt, re-probe mythpc before making another fallback commit.
5. When SSH is back, replay the fallback patch onto mythpc, commit there under the repo's configured identity with `[c2-local]` removed from the subject, and verify the touched file hashes match on both sides.
6. Report the commit ledger line as `myth-git: committed <shorthash> on local-fallback` for the temporary local commit, then `myth-git: committed <shorthash> on mythpc` after replay.
