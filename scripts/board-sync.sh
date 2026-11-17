#!/usr/bin/env bash
# Moves an issue on the project board from a pull request event.
# In review: non-draft PR opened, reopened or ready for review.
# In dev (QA): PR closed with merge. Anything else is a no-op.
# Never moves a card backwards, never closes or comments on the issue.
# Env: ISSUE ACTION DRAFT MERGED GH_TOKEN GITHUB_REPOSITORY PROJECT_ID
#      STATUS_FIELD_ID OPT_BACKLOG OPT_READY OPT_IN_PROGRESS OPT_IN_REVIEW
#      OPT_IN_DEV_QA OPT_DONE
# Prints one line: "noop: <reason>" or "moved: #N <from> -> <to>".
# GraphQL variables ($owner, $p, ...) are not shell expansions.
# shellcheck disable=SC2016
set -euo pipefail

issue="${ISSUE:-}"
if [[ ! "$issue" =~ ^[1-9][0-9]*$ ]]; then
  echo "board-sync: ISSUE must be a positive integer" >&2
  exit 2
fi

: "${ACTION:?}" "${DRAFT:?}" "${MERGED:?}"

case "$ACTION" in
  opened | reopened | ready_for_review)
    if [[ "$DRAFT" == true ]]; then
      echo "noop: draft pull request"
      exit 0
    fi
    target="${OPT_IN_REVIEW:?}"
    ;;
  closed)
    if [[ "$MERGED" != true ]]; then
      echo "noop: closed without merge"
      exit 0
    fi
    target="${OPT_IN_DEV_QA:?}"
    ;;
  *)
    echo "noop: event $ACTION ignored"
    exit 0
    ;;
esac

if [[ -z "${GH_TOKEN:-}" ]]; then
  echo "::notice::PROJECT_TOKEN is not set; skipping board sync"
  exit 0
fi

: "${GITHUB_REPOSITORY:?}" "${PROJECT_ID:?}" "${STATUS_FIELD_ID:?}"
: "${OPT_BACKLOG:?}" "${OPT_READY:?}" "${OPT_IN_PROGRESS:?}" "${OPT_DONE:?}"

# Rank by option ID so renaming an option never changes behavior.
rank() {
  case "$1" in
    "") echo -1 ;;
    "$OPT_BACKLOG") echo 0 ;;
    "$OPT_READY") echo 1 ;;
    "$OPT_IN_PROGRESS") echo 2 ;;
    "$OPT_IN_REVIEW") echo 3 ;;
    "$OPT_IN_DEV_QA") echo 4 ;;
    "$OPT_DONE") echo 5 ;;
    *) echo unknown ;;
  esac
}
label() {
  case "$1" in
    "") echo "(none)" ;;
    "$OPT_BACKLOG") echo "Backlog" ;;
    "$OPT_READY") echo "Ready" ;;
    "$OPT_IN_PROGRESS") echo "In progress" ;;
    "$OPT_IN_REVIEW") echo "In review" ;;
    "$OPT_IN_DEV_QA") echo "In dev (QA)" ;;
    "$OPT_DONE") echo "Done" ;;
  esac
}

lookup='query($owner:String!,$name:String!,$number:Int!){repository(owner:$owner,name:$name){
  issue(number:$number){id projectItems(first:10){nodes{id project{id number}
    fieldValueByName(name:"Status"){... on ProjectV2ItemFieldSingleSelectValue{optionId name}}}}}}}'
add_item='mutation($p:ID!,$c:ID!){addProjectV2ItemById(input:{projectId:$p,contentId:$c}){item{id}}}'
set_status='mutation($p:ID!,$i:ID!,$f:ID!,$o:String!){updateProjectV2ItemFieldValue(input:{
  projectId:$p,itemId:$i,fieldId:$f,value:{singleSelectOptionId:$o}}){projectV2Item{id}}}'

response="$(gh api graphql -f query="$lookup" -f owner="${GITHUB_REPOSITORY%%/*}" \
  -f name="${GITHUB_REPOSITORY#*/}" -F number="$issue")"

content_id="$(jq -r '.data.repository.issue.id // empty' <<<"$response")"
if [[ -z "$content_id" ]]; then
  echo "noop: #$issue is not an issue"
  exit 0
fi

item_id="$(jq -r --arg p "$PROJECT_ID" \
  '[.data.repository.issue.projectItems.nodes[] | select(.project.id == $p)][0].id // empty' <<<"$response")"
current=""
if [[ -n "$item_id" ]]; then
  current="$(jq -r --arg p "$PROJECT_ID" \
    '[.data.repository.issue.projectItems.nodes[] | select(.project.id == $p)][0].fieldValueByName.optionId // empty' <<<"$response")"
fi

current_rank="$(rank "$current")"
if [[ "$current_rank" == unknown ]]; then
  echo "::warning::Issue #$issue has a status option this script does not know; not moving it" >&2
  echo "noop: #$issue has an unknown status option"
  exit 0
fi
if [[ "$current_rank" -ge "$(rank "$target")" ]]; then
  echo "noop: #$issue $(label "$current") is not behind $(label "$target")"
  exit 0
fi

if [[ -z "$item_id" ]]; then
  item_id="$(gh api graphql -f query="$add_item" -f p="$PROJECT_ID" -f c="$content_id" |
    jq -r '.data.addProjectV2ItemById.item.id')"
fi
gh api graphql -f query="$set_status" -f p="$PROJECT_ID" -f i="$item_id" \
  -f f="$STATUS_FIELD_ID" -f o="$target" >/dev/null
echo "moved: #$issue $(label "$current") -> $(label "$target")"
