// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp.features.charging

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.codetics.evapp.R

@Composable
fun stopReasonLabel(reason: String): String =
    when (reason) {
        "Local" -> stringResource(R.string.stop_reason_stopped_at_the_station)
        "Remote" -> stringResource(R.string.stop_reason_stopped_remotely)
        "EVDisconnected" -> stringResource(R.string.stop_reason_vehicle_unplugged)
        "EmergencyStop" -> stringResource(R.string.stop_reason_emergency_stop)
        "PowerLoss" -> stringResource(R.string.stop_reason_power_outage)
        "HardReset", "SoftReset", "Reboot", "ImmediateReset" -> stringResource(R.string.stop_reason_station_restarted)
        "UnlockCommand" -> stringResource(R.string.stop_reason_connector_unlocked_remotely)
        "DeAuthorized" -> stringResource(R.string.stop_reason_authorization_withdrawn)
        "StoppedByEV" -> stringResource(R.string.stop_reason_stopped_by_the_vehicle)
        "SOCLimitReached" -> stringResource(R.string.stop_reason_battery_limit_reached)
        "TimeLimitReached" -> stringResource(R.string.stop_reason_time_limit_reached)
        "EnergyLimitReached" -> stringResource(R.string.stop_reason_energy_limit_reached)
        "Timeout" -> stringResource(R.string.stop_reason_stop_timed_out)
        "MasterPass" -> stringResource(R.string.stop_reason_stopped_with_a_master_pass)
        "LocalOutOfCredit" -> stringResource(R.string.stop_reason_credit_exhausted)
        "GroundFault" -> stringResource(R.string.stop_reason_ground_fault)
        "OvercurrentFault" -> stringResource(R.string.stop_reason_overcurrent)
        "PowerQuality" -> stringResource(R.string.stop_reason_power_quality_issue)
        "StaleSession" -> stringResource(R.string.stop_reason_closed_without_news_from_the_station)
        "SoftStop" -> stringResource(R.string.stop_reason_closed_by_the_operator)
        "Other" -> stringResource(R.string.stop_reason_other_reason)
        else -> reason
    }
