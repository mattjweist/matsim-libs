/*
 * *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2019 by the members listed in the COPYING,        *
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
 * *********************************************************************** *
 */

package org.matsim.contrib.ev.fleet;

import java.util.Objects;

import org.matsim.api.core.v01.Id;
import org.matsim.contrib.ev.charging.ChargingPower;
import org.matsim.contrib.ev.discharging.DriveEnergyConsumption;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

public class ElectricVehicleImpl implements ElectricVehicle {
	public static ElectricVehicle create(ElectricVehicleSpecification vehicleSpecification,
			DriveEnergyConsumption.Factory driveFactory, ChargingPower.Factory chargingFactory) {
		ElectricVehicleImpl ev = new ElectricVehicleImpl(vehicleSpecification);
		ev.driveEnergyConsumption = Objects.requireNonNull(driveFactory.create(ev));
		ev.chargingPower = Objects.requireNonNull(chargingFactory.create(ev));
		return ev;
	}

	private final ElectricVehicleSpecification vehicleSpecification;
	private final Battery battery;

	private DriveEnergyConsumption driveEnergyConsumption;
	private ChargingPower chargingPower;

	private ElectricVehicleImpl(ElectricVehicleSpecification vehicleSpecification) {
		this.vehicleSpecification = vehicleSpecification;
		battery = new BatteryImpl(vehicleSpecification.getBatteryCapacity(), vehicleSpecification.getInitialSoc());
		// consumptionRate = new ConsumptionImpl(vehicleSpecification.getConsumptionRate());
	}

	@Override
	public Id<ElectricVehicle> getId() {
		return vehicleSpecification.getId();
	}

	@Override
	public Battery getBattery() {
		return battery;
	}
	
	@Override
	public String getVehicleType() {
		return vehicleSpecification.getVehicleType();
	}

	@Override
	public ImmutableList<String> getChargerTypes() {
		return vehicleSpecification.getChargerTypes();
	}
	
	@Override
	public Double getConsumptionRate() {
		return vehicleSpecification.getConsumptionRate();
	}

	@Override
	public DriveEnergyConsumption getDriveEnergyConsumption() {
		return driveEnergyConsumption;
	}

	@Override
	public ChargingPower getChargingPower() {
		return chargingPower;
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("vehicleSpecification", vehicleSpecification)
				.add("battery", battery)
				.add("driveEnergyConsumption", driveEnergyConsumption.getClass())
				.add("chargingPower", chargingPower.getClass())
				.toString();
	}
}
