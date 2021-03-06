/* *********************************************************************** *
 * project: org.matsim.*
 * ControlerScoringEvent.java.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
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

package org.matsim.core.controler.events;

import org.matsim.core.controler.MatsimServices;
import org.matsim.core.replanning.ReplanningContext;

/**
 * Event class to notify observers that replanning should happen
 *
 * @author mrieser
 */
public final class ReplanningEvent extends AbstractIterationEvent {
	public ReplanningEvent(MatsimServices services, int iteration, boolean isLastIteration) {
		super(services, iteration, isLastIteration);
	}

	public ReplanningContext getReplanningContext() {
		return services.getInjector().getInstance(ReplanningContext.class);
	}

}
