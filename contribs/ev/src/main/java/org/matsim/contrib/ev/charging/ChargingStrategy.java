/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2018 by the members listed in the COPYING,        *
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

package org.matsim.contrib.ev.charging;

import org.matsim.contrib.ev.fleet.ElectricVehicle;

/**
 * @author michalm
 */
public interface ChargingStrategy {
	default boolean isChargingCompleted(ElectricVehicle ev) {
		return ev.getBattery().getSoc() >= ev.getBattery().getCapacity(); // stop at 100% if necessary
	}

	double calcRemainingEnergyToCharge(ElectricVehicle ev);

	double calcRemainingTimeToCharge(ElectricVehicle ev);
}
