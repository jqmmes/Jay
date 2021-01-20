/*
 * Copyright (c) 2021 University of Porto/Faculty of Sciences (UP/FCUP).
 */

package pt.up.fc.dcc.hyrax.jay.services.profiler.status.device.usage

interface UsageManager {
    fun getRecentUsageList(usagePeriod: Long): Set<PackageUsages>
    fun getTotalMemory(): Long
    fun getFreeMemory(): Long
}