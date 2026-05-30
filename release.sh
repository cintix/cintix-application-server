#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RELEASES_FILE="$SCRIPT_DIR/.releases"
JAR_FILE="$SCRIPT_DIR/dist/cintix-application-server-all.jar"
JAR_SLIM="$SCRIPT_DIR/dist/cintix-application-server.jar"

# --- Colours ---
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Colour

banner() {
    echo ""
    echo -e "${CYAN}  Cintix Application Server — Release${NC}"
    echo ""
}

die() {
    echo -e "${RED}ERROR: $*${NC}" >&2
    exit 1
}

info()  { echo -e "${GREEN}→${NC} $*"; }
warn()  { echo -e "${YELLOW}⚠${NC}  $*"; }
step()  { echo -e "${CYAN}●${NC} $*"; }

# --- Prerequisites ---
check_prereqs() {
    command -v gh  &>/dev/null || die "GitHub CLI (gh) not found. Install: https://cli.github.com"
    command -v git &>/dev/null || die "git not found"
    command -v ant &>/dev/null || die "ant not found"

    if ! gh auth status &>/dev/null; then
        die "gh not authenticated. Run: gh auth login"
    fi
}

# --- Read current version ---
current_version() {
    grep -v '^#' "$RELEASES_FILE" | head -1 | awk '{print $1}'
}

# --- Parse version ---
parse_version() {
    local v="$1"
    echo "$v" | sed 's/v//' | awk -F. '{print $1, $2, $3}'
}

bump_version() {
    local current="$1" bump_type="$2"
    read -r major minor patch <<< "$(parse_version "$current")"

    case "$bump_type" in
        major)  major=$((major + 1)); minor=0; patch=0 ;;
        minor)  minor=$((minor + 1)); patch=0 ;;
        bugfix) patch=$((patch + 1)) ;;
        *) die "Unknown bump type: $bump_type" ;;
    esac

    echo "${major}.${minor}.${patch}"
}

# --- Prompt for release type ---
prompt_bump_type() {
    # All prompts go to stderr because this function is called via $(...)
    echo "" >&2
    echo -e "  Nuværende version: ${YELLOW}$(current_version)${NC}" >&2
    echo "" >&2
    echo "  Hvad slags release?" >&2
    echo "    1 = major   (breaking changes —  1.5.0 → 2.0.0)" >&2
    echo "    2 = minor   (nye features   —  1.5.0 → 1.6.0)" >&2
    echo "    3 = bugfix  (fejlrettelser  —  1.5.0 → 1.5.1)" >&2
    echo "" >&2

    local choice
    read -r -p "  Vælg [2]: " choice
    choice="${choice:-2}"

    case "$choice" in
        1) echo "major"   ;;
        2) echo "minor"   ;;
        3) echo "bugfix"  ;;
        *) die "Ugyldigt valg: '$choice' — skriv 1, 2 eller 3" ;;
    esac
}

# --- Prompt for description ---
prompt_description() {
    echo ""
    echo "  Beskriv releasen (en linje):"
    read -r -p "  > " desc

    if [[ -z "$desc" ]]; then
        local tmp
        tmp="$(mktemp)"
        echo "# Release description for v$NEW_VERSION" > "$tmp"
        echo "# Lines starting with # are ignored." >> "$tmp"
        echo "" >> "$tmp"
        ${EDITOR:-vi} "$tmp"
        desc="$(grep -v '^#' "$tmp" | tr '\n' ' ' | sed 's/  */ /g' | sed 's/^ *//;s/ *$//')"
        rm -f "$tmp"
    fi

    if [[ -z "$desc" ]]; then
        die "Description cannot be empty"
    fi

    RELEASE_DESC="$desc"
}

# --- Build ---
build_jar() {
    step "Building fat jar..."
    if ! ant clean jar-with-dependencies &>/dev/null; then
        die "Build failed"
    fi

    if [[ ! -f "$JAR_FILE" ]]; then
        die "JAR not found after build: $JAR_FILE"
    fi

    local size
    size="$(du -h "$JAR_FILE" | cut -f1)"
    info "Built $JAR_FILE ($size)"
}

# --- Update .releases ---
update_releases_file() {
    local date_str
    date_str="$(date '+%Y-%m-%d')"

    local new_entry="$NEW_VERSION | $date_str | $BUMP_TYPE | $RELEASE_DESC"

    step "Updating $RELEASES_FILE ..."
    {
        echo "$new_entry"
        cat "$RELEASES_FILE"
    } > "$RELEASES_FILE.tmp"
    mv "$RELEASES_FILE.tmp" "$RELEASES_FILE"

    info "Added: v$NEW_VERSION ($BUMP_TYPE) — $RELEASE_DESC"
}

