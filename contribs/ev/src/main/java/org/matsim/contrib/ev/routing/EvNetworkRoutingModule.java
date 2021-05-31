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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
import org.matsim.contrib.ev.discharging.AuxEnergyConsumption;
import org.matsim.contrib.ev.discharging.DriveEnergyConsumption;
import org.matsim.contrib.ev.fleet.ElectricFleetSpecification;
import org.matsim.contrib.ev.fleet.ElectricVehicle;
import org.matsim.contrib.ev.fleet.ElectricVehicleImpl;
import org.matsim.contrib.ev.fleet.ElectricVehicleSpecification;
import org.matsim.contrib.ev.infrastructure.Charger;
import org.matsim.contrib.ev.infrastructure.ChargerSpecification;
import org.matsim.contrib.ev.infrastructure.ChargingInfrastructureSpecification;
import org.matsim.contrib.util.StraightLineKnnFinder;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.LinkWrapperFacility;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelTime;
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

public final class EvNetworkRoutingModule implements RoutingModule {

	private final String mode;

	private final Network network;
	private final RoutingModule delegate;
	private final ElectricFleetSpecification electricFleet;
	private final ChargingInfrastructureSpecification chargingInfrastructureSpecification;
	private final Random random = MatsimRandom.getLocalInstance();
	private final TravelTime travelTime;
	private final DriveEnergyConsumption.Factory driveConsumptionFactory;
	private final AuxEnergyConsumption.Factory auxConsumptionFactory;
	private final String stageActivityModePrefix;
	private final String vehicleSuffix;
	private final EvConfigGroup evConfigGroup;

