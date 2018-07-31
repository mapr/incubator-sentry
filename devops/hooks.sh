#!/usr/bin/env bash

source "interfaces.sh"
source "git-to-git.sh"

pipeline:onLoad() {
    log:level:set "DEBUG"
}

dev:setup() {
    git:hooks:install
    log:warn "dev:setup in devops/hooks.sh has not been set up yet. It should completely prepare a new dev environment from scratch."
}

git:hooks:preCommit() {
    log:warn "git:hooks:preCommit in devops/hooks.sh has not been set up yet. It should perform quick local test to catch errors before running a full PR build"
}

git:pullRequest:onCodeChange() {
    pipeline:verifyStandardChecks
    log:warn "git:pullRequest:onCodeChange in devops/hooks.sh has not been set up yet. It should perform the build and test steps for a Pull Request"
}

git:merge:onComplete() { local artifactoryCredentials=${artifactoryCredentials}
    log:warn "git:merge:onComplete in devops/hooks.sh has not been set up yet. It should ensure built, tested artifacts have been published to appropriate artifactory repositories"
}

gitsync:jenkins:run() {
    export VERBOSE=1
    export GIT_SSL_NO_VERIFY=true
    selfhosting:mount
    gitsync:run
}

gitsync:run() {
    gitsync:sync:all:repos
}