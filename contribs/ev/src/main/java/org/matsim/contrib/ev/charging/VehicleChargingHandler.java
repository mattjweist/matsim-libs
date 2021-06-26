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
 * created by jbischoff, 09.10.2018
 *  This is an events based approach to trigger vehicle charging. Vehicles will be charged as soon as a person begins a charging activity.
 * edited by mattjweist, 11.06.2021
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.ev.fleet.ElectricFleet;
import org.matsim.contrib.ev.fleet.ElectricVehicle;
import org.matsim.contrib.ev.infrastructure.Charger;
import org.matsim.contrib.ev.infrastructure.ChargingInfrastructure;
import org.matsim.contrib.ev.infrastructure.ChargingInfrastructures;
import org.matsim.contrib.ev.routing.MyCSVReader;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.events.MobsimScopeEventHandler;
import org.matsim.vehicles.Vehicle;

import com.google.common.collect.ImmutableListMultimap;

/**
 * This is an events based approach to trigger vehicle charging. Vehicles will be charged as soon as a person begins a charging activity.
 * <p>
 * Do not use this class for charging DVRP vehicles (DynAgents). In that case, vehicle charging is simulated with ChargingActivity (DynActivity)
 */
public class VehicleChargingHandler
		implements ActivityStartEventHandler, ActivityEndEventHandler, PersonLeavesVehicleEventHandler,
		ChargingEndEventHandler, MobsimScopeEventHandler {

	public static final String CHARGING_IDENTIFIER = " charging";
	public static final String CHARGING_INTERACTION = PlanCalcScoreConfigGroup.createStageActivityType(
			CHARGING_IDENTIFIER);
	private final Map<Id<Person>, Id<Vehicle>> lastVehicleUsed = new HashMap<>();
	private final Map<Id<ElectricVehicle>, Id<Charger>> vehiclesAtChargers = new HashMap<>();

	private final ChargingInfrastructure chargingInfrastructure;
	private final ElectricFleet electricFleet;
	private final ImmutableListMultimap<Id<Link>, Charger> chargersAtLinks;

	@Inject
	public VehicleChargingHandler(ChargingInfrastructure chargingInfrastructure, ElectricFleet electricFleet) {
		this.chargingInfrastructure = chargingInfrastructure;
		this.electricFleet = electricFleet;
		chargersAtLinks = ChargingInfrastructures.getChargersAtLinks(chargingInfrastructure);
	}
	
	// key = charger ID, value = # of cars at charger
	@SuppressWarnings("rawtypes")
	public static Map<Id, Integer> numVehiclesAtCharger = new LinkedHashMap<>();	

	/**
	 * This assumes no liability which charger is used, as long as the type matches - matsim
	 * Now chargers are pre-planned - mattjweist
	 * @param event
	 */
	@Override
	public void handleEvent(ActivityStartEvent event) {
		if (event.getActType().endsWith(CHARGING_INTERACTION)) {
			Id<Vehicle> vehicleId = lastVehicleUsed.get(event.getPersonId());
			if (vehicleId != null) {
				Id<ElectricVehicle> evId = Id.create(vehicleId, ElectricVehicle.class);
				if (electricFleet.getElectricVehicles().containsKey(evId)) {
					
					// import list of planned chargers for this person
					ArrayList<Id<Charger>> chargersList = new ArrayList<Id<Charger>>();
					chargersList = MyCSVReader.personChargerMap.get(event.getPersonId());
					
					// get list of chargers at this link
					ElectricVehicle ev = electricFleet.getElectricVehicles().get(evId);
					List<Charger> chargersAtLink = chargersAtLinks.get(event.getLinkId());
					// fill map of charge powers at link
					Map<Charger, Double> chargerPower = new LinkedHashMap<>();
					for (Charger charger : chargersAtLink) {
						chargerPower.put(charger, charger.getPlugPower());
					}
					// create and sort map of chargers at link by descending power	
					final Map<Charger, Double> sortedChargerPower = chargerPower.entrySet()
			                .stream()
			                .sorted((Map.Entry.<Charger, Double>comparingByValue().reversed()))
			                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
					List<Charger> sortedChargersAtLink = new ArrayList<Charger>(sortedChargerPower.keySet());
					
					Charger c = null;
					loop:
					for (Charger ch : chargersAtLink) {
						Id<Charger> chargerId = ch.getId();
						
						// initialize number of vehicles at charger if necessary
						if (!numVehiclesAtCharger.containsKey(chargerId)) {
							numVehiclesAtCharger.put(chargerId, 0);
						}
						
						// check for the planned charger
						if (chargersList.contains(chargerId)) {
							Charger chargerFromPlan = ch;		
							// check if charger has plug available
							if (numVehiclesAtCharger.get(chargerId) < ch.getPlugCount()) {							
								// choose this charger
								c = chargerFromPlan;
								break loop;
							} else {
								// loop through the chargers at the link in order of descending power
								for (Charger ch1 : sortedChargersAtLink) {
									chargerId = ch1.getId();
									// initialize number of vehicles at charger if necessary
									if (!numVehiclesAtCharger.containsKey(chargerId)) {
										numVehiclesAtCharger.put(chargerId, 0);
									}
									// if a plug is available and the power is high
									if (numVehiclesAtCharger.get(chargerId) < ch1.getPlugCount() && ch1.getPlugPower() >= 0.8*110000) {
										// choose this charger
										c = ch1;
										break loop;
									} else {
										// just stick to the original plan
										c = chargerFromPlan;
									}
								}
							}
						}	else {
							// pick some charger from the link
							c = chargersAtLink.stream()
									.filter(ch2 -> ev.getChargerTypes().contains(ch2.getChargerType()))
									.findAny()
									.get();
						}
					}
					// update number of cars at charger
					Id<Charger> chargerId = c.getId();
					numVehiclesAtCharger.put(chargerId, numVehiclesAtCharger.get(chargerId) + 1);
					
					c.getLogic().addVehicle(ev, event.getTime());
					vehiclesAtChargers.put(evId, c.getId());
				}
			}
		}
	}

	@Override
	public void handleEvent(ActivityEndEvent event) {
		if (event.getActType().endsWith(CHARGING_INTERACTION)) {
			Id<Vehicle> vehicleId = lastVehicleUsed.get(event.getPersonId());
			if (vehicleId != null) {
				Id<ElectricVehicle> evId = Id.create(vehicleId, ElectricVehicle.class);
				Id<Charger> chargerId = vehiclesAtChargers.remove(evId);

				if (chargerId != null) {
					Charger c = chargingInfrastructure.getChargers().get(chargerId);
					c.getLogic().removeVehicle(electricFleet.getElectricVehicles().get(evId), event.getTime());
				}
			}
		}
	}

	@Override
	public void handleEvent(PersonLeavesVehicleEvent event) {
		lastVehicleUsed.put(event.getPersonId(), event.getVehicleId());
	}

	@Override
	public void handleEvent(ChargingEndEvent event) {
		vehiclesAtChargers.remove(event.getVehicleId());
		//Charging has ended before activity ends
	}
}
