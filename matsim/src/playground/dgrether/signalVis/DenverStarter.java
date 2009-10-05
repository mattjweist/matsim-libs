/* *********************************************************************** *
 * project: org.matsim.*
 * DenverStarter
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
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
package playground.dgrether.signalVis;

import org.matsim.api.core.v01.ScenarioImpl;
import org.matsim.core.events.EventsImpl;
import org.matsim.core.scenario.ScenarioLoader;

import playground.dgrether.DgPaths;


/**
 * @author dgrether
 *
 */
public class DenverStarter {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		String configFile = DgPaths.STUDIESDG + "denver/dgConfig.xml";
		
		ScenarioLoader scl = new ScenarioLoader(configFile);
		ScenarioImpl sc = scl.loadScenario();
		EventsImpl e = new EventsImpl();
		DgOnTheFlyQueueSimQuad sim = new DgOnTheFlyQueueSimQuad(sc, e);
		sim.setLaneDefinitions(sc.getLaneDefinitions());
		sim.setSignalSystems(sc.getSignalSystems(), sc.getSignalSystemConfigurations());
		sim.run();
		
		
	}

}
