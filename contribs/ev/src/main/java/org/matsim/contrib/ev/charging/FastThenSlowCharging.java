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

package org.matsim.contrib.ev.charging;
/*
 * created by jbischoff, 16.11.2018
 *
 * This charging model mimics the typical behavior at fast-chargers:
 * Up to 50%, full power (or up to 1.75* C) is applied, up to
 * 75% SOC, a maximum of 1.25 * C is applied. Until full, maximum power is 0.5*C.
 * C == battery capacity.
 * This charging behavior is based on research conducted at LTH / University of Lund
 */

import org.matsim.contrib.ev.fleet.Battery;
import org.matsim.contrib.ev.fleet.ElectricVehicle;
import org.matsim.contrib.ev.infrastructure.ChargerSpecification;

public class FastThenSlowCharging implements BatteryCharging {
	private final ElectricVehicle electricVehicle;

	public FastThenSlowCharging(ElectricVehicle electricVehicle) {
		this.electricVehicle = electricVehicle;
	}

	public double calcChargingPower(double maxPower) {
		Battery b = electricVehicle.getBattery();
		double relativeSoc = b.getSoc() / b.getCapacity();

		double chargePower = 0;
		if (relativeSoc <= 0.6) {
			chargePower = Math.min(maxPower, 103000);
		} else if (relativeSoc <= 0.75) {
			chargePower = Math.min(maxPower, 77000);
		} else if (relativeSoc <= 0.9) {
			chargePower = Math.min(maxPower, 50000);
		} else {
			chargePower = Math.min(maxPower, 25000);
		}
		return chargePower;
	}

	// charge time not relevant when providing open-ended charge duration; using energy instead
	@Override
	public double calcChargingTime(ChargerSpecification charger, double energy) {
		return 7200; // arbitrary
	}

	@Override
	public double calcChargingPower(ChargerSpecification charger) {
		return calcChargingPower(charger.getPlugPower());
	}
}
