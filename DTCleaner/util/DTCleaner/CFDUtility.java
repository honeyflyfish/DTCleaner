package DTCleaner;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

import weka.core.Instances;
import weka.core.Utils;

public class CFDUtility {

	/**
	 * Loads conditional functional dependencies from file. |RHS| == 1
	 * Syntax of file:
	 * 	5="16801"->2="240 Billiard St."
	 * 	5="16801"->3="State College"
	 * 	5="16801"->4="PA"
	 *  ...
	 * @param filename, e.g "data/CFDslist.txt"
	 * @return HashMap of CFDs
	 * @throws FileNotFoundException 
	 */
	public static HashMap<LinkedList<SimpleImmutableEntry<Integer, String>>, SimpleImmutableEntry<Integer, String>> readCFDs(String filename) throws FileNotFoundException{
		
		// Holds List<attribute#, value> of the CFD premise side as key, and entry attribute#, value as the value in hashmap
		HashMap<LinkedList<SimpleImmutableEntry<Integer,String>>, SimpleImmutableEntry<Integer,String>> CFDs = 
				new HashMap<LinkedList<SimpleImmutableEntry<Integer,String>>, SimpleImmutableEntry<Integer,String>>();
		
		System.out.println("\nReading CFDs: " + filename + "...\n");
		Scanner in = new Scanner(new FileReader(filename));
		
		while(in.hasNextLine()){
			String line = in.nextLine();
			if(line.contains("->")){
				String [] cfd = line.split("->");
				
				String [] premise;
				if(cfd[0].contains(",")) premise = cfd[0].split(",");
				else premise = new String[]{cfd[0]};
				
				LinkedList<SimpleImmutableEntry<Integer,String>> premises = new LinkedList<SimpleImmutableEntry<Integer,String>>();
				
				for(String p : premise){
					String [] w;
					if(p.contains("=")) w = p.split("=");
					else{
						// invalid CFD syntax
						System.out.println("invalid CFD syntax: "+ line);
						continue;
					}
					
					premises.add(new SimpleImmutableEntry<Integer,String>(Integer.parseInt(w[0]),Util.removeFirstAndLastChars(w[1])));
					
				}
				
				String [] rhs;
				if(cfd[1].contains("=")) rhs = cfd[1].split("=");
				else{
					// invalid CFD syntax
					System.out.println("invalid CFD syntax: "+ line);
					continue;
				}
				
				SimpleImmutableEntry<Integer, String> rhsValue = 
						new SimpleImmutableEntry<Integer,String>(Integer.parseInt(rhs[0]),Util.removeFirstAndLastChars(rhs[1]));
				
				
				CFDs.put(premises, rhsValue);
				
			}else{
				// invalid CFD syntax
				System.out.println("invalid CFD syntax: "+ line);
				continue;
			}
		}
		
		return CFDs;
	}
	
	/**
	 * Prints a summary of CFDs
	 * @param CFDs
	 * @param i
	 * @return String summary: summary of CFDs
	 */
	public static String toSummaryString(Instances i, HashMap<LinkedList<SimpleImmutableEntry<Integer, String>>, SimpleImmutableEntry<Integer, String>> CFDs){
		StringBuilder summary = new StringBuilder();
		summary.append("Num CFDs: "+CFDs.keySet().size()+"\n\n");
		
		int counter = 1;
		
		for(LinkedList<SimpleImmutableEntry<Integer, String>> premise : CFDs.keySet()){
			StringBuilder cfd = new StringBuilder();
			cfd.append(Utils.padLeft("" + (counter++), 4)+"   ");
			
			for(SimpleImmutableEntry<Integer, String> singleLHS : premise){
				cfd.append(i.attribute(singleLHS.getKey()).name());
				cfd.append("=");
				cfd.append(singleLHS.getValue());
				cfd.append(",");
			}
			// delete last ","
			cfd.deleteCharAt(cfd.length() - 1);
			
			cfd.append(" -> ");
			SimpleImmutableEntry<Integer, String> rhsValue = CFDs.get(premise);
			cfd.append(i.attribute(rhsValue.getKey()).name() + "="+rhsValue.getValue());			
			
			//add to summary
			summary.append(cfd.toString() + "\n");
		}
		
		return summary.toString();
	}
	
	/**
	 * Finds and returns a list of tuples that violates the CFDs
	 * @param i
	 * @param CFDs
	 * @return v, tupleIDs: Violated instances in weka instances format, and a list of tupleIDs and their FDs that they violate
	 */
	public static violatedTuples returnViolatedTuples(Instances i, HashMap<LinkedList<SimpleImmutableEntry<Integer, String>>, SimpleImmutableEntry<Integer, String>> CFDs){
		Instances v = new Instances(i,0);
		
		//Holds tuple index of violated tuples, and the CFD it violates
		HashMap<Integer, List<String>> tupleID = new HashMap<Integer, List<String>>();

		
		for(LinkedList<SimpleImmutableEntry<Integer, String>> premise : CFDs.keySet()){
			SimpleImmutableEntry<Integer, String> CFDRHSValue = CFDs.get(premise);
			
			//parsing CFD to String
			String cfd = "";
			for(SimpleImmutableEntry<Integer, String> entry : premise){
				cfd += entry.getKey() + "=" + entry.getValue() + ",";
			}
			//delete last ","
			cfd = cfd.substring(0, cfd.length()-1);
			cfd += "->";
			cfd += CFDRHSValue.getKey() + "=" + CFDRHSValue.getValue();
						
			for(int j = 0; j < i.numInstances(); j++){
				// holds the lhs values from the dataset that match the CFDs premise (lhs)
				LinkedList<SimpleImmutableEntry<Integer, String>> row = new LinkedList<SimpleImmutableEntry<Integer, String>>();
				for(SimpleImmutableEntry<Integer, String> entry : premise){
					row.add(new SimpleImmutableEntry<Integer, String>(entry.getKey(), i.instance(j).stringValue((entry.getKey()))));
				}
				
				SimpleImmutableEntry<Integer, String> rowRHSValue = new SimpleImmutableEntry<Integer, String>(CFDRHSValue.getKey(), i.instance(j).stringValue(CFDRHSValue.getKey()));
				
				// found violating tuple
				if(row.equals(premise) && !rowRHSValue.equals(CFDRHSValue)){
					
					if(!tupleID.containsKey(j)){
						List<String> vCFDs = new LinkedList<String>();
						vCFDs.add(cfd);
						tupleID.put(j, vCFDs);
					}else{
						tupleID.get(j).add(cfd);
					} 
					
					v.add(i.instance(j));
				}
			}		
		}

		System.out.println("Found: "+ v.numInstances() + " violating tuples.");
		
		violatedTuples pair = new violatedTuples(v, tupleID);
		return pair;

	}
	
	/**
	 * Returns the CFD by it's name, e.g. given  5="16801"->2="240 Billiard St.", return ZIPCode=16801 -> Address1=240 Billiard St.
	 * @param i, CFD
	 * @return String representation of the CFDs with the attribute names included
	 */
	public static String CFDtoString(Instances i, String CFD){
		// TBW
		return null;
	}
	
}
