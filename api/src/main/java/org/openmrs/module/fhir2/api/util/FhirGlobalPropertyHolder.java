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

import static org.openmrs.module.fhir2.FhirConstants.FHIR2_MODULE_ID;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import org.openmrs.GlobalProperty;
import org.openmrs.api.APIException;
import org.openmrs.api.GlobalPropertyListener;
import org.openmrs.api.context.Context;
import org.openmrs.util.PrivilegeConstants;

/**
 * Caches the module's global properties.
 * <p>
 * <b>LafiaLink: the cache is per tenant.</b> On the schema-per-tenant platform every tenant has its
 * own {@code global_property} table, but this cache used to be one JVM-wide map keyed by property
 * name. The first tenant to read a property decided the answer for all of them - and because a miss
 * is remembered in {@code missingKeys}, a single read from a tenant that lacked a property (one
 * mid-provisioning, or built before the property existed) made it null for every tenant until a
 * restart. That surfaced as
 * {@code "The Immunization resource requires an administering encounter role to be defined in the global
 * property 'fhir2.administeringEncounterRoleUuid'"} on a tenant that had it. Entries are now keyed
 * by the tenant on the current thread, read from core's {@code TenantContext} reflectively because
 * this module compiles against a platform that does not have it; on a platform without it every
 * entry shares one key and the behaviour is exactly the original.
 */
@Slf4j
public class FhirGlobalPropertyHolder implements GlobalPropertyListener {
	
	private static final String TENANT_CONTEXT_CLASS = "org.openmrs.api.db.hibernate.multitenancy.TenantContext";
	
	/** separates tenant from property in a cache key; neither can contain it */
	private static final char KEY_SEPARATOR = '\u0000';
	
	private static final Map<String, String> globalPropertyCache = new ConcurrentHashMap<>(1 << 4 << 4);
	
	// the idea of missing keys is essentially to store "cache misses" so we avoid needing to do unnecessary lookups
	private static final Set<String> missingKeys = ConcurrentHashMap.newKeySet();
	
	private static volatile Method currentTenantMethod;
	
	private static volatile boolean tenantContextLookedUp;
	
	public static String getGlobalProperty(String globalProperty) {
		String key = cacheKey(globalProperty);
		if (missingKeys.contains(key)) {
			return null;
		} else {
			if (!supportsProperty(globalProperty)) {
				return null;
			}
			
			return globalPropertyCache.computeIfAbsent(key, (k) -> {
				String gp = globalProperty;
				String gpValue = null;
				
				boolean hasUserContext = false;
				try {
					Context.addProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
					hasUserContext = true;
				}
				catch (APIException ignored) {}
				
				try {
					gpValue = Context.getAdministrationService().getGlobalProperty(gp);
				}
				catch (APIException ignored) {
					
				}
				finally {
					if (hasUserContext) {
						Context.removeProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
					}
				}
				
				if (gpValue == null || gpValue.isEmpty()) {
					missingKeys.add(k);
					return null;
				}
				
				return gpValue;
			});
		}
	}
	
	public static String getGlobalProperty(String globalProperty, String defaultValue) {
		return Optional.ofNullable(getGlobalProperty(globalProperty)).orElse(defaultValue);
	}
	
	public static int getGlobalPropertyAsInteger(String globalProperty, int defaultValue) {
		String globalPropertyValue = getGlobalProperty(globalProperty);
		
		if (globalPropertyValue == null || globalPropertyValue.isEmpty()) {
			return defaultValue;
		}
		
		try {
			return Integer.parseInt(globalPropertyValue);
		}
		catch (NumberFormatException e) {
			log.error("Error converting global property {} with value '{}' to an integer", globalProperty,
			    globalPropertyValue, e);
			return defaultValue;
		}
	}
	
	public static Map<String, String> getGlobalProperties(String... globalProperties) {
		Map<String, String> result = new HashMap<>(globalProperties.length);
		for (String globalProperty : globalProperties) {
			String globalPropertyValue = getGlobalProperty(globalProperty);
			if (globalPropertyValue != null && !globalPropertyValue.isEmpty()) {
				result.put(globalProperty, globalPropertyValue);
			}
		}
		
		return result;
	}
	
	public static void reset() {
		globalPropertyCache.clear();
		missingKeys.clear();
	}
	
	@Override
	public boolean supportsPropertyName(String globalProperty) {
		return supportsProperty(globalProperty);
	}
	
	// Listeners run on the thread that saved the property, so the tenant on that thread is the one whose
	// value changed.
	@Override
	public void globalPropertyChanged(GlobalProperty globalProperty) {
		String key = cacheKey(globalProperty.getProperty());
		String value = globalProperty.getPropertyValue();
		if (value == null || value.isEmpty()) {
			// ConcurrentHashMap takes no null value; an empty property is a miss, as a fresh read would say
			globalPropertyCache.remove(key);
			missingKeys.add(key);
		} else {
			globalPropertyCache.put(key, value);
			missingKeys.remove(key);
		}
	}
	
	@Override
	public void globalPropertyDeleted(String globalProperty) {
		String key = cacheKey(globalProperty);
		globalPropertyCache.remove(key);
		missingKeys.add(key);
	}
	
	static String cacheKey(String globalProperty) {
		return currentTenant() + KEY_SEPARATOR + globalProperty;
	}
	
	/**
	 * @return the tenant on this thread, or the empty string on a platform without tenants
	 */
	static String currentTenant() {
		if (!tenantContextLookedUp) {
			synchronized (FhirGlobalPropertyHolder.class) {
				if (!tenantContextLookedUp) {
					try {
						currentTenantMethod = Class.forName(TENANT_CONTEXT_CLASS).getMethod("getCurrentTenant");
					}
					catch (ClassNotFoundException | NoSuchMethodException | LinkageError e) {
						currentTenantMethod = null;
					}
					tenantContextLookedUp = true;
				}
			}
		}
		if (currentTenantMethod == null) {
			return "";
		}
		try {
			Object tenant = currentTenantMethod.invoke(null);
			return tenant == null ? "" : tenant.toString();
		}
		catch (ReflectiveOperationException | RuntimeException e) {
			log.debug("Could not read the current tenant; caching global properties without one", e);
			return "";
		}
	}
	
	private static boolean supportsProperty(String globalProperty) {
		return globalProperty != null
		        && (globalProperty.startsWith(FHIR2_MODULE_ID) || globalProperty.startsWith("allergy"));
	}
}
