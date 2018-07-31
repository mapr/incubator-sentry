#!/usr/bin/env bash

source "devops/interfaces.sh"

UPSTREAM_REPO=$1
DOWNSTREAM_REPO=$2
WORKSPACE_REPO=$3

EXIT_FAST=false

if [ -z "${UPSTREAM_REPO}" ]; then
  echo "no upstream repo specified"
  EXIT_FAST=true
else
  echo "UPSTREAM_REPO=${UPSTREAM_REPO}"
fi

if [ -z "${DOWNSTREAM_REPO}" ]; then
  echo "no downstream repo specified"
  EXIT_FAST=true
else
  echo "DOWNSTREAM_REPO=${DOWNSTREAM_REPO}"
fi

if [ -z "${WORKSPACE_REPO}" ]; then
  echo "no workspace name specified"
  EXIT_FAST=true
else
  echo "WORKSPACE_REPO=${WORKSPACE_REPO}"
fi

if [[ "${EXIT_FAST}" == "true" ]]; then
  exit 1
fi

    # Exact sequence of events for each tuple:
    # 1. clean workspace - nobody actually works on these, right?
    # 2. clone the UPSTREAM repo from github to the WORKSPACE repo
    # 3. in the WORKSPACE repo, add a remote to the DOWNSTREAM repo from github
    # 4. get all branches and tags from the UPSTREAM repo
    # 5. force-push the default/master branch from the UPSTREAM repo to the DOWNSTREAM repo,
    #    because git will have already checked out a local branch for it
    # 6. force-push each branch from the UPSTREAM repo to the DOWNSTREAM repo
    # 7. force-push each tag from the UPSTREAM repo to the DOWNSTREAM repo
    echo "full git-to-git in workspace $WORKSPACE_REPO"
    rm -rf $WORKSPACE_REPO
    set -x
    git clone $UPSTREAM_REPO $WORKSPACE_REPO
    cd $WORKSPACE_REPO
    echo "Now in: `pwd`"
    # the remote doesn't have to be named "downstream" but we name it so for convention
    git remote add --fetch downstream $DOWNSTREAM_REPO
    git fetch downstream "+refs/tags/*:refs/tags/*"
    git pull --all
    git fetch --all
    git fetch --tags

    # We are currently in what github.com has configured as the main branch for the UPSTREAM repo
    FIRST_BRANCH=`git branch | grep "\*" | cut -d " " -f 2`
    FORCE_PUSH_CMD="git push downstream origin/${FIRST_BRANCH}:${FIRST_BRANCH} --force"
    ${FORCE_PUSH_CMD}

    for branch in `git branch -a | grep "remotes/origin" | grep -v HEAD`; do
        UPSTREAM_BRANCH=$branch
        LOCAL_BRANCH=${branch##*/}
        echo "upstream branch: $UPSTREAM_BRANCH"
        echo "local branch: $LOCAL_BRANCH"
        # handle the case where the branch does not exist yeta
        git clean -fdx
        git checkout -- *
        git checkout $UPSTREAM_BRANCH
        git checkout -b $LOCAL_BRANCH
        git push downstream $LOCAL_BRANCH --force
        # double-ensure that the history will be correct
        git push downstream ${UPSTREAM_BRANCH}:${LOCAL_BRANCH} --force
    done #done iterating over branches

    git push --all downstream
    git push --tags downstream

    git tag | sort | while read GIT_TAG ; do
        echo "GIT_TAG=${GIT_TAG}"
        git push downstream ${GIT_TAG} --force
    done

