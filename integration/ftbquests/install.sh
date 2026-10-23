#!/usr/bin/env bash
# Installs the CC: Tweaked and CC: Cloud quest chapters into an ATM11 instance.
#
#   integration/ftbquests/install.sh <instance-path> [--replace-text] [--dry-run]
#
#   <instance-path>  folder that contains config/ftbquests/quests (client instance or server)
#   --replace-text   replace our own lang files lang/<locale>/chapters/cc_tweaked.json5 and
#                    cc_cloud.json5 instead of merging into them (use it to update the quest text
#                    of an earlier install; the backup keeps the old files)
#   --full           accepted for compatibility, no effect: since CC: Cloud 0.9.0 there is only
#                    one CC: Cloud chapter (the former "full" variant is the normal chapter)
#   --dry-run        only print what would happen
#
# What it does:
#   1. backs up config/ftbquests/quests to config/ftbquests/quests.backup-<timestamp>
#   2. copies chapters/cc_tweaked.json5 and chapters/cc_cloud.json5 (refuses to replace a
#      different chapter with the same file name)
#   3. merges our lang keys into lang/<locale>/chapters/cc_*.json5 and lang/<locale>/chapter.json5
#      (en_us, de_de); keys that already exist are never overwritten (except with --replace-text,
#      which replaces the two cc_*.json5 lang files; chapter.json5 is always only merged)
#   4. adds the chapter group to chapter_groups.json5 only if it is missing
# Nothing else in the quest book is touched. Restart the game/server (or run /ftbquests reload).
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC="$HERE/quests"
REPLACE=0
DRY=0
INSTANCE=""
for a in "$@"; do
  case "$a" in
    --full) echo "note: --full has no effect any more (one CC: Cloud chapter since 0.9.0)" ;;
    --replace-text) REPLACE=1 ;;
    --dry-run) DRY=1 ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    *) INSTANCE="$a" ;;
  esac
done
[ -n "$INSTANCE" ] || { echo "usage: $0 <instance-path> [--replace-text] [--dry-run]" >&2; exit 2; }

Q="$INSTANCE/config/ftbquests/quests"
[ -f "$Q/data.json5" ] && [ -d "$Q/chapters" ] || { echo "error: no FTB Quests book at $Q" >&2; exit 1; }

GROUP_ID="2B51AC12041E3F89"   # ATM11 "Tech"
GROUP_TITLE_EN="Tech"
LOCALES="en_us de_de"

run() { if [ "$DRY" = 1 ]; then echo "[dry-run] $*"; else "$@"; fi; }

chapter_id() { sed -n 's/^  id: "\([0-9A-F]\{16\}\)",$/\1/p' "$1" | head -1; }

