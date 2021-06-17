/* *********************************************************************** *
 * project: org.matsim.*
 * RwaWithinDayMobsimListener.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2010 by the members listed in the COPYING,        *
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

package org.matsim.contrib.ev.example;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import com.google.inject.Inject;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.framework.events.MobsimBeforeSimStepEvent;
import org.matsim.core.mobsim.framework.listeners.MobsimBeforeSimStepListener;
import org.matsim.core.mobsim.qsim.InternalInterface;
import org.matsim.core.mobsim.qsim.QSim;
import org.matsim.core.mobsim.qsim.agents.WithinDayAgentUtils;
import org.matsim.core.mobsim.qsim.interfaces.Netsim;
import org.matsim.core.router.TripRouter;
import org.matsim.withinday.utils.EditTrips;
import org.matsim.withinday.utils.EditPlans;
import org.matsim.contrib.ev.charging.ChargingWithQueueingLogic;
import org.matsim.contrib.ev.routing.MyCSVReader;

import javax.inject.Singleton;

/**
 * @author nagel
 *
 */
@Singleton
class WithinDayMobsimListenerRwa implements MobsimBeforeSimStepListener {
    
	private static final Logger log = Logger.getLogger("dummy");
	
	@Inject private TripRouter tripRouter;
	@Inject private Scenario scenario;
	
	private InternalInterface internalInterface; // mattjweist
	
	@Override
	public void notifyMobsimBeforeSimStep(@SuppressWarnings("rawtypes") MobsimBeforeSimStepEvent event) {
		
		Netsim mobsim = (Netsim) event.getQueueSimulation() ; 
		
		double now = mobsim.getSimTimer().getTimeOfDay();
		
		// check agents only every 60 seconds
		if (Math.floor(now) % 1  != 0) {
			return;
		}

		Collection<MobsimAgent> agentsToReplan = getAgentsToReplan(mobsim, now); 
		Collection<MobsimAgent> agentsToDelay = getAgentsToDelay(mobsim, now); 
	
		for (MobsimAgent ma : agentsToReplan) {
			doReplanning(ma, mobsim, now);
		}
		for (MobsimAgent ma : agentsToDelay) {
			doDelaying(ma, mobsim, now);
		}
	}
	
	private static List<MobsimAgent> getAgentsToReplan(Netsim mobsim, double now ) {

		List<MobsimAgent> set = new ArrayList<MobsimAgent>();
		
		final QSim qsim = (QSim) mobsim;
		for (MobsimAgent agent : (qsim).getAgents().values()){
			if (ChargingWithQueueingLogic.vehicleChargeStatus.containsKey(agent.getId()) && ChargingWithQueueingLogic.vehicleChargeStatus.get(agent.getId()) == 1) { // if done charging
				set.add(agent);
				ChargingWithQueueingLogic.vehicleChargeStatus.replace(agent.getId(),0); // reset to zero for next charge event
			}
		}
		return set;
	}
	
	private static List<MobsimAgent> getAgentsToDelay(Netsim mobsim, double now ) {
		
		List<MobsimAgent> set = new ArrayList<MobsimAgent>();
		
		final QSim qsim = (QSim) mobsim;
		for (MobsimAgent agent : (qsim).getAgents().values()){
			if (ChargingWithQueueingLogic.vehicleChargeStatus.containsKey(agent.getId()) && ChargingWithQueueingLogic.vehicleChargeStatus.get(agent.getId()) == 2) { // if queuing
				set.add(agent);
			}
		}
		return set;
	}
	

	private boolean doReplanning(MobsimAgent agent, Netsim mobsim, double now ) {

		Plan plan = WithinDayAgentUtils.getModifiablePlan( agent ) ; 
		if (plan == null) {
			log.info( " we don't have a modifiable plan; returning ... ") ;
			return false;
		}	
		
		EditTrips editTrips = new EditTrips(tripRouter, scenario, internalInterface);
		EditPlans editPlans = new EditPlans( (QSim) mobsim, editTrips);
		editPlans.rescheduleCurrentActivityEndtime(agent, now + 300); // add 5 minutes overhead time
		
		// finally reset the cached Values of the PersonAgent - they may have changed!
		WithinDayAgentUtils.resetCaches(agent);
		
		return true;
	}
	
	
	private boolean doDelaying(MobsimAgent agent, Netsim mobsim, double now ) {

		Plan plan = WithinDayAgentUtils.getModifiablePlan( agent ) ; 
		if (plan == null) {
			log.info( " we don't have a modifiable plan; returning ... ") ;
			return false;
		}	
		EditTrips editTrips = new EditTrips(tripRouter, scenario, internalInterface);
		EditPlans editPlans = new EditPlans( (QSim) mobsim, editTrips);	
		
		double desiredChargeDuration = 7200;
		editPlans.rescheduleCurrentActivityEndtime(agent, now + desiredChargeDuration);
		
		
		// finally reset the cached Values of the PersonAgent - they may have changed!
		WithinDayAgentUtils.resetCaches(agent);
		
		return true;
	}
}
