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

package org.matsim.contrib.ev.discharging;

import org.matsim.core.controler.AbstractModule;
import org.matsim.core.mobsim.qsim.AbstractQSimModule;

/**
 * @author Michal Maciejewski (michalm)
 */
public class DischargingModule extends AbstractModule {
	@Override
	public void install() {
		bind(DriveEnergyConsumption.Factory.class).toInstance(ev -> new StaticDriveEnergyConsumption());
		installQSimModule(new AbstractQSimModule() {
			@Override
			protected void configureQSim() {
				this.bind(DriveDischargingHandler.class).asEagerSingleton();
				addMobsimScopeEventHandlerBinding().to(DriveDischargingHandler.class);
			}
		});
	}
}