# --- Update version in Response.java ---
update_response_version() {
    # 2.0.0 → 2.0, 1.5.1 → 1.5.1
    local short_v
    short_v="$(echo "$NEW_VERSION" | sed 's/\.0$//')"
    local response_file="$SCRIPT_DIR/src/dk/cintix/application/server/modules/http/server/services/domain/models/Response.java"

    step "Updating version in Response.java → $short_v ..."
    sed -i "s/Cintix-Application-Server(CAS)\/[0-9.]*/Cintix-Application-Server(CAS)\/$short_v/" "$response_file"
    info "Response.java version updated to $short_v"
}

# --- Git commit + tag + push ---
git_commit_tag_push() {
    step "Committing release files..."

    local response_file="$SCRIPT_DIR/src/dk/cintix/application/server/modules/http/server/services/domain/models/Response.java"

    # Stage the files we modified
    git add "$RELEASES_FILE" "$response_file"

    git commit -m "release: v$NEW_VERSION — $RELEASE_DESC"

    step "Creating git tag v$NEW_VERSION ..."
    git tag -a "v$NEW_VERSION" -m "v$NEW_VERSION — $RELEASE_DESC"

    info "Pushing commit + tag..."
    git push origin main 2>/dev/null || git push origin master 2>/dev/null || true
    git push origin "v$NEW_VERSION"

    info "Commit + tag v$NEW_VERSION pushed"
}

# --- GitHub Release ---
github_release() {
    step "Creating GitHub release v$NEW_VERSION ..."

    local changelog
    changelog="$(grep -v '^#' "$RELEASES_FILE" | awk -F' \\| ' '{printf "- **v%s** (%s): %s\\n", $1, $3, $4}' | head -5)"

    gh release create "v$NEW_VERSION" \
        --title "v$NEW_VERSION — $RELEASE_DESC" \
        --notes "$(cat <<EOF
## Cintix Application Server v$NEW_VERSION

**Type:** $BUMP_TYPE release
**Date:** $(date '+%Y-%m-%d')

### Description
$RELEASE_DESC

### Assets
- \`cintix-application-server-all.jar\` — fat jar with all dependencies (gson, html-engine)
- \`cintix-application-server.jar\` — slim jar (requires lib/ on classpath)

### Recent releases
$changelog

EOF
)" \
        "$JAR_FILE" \
        "$JAR_SLIM"

    info "GitHub release created: $(gh release view "v$NEW_VERSION" --json url -q '.url')"
}

# --- Confirm ---
confirm() {
    echo ""
    echo -e "  ┌─────────────────────────────────────────┐"
    echo -e "  │  Version:  ${YELLOW}v$NEW_VERSION${NC}                       │"
    echo -e "  │  Type:     ${CYAN}$BUMP_TYPE${NC}                          │"
    echo -e "  │  Desc:     ${GREEN}$RELEASE_DESC${NC}  │"
    echo -e "  └─────────────────────────────────────────┘"
    echo ""
    read -r -p "  Kør release? [y/N] " confirm
    case "$confirm" in
        [yY]|[yY][eE][sS]|[jJ]|[jJ][aA]) return 0 ;;
        *) die "Release afbrudt" ;;
    esac
}

# --- Main ---
main() {
    banner
    check_prereqs

    # Detect bump type from command line or prompt
    if [[ $# -ge 1 ]]; then
        BUMP_TYPE="$1"
        case "$BUMP_TYPE" in
            major|minor|bugfix) ;;
            *) die "Usage: $0 [major|minor|bugfix] [description]" ;;
        esac
    else
        BUMP_TYPE="$(prompt_bump_type)"
    fi

    NEW_VERSION="$(bump_version "$(current_version)" "$BUMP_TYPE")"

    # Description from command line or prompt
    if [[ $# -ge 2 ]]; then
        shift
        RELEASE_DESC="$*"
    else
        prompt_description
    fi

    confirm

    build_jar
    update_releases_file
    update_response_version
    git_commit_tag_push
    github_release

    echo ""
    echo -e "  ${GREEN}╔═════════════════════════════════════╗${NC}"
    echo -e "  ${GREEN}║   Release v$NEW_VERSION complete!       ║${NC}"
    echo -e "  ${GREEN}╚═════════════════════════════════════╝${NC}"
    echo ""
}

main "$@"
