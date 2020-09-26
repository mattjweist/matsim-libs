/*
 * *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2020 by the members listed in the COPYING,        *
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

package org.matsim.contrib.dvrp.passenger;

import java.util.Collection;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Queue;

import javax.inject.Inject;
import javax.inject.Provider;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Route;
import org.matsim.contrib.dvrp.run.ModalProviders;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.framework.MobsimDriverAgent;
import org.matsim.core.mobsim.framework.MobsimPassengerAgent;
import org.matsim.core.mobsim.framework.MobsimTimer;
import org.matsim.core.mobsim.framework.PlanAgent;
import org.matsim.core.mobsim.qsim.DefaultTeleportationEngine;
import org.matsim.core.mobsim.qsim.InternalInterface;
import org.matsim.core.mobsim.qsim.TeleportationEngine;
import org.matsim.vis.snapshotwriters.AgentSnapshotInfo;
import org.matsim.vis.snapshotwriters.VisData;

import com.google.common.base.Preconditions;
import com.google.common.base.Verify;

/**
 * @author Michal Maciejewski (michalm)
 */
public class TeleportingPassengerEngine implements PassengerEngine, VisData {
	public static final String ORIGINAL_ROUTE_ATTRIBUTE = "originalRoute";

	public interface TeleportedRouteCalculator {
		Route calculateRoute(PassengerRequest request);
	}

	private final String mode;
	private final EventsManager eventsManager;
	private final MobsimTimer mobsimTimer;

	private final PassengerRequestCreator requestCreator;
	private final TeleportedRouteCalculator teleportedRouteCalculator;
	private final Network network;
	private final PassengerRequestValidator requestValidator;

	private final InternalPassengerHandling internalPassengerHandling;
	private final TeleportationEngine teleportationEngine;
	private final Queue<Pair<Double, PassengerRequest>> teleportedRequests = new PriorityQueue<>(
			Comparator.comparingDouble(Pair::getLeft));

	private InternalInterface internalInterface;

	public TeleportingPassengerEngine(String mode, EventsManager eventsManager, MobsimTimer mobsimTimer,
			PassengerRequestCreator requestCreator, TeleportedRouteCalculator teleportedRouteCalculator,
			Network network, PassengerRequestValidator requestValidator, Scenario scenario) {
		this.mode = mode;
		this.eventsManager = eventsManager;
		this.mobsimTimer = mobsimTimer;
		this.requestCreator = requestCreator;
		this.teleportedRouteCalculator = teleportedRouteCalculator;
		this.network = network;
		this.requestValidator = requestValidator;

		internalPassengerHandling = new InternalPassengerHandling(mode, eventsManager);
		teleportationEngine = new DefaultTeleportationEngine(scenario, eventsManager, false);
	}

	@Override
	public void setInternalInterface(InternalInterface internalInterface) {
		this.internalInterface = internalInterface;
		internalPassengerHandling.setInternalInterface(internalInterface);
		teleportationEngine.setInternalInterface(internalInterface);
	}

	@Override
	public void onPrepareSim() {
		teleportationEngine.onPrepareSim();
	}

	@Override
	public void doSimStep(double time) {
		//first process passenger dropoff events
		while (!teleportedRequests.isEmpty() && teleportedRequests.peek().getLeft() <= time) {
			PassengerRequest request = teleportedRequests.poll().getRight();
			eventsManager.processEvent(
					new PassengerDroppedOffEvent(time, mode, request.getId(), request.getPassengerId(), null));
		}

		//then end teleported rides
		teleportationEngine.doSimStep(time);
	}

	@Override
	public void afterSim() {
		teleportationEngine.afterSim();
	}

	@Override
	public boolean handleDeparture(double now, MobsimAgent agent, Id<Link> fromLinkId) {
		if (!agent.getMode().equals(mode)) {
			return false;
		}

		MobsimPassengerAgent passenger = (MobsimPassengerAgent)agent;
		Id<Link> toLinkId = passenger.getDestinationLinkId();
		Route route = ((Leg)((PlanAgent)passenger).getCurrentPlanElement()).getRoute();
		PassengerRequest request = requestCreator.createRequest(internalPassengerHandling.createRequestId(),
				passenger.getId(), route, getLink(fromLinkId), getLink(toLinkId), now, now);

		if (internalPassengerHandling.validateRequest(request, requestValidator, now)) {
			adaptRouteForTeleportation(passenger, request, now);
			eventsManager.processEvent(new PassengerPickedUpEvent(now, mode, request.getId(), passenger.getId(), null));
			teleportationEngine.handleDeparture(now, agent, fromLinkId);
			teleportedRequests.add(ImmutablePair.of(now + route.getTravelTime().seconds(), request));
		} else {
			//not much else can be done for immediate requests
			//set the passenger agent to abort - the event will be thrown by the QSim
			passenger.setStateToAbort(mobsimTimer.getTimeOfDay());
			internalInterface.arrangeNextAgentState(passenger);
		}

		return true;
	}

	private void adaptRouteForTeleportation(MobsimPassengerAgent passenger, PassengerRequest request, double now) {
		Route teleportedRoute = teleportedRouteCalculator.calculateRoute(request);

		Leg leg = (Leg)((PlanAgent)passenger).getCurrentPlanElement();
		Route originalRoute = leg.getRoute();
		Verify.verify(originalRoute.getStartLinkId().equals(teleportedRoute.getStartLinkId()));
		Verify.verify(originalRoute.getEndLinkId().equals(teleportedRoute.getEndLinkId()));
		Verify.verify(teleportedRoute.getTravelTime().isDefined());

		leg.getAttributes().putAttribute(ORIGINAL_ROUTE_ATTRIBUTE, originalRoute);
		leg.setRoute(teleportedRoute);

		eventsManager.processEvent(new PassengerRequestScheduledEvent(mobsimTimer.getTimeOfDay(), mode, request.getId(),
				request.getPassengerId(), null, now, now + teleportedRoute.getTravelTime().seconds()));
	}

	private Link getLink(Id<Link> linkId) {
		return Preconditions.checkNotNull(network.getLinks().get(linkId),
				"Link id=%s does not exist in network for mode %s. Agent departs from a link that does not belong to that network?",
				linkId, mode);
	}

	@Override
	public boolean tryPickUpPassenger(PassengerPickupActivity pickupActivity, MobsimDriverAgent driver,
			PassengerRequest request, double now) {
		throw new UnsupportedOperationException("No picking-up when teleporting");
	}

	@Override
	public void dropOffPassenger(MobsimDriverAgent driver, PassengerRequest request, double now) {
		throw new UnsupportedOperationException("No dropping-off when teleporting");
	}

	@Override
	public Collection<AgentSnapshotInfo> addAgentSnapshotInfo(Collection<AgentSnapshotInfo> positions) {
		return teleportationEngine.addAgentSnapshotInfo(positions);
	}

	public static Provider<PassengerEngine> createProvider(String mode) {
		return new ModalProviders.AbstractProvider<>(mode) {
			@Inject
			private EventsManager eventsManager;

			@Inject
			private MobsimTimer mobsimTimer;

			@Inject
			private Scenario scenario;

			@Override
			public TeleportingPassengerEngine get() {
				return new TeleportingPassengerEngine(getMode(), eventsManager, mobsimTimer,
						getModalInstance(PassengerRequestCreator.class),
						getModalInstance(TeleportedRouteCalculator.class), getModalInstance(Network.class),
						getModalInstance(PassengerRequestValidator.class), scenario);
			}
		};
	}
}
