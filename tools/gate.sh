#!/usr/bin/env bash
# Pre-ship gate. Run it, do not remember to do the things it checks.
#
#   bash tools/gate.sh
#
# Exits non-zero on the first failure, so it can sit in a hook or in CI.
set -uo pipefail
cd "$(dirname "$0")/.."

fail=0
note() { printf '%-28s %s\n' "$1" "$2"; }

# Only ever scan tracked text. Earlier versions scanned the working tree and
# tripped on binaries in .gradle and on gradle-wrapper.jar, then reported FAIL
# without blocking anything, which is worse than having no gate.
tracked_text() {
  git ls-files -z | while IFS= read -r -d '' f; do
    case "$f" in
      *.png|*.jpg|*.webp|*.ttf|*.otf|*.jar|*.keystore|*.jks|LICENSE) continue ;;
      tools/gate.sh|CLAUDE.md) continue ;;   # they name what is banned, so they cannot scan themselves
    esac
    [ -f "$f" ] || continue
    grep -Iq . "$f" 2>/dev/null && printf '%s\0' "$f"
  done
}

# --- 1. no en dash or em dash anywhere a person can read -------------------
# This file necessarily contains the characters it bans, so it excludes itself
# from the scan above rather than pretending otherwise. The planted-dash check
# below is what proves the pattern still works.
dash=$'[–—]'
planted=$(mktemp); printf 'planted — dash\n' > "$planted"
if [ "$(grep -c "$dash" "$planted")" != "1" ]; then
  note "dash gate" "BLIND, the check cannot see a planted dash. Aborting."
  rm -f "$planted"; exit 2
fi
rm -f "$planted"

hits=$(tracked_text | xargs -0 grep -n "$dash" 2>/dev/null)
if [ -n "$hits" ]; then
  note "dashes" "FAIL"; echo "$hits"; fail=1
else
  note "dashes" "pass, zero U+2013 and U+2014"
fi

# --- 2. colour literals live in core:design and nowhere else ---------------
colours=$(git ls-files -z '*.kt' | xargs -0 grep -n -E '0x[0-9A-Fa-f]{8}' 2>/dev/null \
  | grep -v '^core/design/' || true)
if [ -n "$colours" ]; then
  note "colour literals" "FAIL, hex outside core:design"; echo "$colours"; fail=1
else
  note "colour literals" "pass, all in core:design"
fi

# --- 3. nothing posts a notification outside core:notify -------------------
notif=$(git ls-files -z '*.kt' | xargs -0 grep -ln 'NotificationManagerCompat\|NotificationManager' 2>/dev/null \
  | grep -v '^core/notify/' || true)
if [ -n "$notif" ]; then
  note "notification funnel" "FAIL, posts outside core:notify"; echo "$notif"; fail=1
else
  note "notification funnel" "pass"
fi

# --- 4. no assistant attribution in the source ----------------------------
who=$(tracked_text | xargs -0 grep -in 'co-authored-by: claude\|anthropic' 2>/dev/null || true)
if [ -n "$who" ]; then
  note "attribution" "FAIL"; echo "$who"; fail=1
else
  note "attribution" "pass"
fi

echo
if [ "$fail" -ne 0 ]; then
  echo "GATE FAILED. Nothing ships until the above is clean."
  exit 1
fi
echo "GATE PASSED."
