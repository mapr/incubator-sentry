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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.sentry.provider.db.service.model;

import java.util.HashSet;
import java.util.Set;

import javax.jdo.annotations.PersistenceCapable;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

/**
 * Database backed Sentry Role. Any changes to this object
 * require re-running the maven build so DN an re-enhance.
 */
@PersistenceCapable
public class MSentryRole {

  private String roleName;
  // set of privileges granted to this role
  private Set<MSentryPrivilege> privileges;
  // set of groups this role belongs to
  private Set<MSentryGroup> groups;
  private long createTime;

  public MSentryRole(String roleName, long createTime) {
    this.roleName = roleName;
    this.createTime = createTime;
    privileges = new HashSet<MSentryPrivilege>();
    groups = new HashSet<MSentryGroup>();
  }

  public long getCreateTime() {
    return createTime;
  }

  public void setCreateTime(long createTime) {
    this.createTime = createTime;
  }

  public String getRoleName() {
    return roleName;
  }

  public void setRoleName(String roleName) {
    this.roleName = roleName;
  }

  public void setPrivileges(Set<MSentryPrivilege> privileges) {
    this.privileges = privileges;
  }

  public Set<MSentryPrivilege> getPrivileges() {
    return privileges;
  }

  public void setGroups(Set<MSentryGroup> groups) {
    this.groups = groups;
  }

  public Set<MSentryGroup> getGroups() {
    return groups;
  }

  public void removePrivilege(MSentryPrivilege privilege) {
    if (privileges.remove(privilege)) {
      privilege.removeRole(this);
    }
  }

  public void appendPrivileges(Set<MSentryPrivilege> privileges) {
    this.privileges.addAll(privileges);
  }

  public void appendPrivilege(MSentryPrivilege privilege) {
    if (privileges.add(privilege)) {
      privilege.appendRole(this);
    }
  }

  public void appendGroups(Set<MSentryGroup> groups) {
    this.groups.addAll(groups);
  }

  public void appendGroup(MSentryGroup group) {
    if (groups.add(group)) {
      group.appendRole(this);
    }
  }

  public void removeGroup(MSentryGroup group) {
    if (groups.remove(group)) {
      group.removeRole(this);
    }
  }

  public void removePrivileges() {
    // copy is required since privilege.removeRole will call remotePrivilege
    for (MSentryPrivilege privilege : ImmutableSet.copyOf(privileges)) {
      privilege.removeRole(this);
    }
    Preconditions.checkState(privileges.isEmpty(), "Privileges should be empty: " + privileges);
  }

  @Override
  public String toString() {
    return "MSentryRole [roleName=" + roleName + ", privileges=[..]"
        + ", groups=[...]" + ", createTime=" + createTime + "]";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((roleName == null) ? 0 : roleName.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    MSentryRole other = (MSentryRole) obj;
    if (roleName == null) {
      if (other.roleName != null)
        return false;
    } else if (!roleName.equals(other.roleName))
      return false;
    return true;
  }
}