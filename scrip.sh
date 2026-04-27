#!/bin/sh

git filter-branch --env-filter '

OLD_EMAIL="e.subbotina@uzum.com"
CORRECT_NAME="Evgeniya Subbotina"
CORRECT_EMAIL="subbotina.eugenia@gmail.com"

if [ "$GIT_COMMITTER_EMAIL" = "$OLD_EMAIL" ]
then
    export GIT_COMMITTER_NAME="$CORRECT_NAME"
    export GIT_COMMITTER_EMAIL="$CORRECT_EMAIL"
fi
if [ "$GIT_AUTHOR_EMAIL" = "$OLD_EMAIL" ]
then
    export GIT_AUTHOR_NAME="$CORRECT_NAME"
    export GIT_AUTHOR_EMAIL="$CORRECT_EMAIL"
fi
' --tag-name-filter cat -- --branches --tags

#git push --force --tags origin 'refs/heads/*' after
