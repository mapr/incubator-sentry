/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless createRequired by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.sentry.provider.db.service.thrift;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.apache.sentry.core.model.db.AccessConstants;
import org.apache.sentry.provider.db.service.persistent.SentryStore;
import org.apache.sentry.service.thrift.SentryServiceIntegrationBase;
import org.junit.Test;

import com.google.common.collect.Sets;


public class TestSentryServiceIntegration extends SentryServiceIntegrationBase {

  @Test
  public void testCreateDropShowRole() throws Exception {
    String requestorUserName = ADMIN_USER;
    Set<String> requestorUserGroupNames = Sets.newHashSet(ADMIN_GROUP);
    String roleName = "admin_r";
    setLocalGroupMapping(requestorUserName, requestorUserGroupNames);
    writePolicyFile();

    client.dropRoleIfExists(requestorUserName, roleName);

    client.createRole(requestorUserName, roleName);

    Set<TSentryRole> roles = client.listRoles(requestorUserName);
    assertEquals("Incorrect number of roles", 1, roles.size());

    for (TSentryRole role:roles) {
      assertTrue(role.getRoleName(), role.getRoleName().equalsIgnoreCase(roleName));
    }
    client.dropRole(requestorUserName, roleName);
  }


  @Test
  public void testGranRevokePrivilegeOnTableForRole() throws Exception {
    String requestorUserName = ADMIN_USER;
    Set<String> requestorUserGroupNames = Sets.newHashSet(ADMIN_GROUP);
    setLocalGroupMapping(requestorUserName, requestorUserGroupNames);
    writePolicyFile();
    String roleName1 = "admin_r1";
    String roleName2 = "admin_r2";

    client.dropRoleIfExists(requestorUserName,  roleName1);
    client.createRole(requestorUserName,  roleName1);

    client.grantTablePrivilege(requestorUserName, roleName1, "server", "db1", "table1", "ALL");
    client.grantTablePrivilege(requestorUserName, roleName1, "server", "db1", "table2", "ALL");
    client.grantTablePrivilege(requestorUserName, roleName1, "server", "db2", "table3", "ALL");
    client.grantTablePrivilege(requestorUserName, roleName1, "server", "db2", "table4", "ALL");


    client.dropRoleIfExists(requestorUserName,  roleName2);
    client.createRole(requestorUserName,  roleName2);

    client.grantTablePrivilege(requestorUserName, roleName2, "server", "db1", "table1", "ALL");
    client.grantTablePrivilege(requestorUserName, roleName2, "server", "db1", "table2", "ALL");
    client.grantTablePrivilege(requestorUserName, roleName2, "server", "db2", "table3", "ALL");
    client.grantTablePrivilege(requestorUserName, roleName2, "server", "db2", "table4", "ALL");

    Set<TSentryPrivilege> listPrivilegesByRoleName = client.listAllPrivilegesByRoleName(requestorUserName, roleName1);
    assertEquals("Privilege not assigned to role1 !!", 4, listPrivilegesByRoleName.size());

    listPrivilegesByRoleName = client.listAllPrivilegesByRoleName(requestorUserName, roleName2);
    assertEquals("Privilege not assigned to role2 !!", 4, listPrivilegesByRoleName.size());


    client.revokeTablePrivilege(requestorUserName, roleName1, "server", "db1", "table1", "ALL");
    listPrivilegesByRoleName = client.listAllPrivilegesByRoleName(requestorUserName, roleName1);
    assertTrue("Privilege not correctly revoked !!", listPrivilegesByRoleName.size() == 3);
    listPrivilegesByRoleName = client.listAllPrivilegesByRoleName(requestorUserName, roleName2);
    assertTrue("Privilege not correctly revoked !!", listPrivilegesByRoleName.size() == 4);

    client.revokeTablePrivilege(requestorUserName, roleName2, "server", "db1", "table1", "ALL");
    listPrivilegesByRoleName = client.listAllPrivilegesByRoleName(requestorUserName, roleName2);
    assertTrue("Privilege not correctly revoked !!", listPrivilegesByRoleName.size() == 3);
    listPrivilegesByRoleName = client.listAllPrivilegesByRoleName(requestorUserName, roleName1);
    assertTrue("Privilege not correctly revoked !!", listPrivilegesByRoleName.size() == 3);

    client.revokeTablePrivilege(requestorUserName, roleName1, "server", "db1", "table2", "ALL");
    client.revokeTablePrivilege(requestorUserName, roleName1, "server", "db2", "table3", "ALL");
    client.revokeTablePrivilege(requestorUserName, roleName1, "server", "db2", "table4", "ALL");
    listPrivilegesByRoleName = client.listAllPrivilegesByRoleName(requestorUserName, roleName1);
    assertTrue("Privilege not correctly revoked !!", listPrivilegesByRoleName.size() == 0);

    client.revokeTablePrivilege(requestorUserName, roleName2, "server", "db1", "table2", "ALL");
    client.revokeTablePrivilege(requestorUserName, roleName2, "server", "db2", "table3", "ALL");
    client.revokeTablePrivilege(requestorUserName, roleName2, "server", "db2", "table4", "ALL");
    listPrivilegesByRoleName = client.listAllPrivilegesByRoleName(requestorUserName, roleName2);
    assertTrue("Privilege not correctly revoked !!", listPrivilegesByRoleName.size() == 0);
  }

