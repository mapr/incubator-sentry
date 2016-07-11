#/bin/bash

echo "This script has a good chance of ANNIHILATING any work that was done directly on MapR's GitHub. It will not affect your workspace though, besides confronting you with conflicts from upstream."

git config --global core.autocrlf input
# in combination with the rest of the script, we will basically smash all Windows line endings as they go from upstream to downstream

# Git workspace is put on selfhosting for reducing disk usage on normal disks, and ease of changing which machine executes the script.
# Although this causes a slight performance hit.
GITWORKSPACE="/home/MAPRTECH/share/packages/github-sync"
mkdir -pv $GITWORKSPACE
pushd $GITWORKSPACE

# Each tuple is:
# 1. UPSTREAM repo
# 2. DOWNSTREAM repo
# 3. WORKSPACE repo (only needed for uniqueness, it could actually be anything, and in theory it should be safe for all tuples to use the same value for every tuple, but I don't want to test that theory)
TUPLES="git@github.com:OpenTSDB/asynchbase.git git@github.com:mapr/asynchbase.git asynchbase
git@github.com:OpenTSDB/asynchbase.git git@github.com:mapr/private-asynchbase.git private-asynchbase
git@github.com:Cascading/cascading.git git@github.com:mapr/cascading.git cascading
git@github.com:Cascading/cascading.git git@github.com:mapr/private-cascading.git private-cascading
git@github.com:Cascading/cascading.samples.git git@github.com:mapr/cascading.samples.git cascading.samples
git@github.com:Cascading/cascading.samples.git git@github.com:mapr/private-cascading.samples.git private-cascading.samples
https://git-wip-us.apache.org/repos/asf/drill.git/ git@github.com:mapr/incubator-drill incubator-drill
https://git-wip-us.apache.org/repos/asf/drill.git/ git@github.com:mapr/private-drill private-drill
git@github.com:apache/flume.git git@github.com:mapr/flume.git flume
git@github.com:apache/flume.git git@github.com:mapr/private-flume.git private-flume
git@github.com:grafana/grafana.git git@github.com:mapr/grafana.git grafana
git@github.com:grafana/grafana.git git@github.com:mapr/private-grafana.git private-grafana
git@github.com:collectd/collectd.git git@github.com:mapr/private-collectd.git private-collectd
git@github.com:egineering-llc/collectd-fast-jmx.git git@github.com:mapr/private-collectd-fast-jmx.git private-collectd-fast-jmx
git@github.com:fluent/fluentd.git git@github.com:mapr/private-fluentd.git private-fluentd
git@github.com:elastic/elasticsearch.git git@github.com:mapr/private-elasticsearch.git private-elasticsearch
git@github.com:elastic/kibana.git git@github.com:mapr/private-kibana.git private-kibana
git@github.com:elastic/curator.git git@github.com:mapr/private-curator private-curator
git@github.com:phusion/traveling-ruby.git git@github.com:mapr/private-traveling-ruby.git private-traveling-ruby
https://git-wip-us.apache.org/repos/asf/hadoop.git git@github.com:mapr/private-hadoop-common.git private-hadoop-common
https://git-wip-us.apache.org/repos/asf/hadoop.git git@github.com:mapr/hadoop-common.git hadoop-common
git@github.com:apache/hbase.git git@github.com:mapr/hbase.git hbase
git@github.com:apache/hbase.git git@github.com:mapr/private-hbase.git private-hbase
git@github.com:apache/hbase.git git@github.com:mapr/hbase-git.git hbase-git
git@github.com:apache/hbase.git git@github.com:mapr/private-hbase-git.git private-hbase-git
git@github.com:apache/hcatalog.git git@github.com:mapr/hcatalog.git hcatalog
git@github.com:apache/hcatalog.git git@github.com:mapr/private-hcatalog.git private-hcatalog
git@github.com:apache/hive.git git@github.com:mapr/hive.git hive
git@github.com:apache/hive.git git@github.com:mapr/private-hive.git private-hive
git@github.com:cloudera/hue.git git@github.com:mapr/hue.git hue
git@github.com:cloudera/hue.git git@github.com:mapr/private-hue.git private-hue
git@github.com:cloudera/impala.git git@github.com:mapr/private-impala.git private-impala
git@github.com:apache/kafka.git git@github.com:mapr/private-kafka.git private-kafka
git@github.com:mongodb/libbson.git git@github.com:mapr/libbson.git libbson
git@github.com:mongodb/libbson.git git@github.com:mapr/private-libbson.git private-libbson
git@github.com:mongodb/mongo-java-driver.git git@github.com:mapr/ebson.git ebson
git@github.com:mongodb/mongo-java-driver.git git@github.com:mapr/private-ebson.git private-ebson
git@github.com:apache/mahout.git git@github.com:mapr/mahout.git mahout
git@github.com:apache/mahout.git git@github.com:mapr/private-mahout.git private-mahout
git@github.com:apache/oozie.git git@github.com:mapr/oozie.git oozie
git@github.com:apache/oozie.git git@github.com:mapr/private-oozie.git private-oozie
git@github.com:OpenTSDB/opentsdb.git git@github.com:mapr/opentsdb.git opentsdb
git@github.com:OpenTSDB/opentsdb.git git@github.com:mapr/private-opentsdb.git private-opentsdb
git@github.com:apache/pig.git git@github.com:mapr/pig.git pig
git@github.com:apache/pig.git git@github.com:mapr/private-pig.git private-pig
git://git.apache.org/incubator-sentry git@github.com:mapr/incubator-sentry.git incubator-sentry
git@github.com:apache/sentry.git git@github.com:mapr/sentry.git sentry
git@github.com:apache/sentry.git git@github.com:mapr/private-sentry.git private-sentry
git@github.com:amplab/shark.git git@github.com:mapr/shark.git shark
git@github.com:amplab/shark.git git@github.com:mapr/private-shark.git private-shark
git@github.com:apache/spark.git git@github.com:mapr/spark.git spark
git@github.com:amplab/shark.git git@github.com:mapr/private-shark.git private-shark
git@github.com:apache/sqoop.git git@github.com:mapr/sqoop.git sqoop
git@github.com:apache/sqoop.git git@github.com:mapr/private-sqoop.git private-sqoop
git@github.com:apache/storm.git git@github.com:mapr/private-storm.git private-storm
git@github.com:apache/storm.git git@github.com:mapr/incubator-storm.git incubator-storm
git@github.com:apache/tez.git git@github.com:mapr/incubator-tez.git incubator-tez
git@github.com:apache/tez.git git@github.com:mapr/private-tez.git private-tez
git@github.com:apache/whirr.git git@github.com:mapr/whirr.git whirr
git@github.com:apache/whirr.git git@github.com:mapr/private-whirr.git private-whirr
git@github.com:brianfrankcooper/YCSB.git git@github.com:mapr/YCSB.git YCSB
git@github.com:brianfrankcooper/YCSB.git git@github.com:mapr/private-YCSB.git private-YCSB
git@github.com:apache/zookeeper.git git@github.com:mapr/zookeeper.git zookeeper
git@github.com:apache/zookeeper.git git@github.com:mapr/private-zookeeper.git private-zookeeper"


echo "$TUPLES" | while read UPSTREAM_REPO DOWNSTREAM_REPO WORKSPACE_REPO
do

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

done #done iterating over tuples
popd #popping GITWORKSPACE for completeness

