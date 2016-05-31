# private-gitsync
Scripting for synchronizing open-source repositories onto the MapR forks

## How to add a sync entry

1. Edit the script `git-to-git.sh`
2. Go to the list of entries that show a lot of Git repositories
3. Understand that each row is space-separated data in the following format:
   * Column 1: Original repository for the open-source project
   * Space
   * Column 2: MapR fork of the repository
   * Space
   * Column 3: short and convenient name for the project. This can be anything as long as it is a unique string.
   * Line break (unless it is the last row)
4. Insert the entry you want. Here is an example for HBase:
   * `git@github.com:apache/hbase.git git@github.com:mapr/hbase.git hbase`
   * `git@github.com:apache/hbase.git git@github.com:mapr/private-hbase.git private-hbase`
5. Save your commit, and submit it as a pull request.