  @Test
  public void testMultipleRolesSamePrivilege() throws Exception {
    String requestorUserName = ADMIN_USER;
    Set<String> requestorUserGroupNames = Sets.newHashSet(ADMIN_GROUP);
    setLocalGroupMapping(requestorUserName, requestorUserGroupNames);
    writePolicyFile();
    String roleName1 = "admin_r1";
    String roleName2 = "admin_r2";

    client.dropRoleIfExists(requestorUserName, roleName1);
    client.createRole(requestorUserName,  roleName1);

    client.dropRoleIfExists(requestorUserName,  roleName2);
    client.createRole(requestorUserName,  roleName2);

    client.grantTablePrivilege(requestorUserName, roleName1, "server", "db", "table", "ALL");
    Set<TSentryPrivilege> listPrivilegesByRoleName = client.listAllPrivilegesByRoleName(requestorUserName, roleName1);
    assertTrue("Privilege not assigned to role1 !!", listPrivilegesByRoleName.size() == 1);

    client.grantTablePrivilege(requestorUserName, roleName2, "server", "db", "table", "ALL");
    listPrivilegesByRoleName = client.listAllPrivilegesByRoleName(requestorUserName, roleName2);
    assertTrue("Privilege not assigned to role2 !!", listPrivilegesByRoleName.size() == 1);
  }


  @Test
  public void testShowRoleGrant() throws Exception {
    String requestorUserName = ADMIN_USER;
    Set<String> requestorUserGroupNames = Sets.newHashSet(ADMIN_GROUP);
    String roleName = "admin_testdb";
    String groupName = "group1";
    setLocalGroupMapping(requestorUserName, requestorUserGroupNames);
    writePolicyFile();

    client.dropRoleIfExists(requestorUserName, roleName);
    client.createRole(requestorUserName, roleName);

    Set<TSentryRole> roles = client.listRoles(requestorUserName);
    assertEquals("Incorrect number of roles", 1, roles.size());

    client.grantRoleToGroup(requestorUserName, groupName, roleName);
    Set<TSentryRole> groupRoles = client.listRolesByGroupName(requestorUserName, groupName);
    assertTrue(groupRoles.size() == 1);
    for (TSentryRole role:groupRoles) {
      assertTrue(role.getRoleName(), role.getRoleName().equalsIgnoreCase(roleName));
      assertTrue(role.getGroups().size() == 1);
      for (TSentryGroup group :role.getGroups()) {
        assertTrue(group.getGroupName(), group.getGroupName().equalsIgnoreCase(groupName));
      }
    }

    client.dropRole(requestorUserName, roleName);
  }

  @Test
  public void testShowGrant() throws Exception {
    String requestorUserName = ADMIN_USER;
    Set<String> requestorUserGroupNames = Sets.newHashSet(ADMIN_GROUP);
    String roleName = "admin_testdb";
    String server = "server1";
    String db = "testDB";
    setLocalGroupMapping(requestorUserName, requestorUserGroupNames);
    writePolicyFile();

    client.dropRoleIfExists(requestorUserName, roleName);
    client.createRole(requestorUserName, roleName);

    Set<TSentryRole> roles = client.listRoles(requestorUserName);
    assertEquals("Incorrect number of roles", 1, roles.size());

    client.grantDatabasePrivilege(requestorUserName, roleName, server, db, AccessConstants.ALL);
    Set<TSentryPrivilege> privileges = client.listAllPrivilegesByRoleName(requestorUserName, roleName);
    assertTrue(privileges.size() == 1);

    client.revokeDatabasePrivilege(requestorUserName, roleName, server, db, AccessConstants.ALL);
    client.dropRole(requestorUserName, roleName);
  }

  // See SENTRY-166
  @Test
  public void testUriWithEquals() throws Exception {
    String requestorUserName = ADMIN_USER;
    Set<String> requestorUserGroupNames = Sets.newHashSet(ADMIN_GROUP);
    String roleName = "admin_testdb";
    String server = "server1";
    String uri = "file://u/w/h/t/partition=value/";
    setLocalGroupMapping(requestorUserName, requestorUserGroupNames);
    writePolicyFile();

    // Creating associated role
    client.dropRoleIfExists(requestorUserName, roleName);
    client.createRole(requestorUserName, roleName);
    Set<TSentryRole> roles = client.listRoles(requestorUserName);
    assertEquals("Incorrect number of roles", 1, roles.size());

    client.grantURIPrivilege(requestorUserName, roleName, server, uri);
    Set<TSentryPrivilege> privileges = client.listAllPrivilegesByRoleName(requestorUserName, roleName);
    assertTrue(privileges.size() == 1);

    // Revoking the same privilege
    client.revokeURIPrivilege(requestorUserName, roleName, server, uri);
    privileges = client.listAllPrivilegesByRoleName(requestorUserName, roleName);
    assertTrue(privileges.size() == 0);

    // Clean up
    client.dropRole(requestorUserName, roleName);
  }

  // See SENTRY-181
  @Test
  public void testSameGrantTwice() throws Exception {
    String requestorUserName = ADMIN_USER;
    Set<String> requestorUserGroupNames = Sets.newHashSet(ADMIN_GROUP);
    setLocalGroupMapping(requestorUserName, requestorUserGroupNames);
    writePolicyFile();
    String roleName = "admin_r1";

    client.createRole(requestorUserName, roleName);
    client.grantTablePrivilege(requestorUserName, roleName, "server", "db1", "table1", "ALL");
    client.grantTablePrivilege(requestorUserName, roleName, "server", "db1", "table1", "ALL");
    assertEquals(1, client.listAllPrivilegesByRoleName(requestorUserName, roleName).size());
  }
}
