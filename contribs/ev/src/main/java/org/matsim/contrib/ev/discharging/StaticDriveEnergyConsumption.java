/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2016 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.contrib.ev.discharging;

import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.ev.fleet.ElectricVehicle;

/**
 * modified for static model by mattjweist
 */
public class StaticDriveEnergyConsumption implements DriveEnergyConsumption {
	// static consumption model -mattjweist
	@Override
	public double calcEnergyConsumption(Link link, double travelTime, double linkEnterTime, ElectricVehicle ev) {
		if (travelTime == 0) {
			return 0;
		}
		
		// double consumptionRate = 26; // J/m
		// consumptionRate = EvUnits.kWh_100km_to_J_m(consumptionRate); // convert from kWh/100km to J/m
		
		double consumption = ev.getConsumptionRate() * link.getLength();
		return consumption;
	}
}