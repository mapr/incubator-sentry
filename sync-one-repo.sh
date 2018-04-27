#/bin/bash

UPSTREAM_REPO=$1
DOWNSTREAM_REPO=$2
WORKSPACE_REPO=$3

if [ -z "${UPSTREAM_REPO}" ]; then
  echo "no upstream repo specified"
else
  echo "UPSTREAM_REPO=${UPSTREAM_REPO}"
fi

if [ -z "${DOWNSTREAM_REPO}" ]; then
  echo "no downstream repo specified"
else
  echo "DOWNSTREAM_REPO=${DOWNSTREAM_REPO}"
fi

if [ -z "${WORKSPACE_REPO}" ]; then
  echo "no workspace name specified"
else
  echo "WORKSPACE_REPO=${WORKSPACE_REPO}"
fi

    # Exact sequence of events for each tuple:
    # 1. clean workspace - nobody actually works on these, right?
    # 2. clone the DOWNSTREAM repo from github to the WORKSPACE repo
    # 3. in the WORKSPACE repo, add a remote to the UPSTREAM repo from github
    # 4. get all branches and tags from the UPSTREAM repo
    # 5. force-push each branch from the UPSTREAM repo to the DOWNSTREAM repo
    # 6. force-push each tag from the UPSTREAM repo to the DOWNSTREAM repo
    echo "full git-to-git in workspace $WORKSPACE_REPO"
    rm -rf $WORKSPACE_REPO
    echo "Waiting a minute before the next clone"
    sleep 60s
    git clone $DOWNSTREAM_REPO $WORKSPACE_REPO
    pushd $WORKSPACE_REPO
    echo "Now in: `pwd`"
    # the remote doesn't have to be named "upstream" but we name it so for convention
    git remote add --fetch upstream $UPSTREAM_REPO
    git fetch upstream "+refs/tags/*:refs/tags/*"
    git pull --all
    git fetch --all
    git fetch --tags

    # We are currently in what github.com has configured as the main branch for the DOWNSTREAM repo
    FIRST_BRANCH=`git branch | grep "\*" | cut -d " " -f 2`
    FORCE_PUSH_CMD="git push origin upstream/${FIRST_BRANCH}:${FIRST_BRANCH} --force"
    ${FORCE_PUSH_CMD}

    for branch in `git branch -a | grep "remotes/upstream" | grep -v HEAD`; do
        UPSTREAM_BRANCH=$branch
        LOCAL_BRANCH=${branch##*/}
        echo "upstream branch: $UPSTREAM_BRANCH"
        echo "local branch: $LOCAL_BRANCH"
        # handle the case where the branch does not exist yet
        git checkout $UPSTREAM_BRANCH
        git clean -f -d
        git checkout -- *
        git checkout -b $LOCAL_BRANCH
        git push origin $LOCAL_BRANCH --force
        # double-ensure that the history will be correct
        git push origin ${UPSTREAM_BRANCH}:${LOCAL_BRANCH} --force
    done #done iterating over branches

    git push --all origin
    git push --tags origin

    git tag | sort | while read GIT_TAG ; do
        echo "GIT_TAG=${GIT_TAG}"
        git push origin ${GIT_TAG} --force
    done

    popd #popping WORKSPACE_REPO

