#!/bin/bash

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

conf_file_loop() {
    xmlstarlet c14n --without-comments "${2}" > /tmp/temp.xml
    CONF_FILE_WO_COMMENTED=/tmp/temp.xml
    RESULT=$(xmlstarlet sel -t -v "/configuration/property[name[text()='${1}']]/value" $CONF_FILE_WO_COMMENTED)
    rm -f $CONF_FILE_WO_COMMENTED
}

parse_db_url() {
    local IFS=":"
    read -ra URL <<< "$1"
    pos=$(( ${#URL[*]} - 1 ))
    last=${URL[$pos]}

    for i in "${URL[@]}"; do
        if [[ $i == "$last" ]]; then
            IFS="/"
            read -ra SUBSTR <<< "$i"
            second_pos=$(( ${#SUBSTR[*]} - 1 ))
            second_last=${SUBSTR[$second_pos]}

            for j in "${SUBSTR[@]}"; do
                if [[ $j == "$second_last" ]]; then
                    export SENTRY_DB_NAME
                    SENTRY_DB_NAME="${j}"                    
                fi
            done
        fi
    done
}

SENTRY_HOME=$(cd .. ; pwd)

SENTRY_CONF=${SENTRY_HOME}/conf
SENTRY_CONF_FILE=${SENTRY_CONF}/sentry-site.xml

SENTRY_STORE_JDBC_USER="sentry.store.jdbc.user"
SENTRY_STORE_JDBC_PASSWORD="sentry.store.jdbc.password"
SENTRY_STORE_JDBC_URL="sentry.store.jdbc.url"

DB_TYPE=$1

if [ "$DB_TYPE" != "postgres" ] ; then
    USER=$2
    read -p "Enter password: " -s PASSWORD
fi

conf_file_loop $SENTRY_STORE_JDBC_USER "$SENTRY_CONF_FILE"
if [[ 'xx' == "x${RESULT}x" ]]; then
    echo "ERROR: please set property \"sentry.store.jdbc.user\" into your sentry-site.xml file" >&2
    exit 2
fi
SENTRY_USER=$RESULT

conf_file_loop $SENTRY_STORE_JDBC_PASSWORD "$SENTRY_CONF_FILE"
if [[ 'xx' == "x${RESULT}x" ]]; then
    echo "ERROR: please set property \"sentry.store.jdbc.password\" into your sentry-site.xml file" >&2
    exit 2
fi
SENTRY_PASSWORD=$RESULT

if [ -z "$USER" ] || [ -z "$PASSWORD" ] ; then
    if [ "$DB_TYPE" != "postgres" ] ; then
        echo "ERROR: You should specify administrator username in case you work with MySQL or Oracle."
        echo "Usage: ./configure_sentry.sh (oracle|mysql|postgres) <username>"
        exit 1
    fi
fi

conf_file_loop $SENTRY_STORE_JDBC_URL "$SENTRY_CONF_FILE"
if [[ 'xx' == "x${RESULT}x" ]]; then
    echo "ERROR: please set property \"sentry.store.jdbc.url\" into your sentry-site.xml file" >&2
    exit 2
fi

if [ "$DB_TYPE" == "postgres" ] || [ "$DB_TYPE" == "mysql" ]; then
    parse_db_url "$RESULT"
fi

case $DB_TYPE in
    mysql)
        COMMAND="SET @dbName = '${SENTRY_DB_NAME}'; SET @userName = '${SENTRY_USER}'; SET @userPassword = '${SENTRY_PASSWORD}'; \. create_sentry_store_schema_mysql.sql"
        mysql -u"$USER" -p"$PASSWORD" -e "$COMMAND"
        ;;

    postgres)
        psql -v dbName="${SENTRY_DB_NAME}" -v userName="${SENTRY_USER}" -v userPassword="'${SENTRY_PASSWORD}'" -f create_sentry_store_schema_postgres.sql
        ;;

    oracle)
        sqlplus /nolog << EOF
        connect $USER/$PASSWORD
        @create_sentry_store_schema_oracle.sql $SENTRY_USER $SENTRY_PASSWORD
        quit
EOF
        ;;

    *)
        echo "ERROR: Please specify correct database type. Currently supports Oracle, MySQL and Postgres"
        echo "ERROR: You should specify administrator username in case you work with MySQL or Oracle."
        echo "Usage: ./configure_sentry.sh (oracle|mysql|postgres) <username>"
        exit 2
esac

echo "Init sentry schema"
bash sentry --command schema-tool --conffile "${SENTRY_CONF_FILE}" --dbType "$DB_TYPE" --initSchema