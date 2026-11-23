/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.fhir2.api.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.GlobalProperty;
import org.openmrs.api.db.hibernate.multitenancy.TenantContext;

/**
 * Each tenant has its own global_property table, so a value - or a miss - cached for one tenant
 * must never be served to another. A single miss from one tenant used to make
 * fhir2.administeringEncounterRoleUuid null for every tenant until a restart.
 */
public class FhirGlobalPropertyHolderTenantTest {
	
	private static final String ROLE_GP = "fhir2.administeringEncounterRoleUuid";
	
	private final FhirGlobalPropertyHolder listener = new FhirGlobalPropertyHolder();
	
	@Before
	@After
	public void reset() {
		FhirGlobalPropertyHolder.reset();
		TenantContext.clear();
	}
	
	@Test
	public void cacheKey_shouldDifferBetweenTenants() {
		TenantContext.setCurrentTenant("hospitala");
		String a = FhirGlobalPropertyHolder.cacheKey(ROLE_GP);
		TenantContext.setCurrentTenant("hospitalb");
		String b = FhirGlobalPropertyHolder.cacheKey(ROLE_GP);
		
		assertThat(a, not(equalTo(b)));
	}
	
	@Test
	public void aMissInOneTenantShouldNotHideTheValueInAnother() {
		TenantContext.setCurrentTenant("halfbuilt");
		listener.globalPropertyDeleted(ROLE_GP);
		assertThat(FhirGlobalPropertyHolder.getGlobalProperty(ROLE_GP), nullValue());
		
		TenantContext.setCurrentTenant("hospitala");
		listener.globalPropertyChanged(new GlobalProperty(ROLE_GP, "240b26f9-dd88-4172-823d-4a8bfeb7841f"));
		
		assertThat(FhirGlobalPropertyHolder.getGlobalProperty(ROLE_GP), equalTo("240b26f9-dd88-4172-823d-4a8bfeb7841f"));
		TenantContext.setCurrentTenant("halfbuilt");
		assertThat("the miss stays with the tenant that had it", FhirGlobalPropertyHolder.getGlobalProperty(ROLE_GP),
		    nullValue());
	}
	
	@Test
	public void aValueSavedInOneTenantShouldNotBeServedToAnother() {
		TenantContext.setCurrentTenant("hospitala");
		listener.globalPropertyChanged(new GlobalProperty(ROLE_GP, "role-of-a"));
		TenantContext.setCurrentTenant("hospitalb");
		listener.globalPropertyChanged(new GlobalProperty(ROLE_GP, "role-of-b"));
		
		assertThat(FhirGlobalPropertyHolder.getGlobalProperty(ROLE_GP), equalTo("role-of-b"));
		TenantContext.setCurrentTenant("hospitala");
		assertThat(FhirGlobalPropertyHolder.getGlobalProperty(ROLE_GP), equalTo("role-of-a"));
	}
	
	@Test
	public void anEmptySavedValueShouldBeAMissNotAnError() {
		TenantContext.setCurrentTenant("hospitala");
		listener.globalPropertyChanged(new GlobalProperty(ROLE_GP, ""));
		
		assertThat(FhirGlobalPropertyHolder.getGlobalProperty(ROLE_GP), nullValue());
	}
}
