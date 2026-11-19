/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.api.db.hibernate.multitenancy;

/**
 * Test stand-in for the LafiaLink platform's TenantContext, which the stock platform this module
 * compiles against does not have. Only what FhirGlobalPropertyHolder reads.
 */
public class TenantContext {
	
	private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
	
	public static String getCurrentTenant() {
		String tenant = CURRENT.get();
		return tenant == null ? "public" : tenant;
	}
	
	public static void setCurrentTenant(String tenant) {
		CURRENT.set(tenant);
	}
	
	public static void clear() {
		CURRENT.remove();
	}
}
