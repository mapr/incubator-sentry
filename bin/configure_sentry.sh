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

read -p "[INPUT] MySql user: " USER
read -p "[INPUT] MySql password: " -s PASSWORD
echo
echo "[INFO] Create sentry store schema in mysql..."
mysql -u$USER -p$PASSWORD < create_sentry_store_schema.sql
echo "[INFO] Done."
echo "[INFO] Init sentry schema..."
bash sentry --command schema-tool --conffile /opt/mapr/sentry/sentry-1.4.0/conf/default-hive-sentry-site.xml --dbType mysql --initSchema
echo "[INFO] Done."