# merge_lang <src> <dst>: append every top-level "key": entry of <src> that <dst> does not have yet.
merge_lang() {
  local src="$1" dst="$2"
  if [ ! -f "$dst" ]; then
    echo "  + $dst (new)"
    run mkdir -p "$(dirname "$dst")"
    run cp "$src" "$dst"
    return
  fi
  local tmp
  tmp="$(mktemp)"
  awk '
    function keyof(line,   m) { if (match(line, /^[ \t]*"[^"]+"[ \t]*:/)) { m = substr(line, RSTART, RLENGTH); sub(/^[ \t]*"/, "", m); sub(/"[ \t]*:$/, "", m); return m } return "" }
    FNR == NR { k = keyof($0); if (k != "") have[k] = 1; dst[++nd] = $0; next }
    {
      if ($0 ~ /^[ \t]*[{}][ \t]*$/) { cur = ""; next }
      k = keyof($0)
      if (k != "") { cur = k; if (!(k in have)) { order[++nk] = k; added++ } }
      if (cur != "" && !(cur in have)) block[cur] = block[cur] $0 "\n"
    }
    END {
      if (added == 0) { exit 3 }
      last = nd; while (last > 0 && dst[last] ~ /^[ \t]*$/) last--
      if (dst[last] !~ /^[ \t]*}[ \t]*$/) { print "unexpected end of " FILENAME > "/dev/stderr"; exit 4 }
      prev = last - 1; while (prev > 0 && dst[prev] ~ /^[ \t]*$/) prev--
      for (i = 1; i < last; i++) {
        line = dst[i]
        if (i == prev && line !~ /,[ \t]*$/ && line !~ /^[ \t]*{[ \t]*$/) line = line ","
        print line
      }
      for (i = 1; i <= nk; i++) printf "%s", block[order[i]]
      print "}"
      print added > "/dev/stderr"
    }
  ' "$dst" "$src" > "$tmp" 2> "$tmp.n" && status=0 || status=$?
  if [ "$status" = 3 ]; then
    echo "  = $dst (all keys present)"
  elif [ "$status" = 0 ]; then
    echo "  ~ $dst (+$(cat "$tmp.n") keys)"
    if [ "$DRY" = 1 ]; then echo "[dry-run] update $dst"; else cat "$tmp" > "$dst"; fi
  else
    cat "$tmp.n" >&2; rm -f "$tmp" "$tmp.n"; echo "error: could not merge $dst" >&2; exit 1
  fi
  rm -f "$tmp" "$tmp.n"
}

CLOUD_CHAPTER="$SRC/chapters/cc_cloud.json5"
MERGED_EXISTING=0

# replace_or_merge_lang <src> <dst>: our own per-chapter lang file
replace_or_merge_lang() {
  if [ -f "$2" ]; then
    if [ "$REPLACE" = 1 ]; then
      echo "  ! $2 (replaced)"
      run cp "$1" "$2"
      return
    fi
    MERGED_EXISTING=1
  fi
  merge_lang "$1" "$2"
}

# --- sanity: never replace somebody else's chapter -------------------------------------------
for f in "$SRC/chapters/cc_tweaked.json5" "$CLOUD_CHAPTER"; do
  dst="$Q/chapters/$(basename "$f")"
  if [ -f "$dst" ] && [ "$(chapter_id "$dst")" != "$(chapter_id "$f")" ]; then
    echo "error: $dst exists and is a different chapter (id $(chapter_id "$dst")); nothing changed" >&2
    exit 1
  fi
done

# --- 1. backup ------------------------------------------------------------------------------
BACKUP="$Q.backup-$(date +%Y%m%d-%H%M%S)"
n=1; base="$BACKUP"; while [ -e "$BACKUP" ]; do BACKUP="$base-$n"; n=$((n + 1)); done
echo "Backup: $BACKUP"
run cp -Rp "$Q" "$BACKUP"

# --- 2. chapters ------------------------------------------------------------------------------
echo "Chapters:"
for f in "$SRC/chapters/cc_tweaked.json5" "$CLOUD_CHAPTER"; do
  echo "  > $Q/chapters/$(basename "$f")"
  run cp "$f" "$Q/chapters/$(basename "$f")"
done

# --- 3. lang ------------------------------------------------------------------------------------
echo "Lang:"
for loc in $LOCALES; do
  replace_or_merge_lang "$SRC/lang/$loc/chapters/cc_tweaked.json5" "$Q/lang/$loc/chapters/cc_tweaked.json5"
  replace_or_merge_lang "$SRC/lang/$loc/chapters/cc_cloud.json5" "$Q/lang/$loc/chapters/cc_cloud.json5"
  merge_lang "$SRC/lang/$loc/chapter.additions.json5" "$Q/lang/$loc/chapter.json5"
done

# --- 4. chapter group (only if missing) --------------------------------------------------------
if grep -q "id: \"$GROUP_ID\"" "$Q/chapter_groups.json5"; then
  echo "Chapter group $GROUP_ID (Tech) present: chapter_groups.json5 unchanged"
else
  echo "Chapter group $GROUP_ID missing: adding it to chapter_groups.json5"
  if [ "$DRY" = 0 ]; then
    tmp="$(mktemp)"
    awk -v id="$GROUP_ID" '
      { lines[++n] = $0 }
      END {
        for (i = n; i > 0; i--) if (lines[i] ~ /^  \],?[ \t]*$/) { close_at = i; break }
        for (i = 1; i <= n; i++) {
          if (i == close_at) { print "    {"; print "      id: \"" id "\","; print "    },"; }
          print lines[i]
        }
      }' "$Q/chapter_groups.json5" > "$tmp" && cat "$tmp" > "$Q/chapter_groups.json5"
    rm -f "$tmp"
    tmpl="$(mktemp)"
    printf '{\n  "chapter_group.%s.title": "%s",\n}\n' "$GROUP_ID" "$GROUP_TITLE_EN" > "$tmpl"
    merge_lang "$tmpl" "$Q/lang/en_us/chapter_group.json5"
    rm -f "$tmpl"
  fi
fi

if [ "$MERGED_EXISTING" = 1 ]; then
  echo "Note: existing quest text was kept (only missing keys were added). To update the text of an"
  echo "      earlier install (e.g. the former \"coming soon\" quests), run again with --replace-text."
fi
echo "Done. Restart the game/server or run /ftbquests reload. Backup: $BACKUP"
