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

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;

import org.matsim.api.core.v01.Id;
import org.matsim.contrib.ev.fleet.ElectricVehicle;
import org.matsim.contrib.ev.infrastructure.ChargerSpecification;
import org.matsim.core.api.experimental.events.EventsManager;


public class ChargingWithQueueingLogic implements ChargingLogic {
	private final ChargerSpecification charger;
	private final ChargingStrategy chargingStrategy;
	private final EventsManager eventsManager;

	private final Map<Id<ElectricVehicle>, ElectricVehicle> pluggedVehicles = new LinkedHashMap<>();
	private final Queue<ElectricVehicle> queuedVehicles = new LinkedList<>();
	private final Map<Id<ElectricVehicle>, ChargingListener> listeners = new LinkedHashMap<>();
	
	public static Map<Id, Integer> vehicleChargeStatus = new LinkedHashMap<>();
	// is initialized during first charging activity
	// 0 = driving or charging; 1 = finished charging; 2 = queuing

	public ChargingWithQueueingLogic(ChargerSpecification charger, ChargingStrategy chargingStrategy,
			EventsManager eventsManager) {
		this.chargingStrategy = Objects.requireNonNull(chargingStrategy);
		this.charger = Objects.requireNonNull(charger);
		this.eventsManager = Objects.requireNonNull(eventsManager);
	}

	
	@Override
	public void chargeVehicles(double chargePeriod, double now) {
		Iterator<ElectricVehicle> evIter = pluggedVehicles.values().iterator();
		while (evIter.hasNext()) { // loop through every plugged vehicle
			ElectricVehicle ev = evIter.next();
			ev.getBattery().changeSoc(ev.getChargingPower().calcChargingPower(charger) * chargePeriod);
			
			if (chargingStrategy.isChargingCompleted(ev)) {				
				Id<ElectricVehicle> evId = ev.getId();
				
				evIter.remove();
				eventsManager.processEvent(new ChargingEndEvent(now, charger.getId(), evId));
				listeners.remove(evId).notifyChargingEnded(ev, now);
				
				System.out.println("agent " + evId + " finished charging at t=" + now + "s = " + now/3600);

				if (ChargingWithQueueingLogic.vehicleChargeStatus.containsKey(evId)) {
					vehicleChargeStatus.replace(evId,1);
				} else {
					vehicleChargeStatus.put(evId,1);
				}
			}
		}

		int queuedToPluggedCount = Math.min(queuedVehicles.size(), charger.getPlugCount() - pluggedVehicles.size());
		for (int i = 0; i < queuedToPluggedCount; i++) {
			plugVehicle(queuedVehicles.poll(), now);
		}
	}

	@Override
	public void addVehicle(ElectricVehicle ev, double now) {
		addVehicle(ev, new ChargingListener() {}, now);
	}

	@Override
	public void addVehicle(ElectricVehicle ev, ChargingListener chargingListener, double now) {
		listeners.put(ev.getId(), chargingListener);
		double pluggedVehiclesSize = pluggedVehicles.size(); // debug
		double chargerPlugCount = charger.getPlugCount(); // debug
		if (pluggedVehicles.size() < charger.getPlugCount()) {
			plugVehicle(ev, now);
		} else {
			queueVehicle(ev, now);
		}
	}

	@Override
	public void removeVehicle(ElectricVehicle ev, double now) {
		if (pluggedVehicles.remove(ev.getId()) != null) {// successfully removed
			eventsManager.processEvent(new ChargingEndEvent(now, charger.getId(), ev.getId()));
			listeners.remove(ev.getId()).notifyChargingEnded(ev, now);
			
			if (!queuedVehicles.isEmpty()) {
				plugVehicle(queuedVehicles.poll(), now);
			}
		} else if (queuedVehicles.remove(ev)) {//
		} else {// neither plugged nor queued
			throw new IllegalArgumentException(
					"Vehicle: " + ev.getId() + " is neither queued nor plugged at charger: " + charger.getId());
		}
	}

	private void queueVehicle(ElectricVehicle ev, double now) {
		queuedVehicles.add(ev);
		Id<ElectricVehicle> evId = ev.getId();
		listeners.get(evId).notifyVehicleQueued(ev, now);
		
		System.out.println(("agent " + evId + " started queuing at t=" + now + "s = " + now/3600));
		if (ChargingWithQueueingLogic.vehicleChargeStatus.containsKey(evId)) {
			vehicleChargeStatus.replace(evId,2);
		} else {
			vehicleChargeStatus.put(evId,2);
		}
	}

	private void plugVehicle(ElectricVehicle ev, double now) {
		Id<ElectricVehicle> evId = ev.getId();
		if (pluggedVehicles.put(evId, ev) != null) {
			throw new IllegalArgumentException();
		}
		
		System.out.println("agent " + evId + " started charging at t=" + now + "s = " + now/3600);
		if (ChargingWithQueueingLogic.vehicleChargeStatus.containsKey(evId)) {
			vehicleChargeStatus.replace(evId,0);
		} else {
			vehicleChargeStatus.put(evId,0);
		}
		
		eventsManager.processEvent(new ChargingStartEvent(now, charger.getId(), evId, charger.getChargerType()));
		listeners.get(evId).notifyChargingStarted(ev, now);
	}

	private final Collection<ElectricVehicle> unmodifiablePluggedVehicles = Collections.unmodifiableCollection(
			pluggedVehicles.values());

	@Override
	public Collection<ElectricVehicle> getPluggedVehicles() {
		return unmodifiablePluggedVehicles;
	}

	private final Collection<ElectricVehicle> unmodifiableQueuedVehicles = Collections.unmodifiableCollection(
			queuedVehicles);

	@Override
	public Collection<ElectricVehicle> getQueuedVehicles() {
		return unmodifiableQueuedVehicles;
	}

	@Override
	public ChargingStrategy getChargingStrategy() {
		return chargingStrategy;
	}
}
