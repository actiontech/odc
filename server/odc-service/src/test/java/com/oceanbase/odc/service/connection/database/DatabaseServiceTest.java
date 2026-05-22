/*
 * Copyright (c) 2023 OceanBase.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.oceanbase.odc.service.connection.database;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.core.shared.constant.ResourceRoleName;
import com.oceanbase.odc.service.collaboration.project.model.Project;

/**
 * Pure-function tests for {@link DatabaseService#fillCurrentUserResourceRoles}.
 *
 * <p>
 * Task-004-FIX L2: ensures the list API enriches each project entry with
 * {@code currentUserResourceRoles}, matching the detail-API behavior driven by
 * {@code ProjectService.entityToModel}. Kept as a vanilla JUnit test (no Spring context, no
 * Mockito) so it runs in seconds without the DatabaseService bean graph.
 */
public class DatabaseServiceTest {

    /**
     * GaussDB / openGauss list API regression: when the user is OWNER on project 1, every project entry
     * returned by the list API must surface the OWNER role just like the detail API does.
     */
    @Test
    public void fillCurrentUserResourceRoles_assignsOwner_whenUserHasRole() {
        Project p1 = newProject(1L, "team-a");
        Map<Long, Project> projects = new LinkedHashMap<>();
        projects.put(1L, p1);

        Map<Long, Set<ResourceRoleName>> roleMap = new HashMap<>();
        Set<ResourceRoleName> ownerOnly = new HashSet<>();
        ownerOnly.add(ResourceRoleName.OWNER);
        roleMap.put(1L, ownerOnly);

        DatabaseService.fillCurrentUserResourceRoles(projects, roleMap);

        Assert.assertNotNull("currentUserResourceRoles must be populated (Task-004-FIX L2)",
                p1.getCurrentUserResourceRoles());
        Assert.assertEquals(ownerOnly, p1.getCurrentUserResourceRoles());
    }

    /**
     * If the current user has no role on a project, the field must still be a non-null empty set so the
     * front-end sees the same shape as the detail API.
     */
    @Test
    public void fillCurrentUserResourceRoles_assignsEmptySet_whenUserHasNoRoleOnProject() {
        Project p1 = newProject(7L, "team-no-role");
        Map<Long, Project> projects = new HashMap<>();
        projects.put(7L, p1);

        DatabaseService.fillCurrentUserResourceRoles(projects, Collections.emptyMap());

        Assert.assertNotNull("currentUserResourceRoles must never be left null on list API",
                p1.getCurrentUserResourceRoles());
        Assert.assertTrue("currentUserResourceRoles must be an empty set when user has no role",
                p1.getCurrentUserResourceRoles().isEmpty());
    }

    /**
     * Mixed: user is DBA on project 2 and unaffiliated with project 5. Both entries must come back
     * populated (DBA on 2, empty set on 5).
     */
    @Test
    public void fillCurrentUserResourceRoles_handlesMixedRolesAcrossProjects() {
        Project p2 = newProject(2L, "team-dba");
        Project p5 = newProject(5L, "team-empty");
        Map<Long, Project> projects = new LinkedHashMap<>();
        projects.put(2L, p2);
        projects.put(5L, p5);

        Map<Long, Set<ResourceRoleName>> roleMap = new HashMap<>();
        Set<ResourceRoleName> dbaOnly = new HashSet<>();
        dbaOnly.add(ResourceRoleName.DBA);
        roleMap.put(2L, dbaOnly);

        DatabaseService.fillCurrentUserResourceRoles(projects, roleMap);

        Assert.assertEquals(dbaOnly, p2.getCurrentUserResourceRoles());
        Assert.assertNotNull(p5.getCurrentUserResourceRoles());
        Assert.assertTrue(p5.getCurrentUserResourceRoles().isEmpty());
    }

    /**
     * Null project map / null role map / null entries are tolerated: the list path may legitimately
     * receive an empty projectId2Project (e.g. when no database has been bound to any project yet).
     */
    @Test
    public void fillCurrentUserResourceRoles_isNullSafe() {
        // No-op paths must not throw.
        DatabaseService.fillCurrentUserResourceRoles(null, null);
        DatabaseService.fillCurrentUserResourceRoles(Collections.emptyMap(), null);

        // Null project value inside the map must be skipped without NPE.
        Map<Long, Project> projects = new HashMap<>();
        projects.put(9L, null);
        DatabaseService.fillCurrentUserResourceRoles(projects, Collections.emptyMap());
        Assert.assertNull(projects.get(9L));

        // Null project.id must be skipped (no NPE, no setter call).
        Project withoutId = new Project();
        Map<Long, Project> projects2 = new HashMap<>();
        projects2.put(10L, withoutId);
        DatabaseService.fillCurrentUserResourceRoles(projects2, Collections.emptyMap());
        Assert.assertNull(withoutId.getCurrentUserResourceRoles());
    }

    /**
     * Idempotent: calling the helper twice with the same map must not duplicate roles or lose data
     * (defensive guard for any future caller that wraps the list path inside a retry loop).
     */
    @Test
    public void fillCurrentUserResourceRoles_isIdempotent() {
        Project p = newProject(3L, "team-developer");
        Map<Long, Project> projects = new HashMap<>();
        projects.put(3L, p);

        Map<Long, Set<ResourceRoleName>> roleMap = new HashMap<>();
        Set<ResourceRoleName> developerOnly = new HashSet<>();
        developerOnly.add(ResourceRoleName.DEVELOPER);
        roleMap.put(3L, developerOnly);

        DatabaseService.fillCurrentUserResourceRoles(projects, roleMap);
        DatabaseService.fillCurrentUserResourceRoles(projects, roleMap);

        Assert.assertEquals(developerOnly, p.getCurrentUserResourceRoles());
    }

    private static Project newProject(Long id, String name) {
        Project p = new Project();
        p.setId(id);
        p.setName(name);
        return p;
    }
}
