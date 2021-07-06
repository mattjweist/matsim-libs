package org.matsim.contrib.ev.routing;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.matsim.api.core.v01.Id;
import org.matsim.contrib.ev.infrastructure.Charger;
import org.matsim.api.core.v01.population.Person;


public class MyCSVReader {
	
	// First create a map of agents and their planned charging stations
	// Then create a map for the first map and their times
	public static Map<Id<Person>, ArrayList<Id<Charger>>> personChargerMap = new LinkedHashMap<>();
	// public static Map<Map<Id<Person>, ArrayList<Id<Charger>>>, ArrayList<Double>> personChargerTimeMap = new LinkedHashMap<>();
	public static Map<Id<Person>, ArrayList<Double>> personEndSocMap = new LinkedHashMap<>();


	public static void loadChargeMaps(String path) {
		
			String line = "";
			
			try {
				BufferedReader br = new BufferedReader(new FileReader(path));
				while((line = br.readLine()) != null) { // read each line (person) one by one
					if (!line.startsWith("V")) { // "Vehicle ID" -- skip header row
						String[] values = line.split(",");
						
						// create array lists for chargers, end SOCs
						ArrayList<Id<Charger>> chargersList = new ArrayList<Id<Charger>>();
						ArrayList<Double> endSocList = new ArrayList<Double>();
						for (int i = 1; i<=(values.length-1)/2; i++) {
							// read charger id
							Id<Charger> chargerId = Id.create(values[2*i-1], Charger.class);
							chargersList.add(chargerId);
							// read end SOC
							Double endSoc = Double.parseDouble(values[2*i]);
							endSocList.add(endSoc);
						}
						// read person id
						Id<Person> personId = Id.create(values[0], Person.class);
						// populate maps
						personChargerMap.put(personId,chargersList);
						// personChargerTimeMap.put(personChargerMap, chargeTimeList);
						personEndSocMap.put(personId,endSocList);
					}
				}
				
				br.close();
				System.out.println("Charge map: " + personChargerMap);
				System.out.println("End SOC map: " + personEndSocMap);
				
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
	}
}
