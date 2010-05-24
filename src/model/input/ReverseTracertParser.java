package model.input;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;


import model.Action;
import model.PartitionGraph;

public class ReverseTracertParser extends DefaultScalableTraceParser {
	
	public PartitionGraph parseTraceFile(String fileName, int linesToRead, int __unused) {
		try {
			FileInputStream fstream = new FileInputStream(fileName);
			BufferedReader br = new BufferedReader(new InputStreamReader(
					fstream));
			ArrayList<String> traceLines = new ArrayList<String>();
			String strLine = null;
			int linesRead = 0;
			while ((strLine = br.readLine()) != null) {
				if (linesRead++ > linesToRead) break;
				traceLines.add(strLine);
			}
			br.close();

			// Build the graph
			return parseTrace(traceLines.toArray(new String[] {}));
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
			return null;
		}
	}
	
	public boolean isSeparator(String line) {
		return (line.equals("REACHED") ||
				line.equals("FOUND") ||
				line.equals("NONE") ||
				line.equals("FAILED"));
	}

	public PartitionGraph parseTrace(String[] traceLines) {
		GraphBuilder gb = new GraphBuilder();
		boolean nextSplit = false;
		for (String line : traceLines) {
			if (nextSplit) {
				gb.split();
				nextSplit = false;
			}
			
			if (line.trim().length() == 0) continue;
			
			if (isSeparator(line)) {
				nextSplit = true;
			} 
			

			Action a = parseTraceEntry(line);
			gb.append(a);
			
		}
		return gb.getGraph(false);
	}

	private Action parseTraceEntry(String entry) {
		return new Action(entry);
	}

}
