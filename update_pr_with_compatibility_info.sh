#!/bin/bash

pr_number=$1

required_label="Breaks Binary Compatibility"

current_labels=$(gh pr view $pr_number --json labels --jq '.labels[].name | select(endswith("Compatibility"))')

printf "PR %s Current labels:\n%s\n\n" "$pr_number" "$current_labels"

current_labels_to_remove=$(grep -Fx --invert-match "$required_label" <(echo "$current_labels") | paste -sd ',' -)

echo "current_labels_to_remove = $current_labels_to_remove"

gh pr edit $pr_number --remove-label "$current_labels_to_remove" --add-label "$required_label"

if grep -Fxq "$required_label" <(echo "$current_labels") ; then
  echo "The '$required_label' label was already present, no need to comment"
else
  echo "The '$required_label' label was not already present, PR has changed compatability level"
  gh pr comment $pr_number --body "Mock info: This PR $required_label when compared against most recent release 3.0.0. Details..."
fi
