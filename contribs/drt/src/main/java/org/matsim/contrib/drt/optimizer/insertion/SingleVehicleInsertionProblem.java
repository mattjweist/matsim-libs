/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2017 by the members listed in the COPYING,        *
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

package org.matsim.contrib.drt.optimizer.insertion;

import static org.matsim.contrib.drt.optimizer.insertion.InsertionCostCalculator.INFEASIBLE_SOLUTION_COST;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

import org.matsim.contrib.drt.passenger.DrtRequest;

/**
 * @author michalm
 */
public class SingleVehicleInsertionProblem<D> {
	private static class InsertionWithCost<D> {
		private final InsertionWithDetourData<D> insertionWithDetourData;
		private final double cost;

		private InsertionWithCost(InsertionWithDetourData<D> insertionWithDetourData, double cost) {
			this.insertionWithDetourData = insertionWithDetourData;
			this.cost = cost;
		}
	}

	private final InsertionCostCalculator costCalculator;
	private final ToDoubleFunction<D> detourTime;

	SingleVehicleInsertionProblem(ToDoubleFunction<D> detourTime, InsertionCostCalculator costCalculator) {
		this.costCalculator = costCalculator;
		this.detourTime = detourTime;
	}

	public Optional<InsertionWithDetourData<D>> findBestInsertion(DrtRequest drtRequest,
			Stream<InsertionWithDetourData<D>> insertions) {
		return insertions.map(insertion -> new InsertionWithCost<>(insertion,
				costCalculator.calculate(drtRequest, insertion, detourTime)))
				.filter(iWithCost -> iWithCost.cost < INFEASIBLE_SOLUTION_COST)
				.max(Comparator.comparingDouble(insertionWithCost -> insertionWithCost.cost))
				.map(insertionWithCost -> insertionWithCost.insertionWithDetourData);
	}
}
