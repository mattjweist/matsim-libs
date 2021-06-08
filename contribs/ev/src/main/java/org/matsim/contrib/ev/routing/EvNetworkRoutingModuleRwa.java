/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2015 by the members listed in the COPYING,        *
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
package org.matsim.contrib.ev.routing;

import java.util.ArrayList;
import java.util.List;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.ev.EvConfigGroup;
import org.matsim.contrib.ev.charging.ChargingWithQueueingLogic;
import org.matsim.contrib.ev.charging.VehicleChargingHandler;
import org.matsim.contrib.ev.fleet.ElectricFleetSpecification;
import org.matsim.contrib.ev.fleet.ElectricVehicle;
import org.matsim.contrib.ev.infrastructure.Charger;
import org.matsim.contrib.ev.infrastructure.ChargerSpecification;
import org.matsim.contrib.ev.infrastructure.ChargingInfrastructureSpecification;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.LinkWrapperFacility;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.facilities.Facility;

/**
 * This network Routing module adds stages for re-charging into the Route.
 * This wraps a "computer science" {@link LeastCostPathCalculator}, which routes from a node to another node, into something that
 * routes from a {@link Facility} to another {@link Facility}, as we need in MATSim.
 *
 * @author jfbischoff
 * 
 * modified for RWA by mattjweist
 */

public final class EvNetworkRoutingModuleRwa implements RoutingModule {

	private final String mode;
	private final Network network;
	private final RoutingModule delegate;
	private final ElectricFleetSpecification electricFleet;
	private final ChargingInfrastructureSpecification chargingInfrastructureSpecification;
	private final String stageActivityModePrefix;
	private final String vehicleSuffix;
	private final EvConfigGroup evConfigGroup;

	public EvNetworkRoutingModuleRwa(final String mode, final Network network, RoutingModule delegate,
			ElectricFleetSpecification electricFleet,
			ChargingInfrastructureSpecification chargingInfrastructureSpecification, 
			EvConfigGroup evConfigGroup) {
		Gbl.assertNotNull(network);
		this.delegate = delegate;
		this.network = network;
		this.mode = mode;
		this.electricFleet = electricFleet;
		this.chargingInfrastructureSpecification = chargingInfrastructureSpecification;
		stageActivityModePrefix = mode + VehicleChargingHandler.CHARGING_IDENTIFIER;
		this.evConfigGroup = evConfigGroup;
		this.vehicleSuffix = mode.equals(TransportMode.car) ? "" : "_" + mode;
	}
	
	@Override
	public List<? extends PlanElement> calcRoute(final Facility fromFacility, final Facility toFacility,
			final double departureTime, final Person person) {
		List<? extends PlanElement> basicRoute = delegate.calcRoute(fromFacility, toFacility, departureTime, person);
		Id<ElectricVehicle> evId = Id.create(person.getId() + vehicleSuffix, ElectricVehicle.class);
		
		if (!electricFleet.getVehicleSpecifications().containsKey(evId)) {
			return basicRoute;
		} else {
			
			ChargingWithQueueingLogic.vehicleChargeStatus.put(evId,0); // initialize charge status
			
			double numberOfStops = MyCSVReader.personChargerMap.get(person.getId()).size(); // number of charging stations
			
			if (numberOfStops < 1) {
				return basicRoute;
			} else {

				List<PlanElement> stagedRoute = new ArrayList<>();
				Facility lastFrom = fromFacility;
				double lastArrivaltime = departureTime;
				
				ArrayList<Id<Charger>> chargersList = new ArrayList<Id<Charger>>(); // initialize list of chargers
				chargersList = MyCSVReader.personChargerMap.get(person.getId()); // fill list of chargers for person
				// ArrayList<Double> chargeTimeList = new ArrayList<Double>(); // initialize list of charge times
				// chargeTimeList = MyCSVReader.personTimeMap.get(person.getId()); // fill list of charge times for person
						
				for (int i = 0; i < numberOfStops; i++) {
									
					Id<Charger> chargerId = chargersList.get(i); // pull ith charger from table
				
					ChargerSpecification selectedCharger = chargingInfrastructureSpecification.getChargerSpecifications().get(chargerId);
					
					Link selectedChargerLink = network.getLinks().get(selectedCharger.getLinkId()); // get corresponding link ID
					Facility nexttoFacility = new LinkWrapperFacility(selectedChargerLink);
					if (nexttoFacility.getLinkId().equals(lastFrom.getLinkId())) {
						continue;
					}
					
					List<? extends PlanElement> routeSegment = delegate.calcRoute(lastFrom, nexttoFacility,
							lastArrivaltime, person);
					
					Leg lastLeg = (Leg)routeSegment.get(0);
					lastArrivaltime = lastLeg.getDepartureTime().seconds() + lastLeg.getTravelTime().seconds();
					stagedRoute.add(lastLeg);
					Activity chargeAct = PopulationUtils.createStageActivityFromCoordLinkIdAndModePrefix(selectedChargerLink.getCoord(),
							selectedChargerLink.getId(), stageActivityModePrefix);
					// double desiredChargingTime = chargeTimeList.get(i); // pull ith charge time from table
					double desiredChargingTime = 7200; // set arbitrarily long charge time
					chargeAct.setMaximumDuration(Math.max(evConfigGroup.getMinimumChargeTime(), desiredChargingTime));
					lastArrivaltime += chargeAct.getMaximumDuration().seconds();
					stagedRoute.add(chargeAct);
					lastFrom = nexttoFacility;
				}
				stagedRoute.addAll(delegate.calcRoute(lastFrom, toFacility, lastArrivaltime, person));
				return stagedRoute;
			}
		}
	}

	@Override
	public String toString() {
		return "[NetworkRoutingModule: mode=" + this.mode + "]";
	}

}