	public EvNetworkRoutingModule(final String mode, final Network network, RoutingModule delegate,
			ElectricFleetSpecification electricFleet,
			ChargingInfrastructureSpecification chargingInfrastructureSpecification, TravelTime travelTime,
			DriveEnergyConsumption.Factory driveConsumptionFactory, AuxEnergyConsumption.Factory auxConsumptionFactory,
			EvConfigGroup evConfigGroup) {
		this.travelTime = travelTime;
		Gbl.assertNotNull(network);
		this.delegate = delegate;
		this.network = network;
		this.mode = mode;
		this.electricFleet = electricFleet;
		this.chargingInfrastructureSpecification = chargingInfrastructureSpecification;
		this.driveConsumptionFactory = driveConsumptionFactory;
		this.auxConsumptionFactory = auxConsumptionFactory;
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
			Leg basicLeg = (Leg)basicRoute.get(0);
			ElectricVehicleSpecification ev = electricFleet.getVehicleSpecifications().get(evId);
			
			ChargingWithQueueingLogic.vehicleChargeStatus.put(evId,0); // mattjweist

			Map<Link, Double> estimatedEnergyConsumption = estimateConsumption(ev, basicLeg);
			double estimatedOverallConsumption = estimatedEnergyConsumption.values()
					.stream()
					.mapToDouble(Number::doubleValue)
					.sum() * 1;
			double capacity = ev.getBatteryCapacity() * (0.7 + random.nextDouble() * 0.1); // stop at 20-30% battery
			
			/* mattjweist */ 
			// Important to note that this routing strategy expects charging up to 80%.
			// If a different charging strategy is used (ex: chargeToMaxSocStrategy),
			// then an insufficient number of stops will be planned.
			double initialCapacity;
			if (ev.getBatteryCapacity() == ev.getInitialSoc()) {
				initialCapacity = capacity;
			} else {
				initialCapacity = capacity - (ev.getBatteryCapacity() - ev.getInitialSoc());
			}
			
			capacity = capacity - 0.2 * ev.getBatteryCapacity(); // adjusted for charge to 80% strategy
			double numberOfStops = Math.ceil( (estimatedOverallConsumption - initialCapacity) / capacity);
			
			double minSocArrival = 0.5 * ev.getBatteryCapacity(); // minimum SOC upon arrival
			double maxCharge = 0.8 * ev.getBatteryCapacity(); // maximum SOC after charging
			
			if (numberOfStops < 1) {
				return basicRoute;
			} else {
				List<Link> stopLocations = new ArrayList<>();
				double currentConsumption = 0;
				for (Map.Entry<Link, Double> e : estimatedEnergyConsumption.entrySet()) {
					currentConsumption += e.getValue();				
		
					/* mattjweist */ 
					// First stop 
					// TODO: consider MinSocArrival in case of only one stop
					if ( currentConsumption > initialCapacity && stopLocations.isEmpty()) {
						stopLocations.add(e.getKey());
						currentConsumption = 0;
					}
					// all remaining stops except last stop
					else if ( (stopLocations.size() < numberOfStops - 1) && (currentConsumption > capacity)  && !stopLocations.isEmpty()) { // adjusted to stop at 80% charge
						stopLocations.add(e.getKey());
						currentConsumption = 0;
					}
					// last stop
					else if ( (stopLocations.size() >= numberOfStops - 1) && (maxCharge - currentConsumption < minSocArrival) ) { // adjusted to stop at 80% charge
						stopLocations.add(e.getKey());
						currentConsumption = 0;
					}
				}
				
				/* mattjweist */ 
				// Important to note that this routing strategy expects a constant charge rate.
				List<PlanElement> stagedRoute = new ArrayList<>();
				Facility lastFrom = fromFacility;
				double lastArrivaltime = departureTime;
				
				for (Link stopLocation : stopLocations) {

					StraightLineKnnFinder<Link, ChargerSpecification> straightLineKnnFinder = new StraightLineKnnFinder<>(
							2, Link::getCoord, s -> network.getLinks().get(s.getLinkId()).getCoord());
					List<ChargerSpecification> nearestChargers = straightLineKnnFinder.findNearest(stopLocation,
							chargingInfrastructureSpecification.getChargerSpecifications()
									.values()
									.stream()
									.filter(charger -> ev.getChargerTypes().contains(charger.getChargerType())));
					// ChargerSpecification selectedCharger = nearestChargers.get(random.nextInt(1));
					ChargerSpecification selectedCharger = nearestChargers.get(0);
					Id<Charger> chargerId = selectedCharger.getId();
					
					Link selectedChargerLink = network.getLinks().get(selectedCharger.getLinkId());
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
					double maxPowerEstimate = Math.min(selectedCharger.getPlugPower(), 110000); // Mercedes EQC max DC charging 110kW -mattjweist
					double estimatedChargingTime = capacity / maxPowerEstimate * 100; // overestimate. to be replaced by algo. -mattjweist
					chargeAct.setMaximumDuration(Math.max(evConfigGroup.getMinimumChargeTime(), estimatedChargingTime));
					lastArrivaltime += chargeAct.getMaximumDuration().seconds();
					stagedRoute.add(chargeAct);
					lastFrom = nexttoFacility;
				}
				stagedRoute.addAll(delegate.calcRoute(lastFrom, toFacility, lastArrivaltime, person));
				return stagedRoute;
			}
		}
	}

	private Map<Link, Double> estimateConsumption(ElectricVehicleSpecification ev, Leg basicLeg) {
		Map<Link, Double> consumptions = new LinkedHashMap<>();
		NetworkRoute route = (NetworkRoute)basicLeg.getRoute();
		List<Link> links = NetworkUtils.getLinks(network, route.getLinkIds());
		ElectricVehicle pseudoVehicle = ElectricVehicleImpl.create(ev, driveConsumptionFactory, auxConsumptionFactory,
				v -> charger -> {
					throw new UnsupportedOperationException();
				});
		DriveEnergyConsumption driveEnergyConsumption = pseudoVehicle.getDriveEnergyConsumption();
		AuxEnergyConsumption auxEnergyConsumption = pseudoVehicle.getAuxEnergyConsumption();
		double linkEnterTime = basicLeg.getDepartureTime().seconds();
		for (Link l : links) {
			double travelT = travelTime.getLinkTravelTime(l, basicLeg.getDepartureTime().seconds(), null, null);

			double consumption = driveEnergyConsumption.calcEnergyConsumption(l, travelT, linkEnterTime)
					+ auxEnergyConsumption.calcEnergyConsumption(basicLeg.getDepartureTime().seconds(), travelT, l.getId());
	
			consumptions.put(l, consumption); // mattjweist
			linkEnterTime += travelT;
		}
		return consumptions;
	}

	@Override
	public String toString() {
		return "[NetworkRoutingModule: mode=" + this.mode + "]";
	}

}
