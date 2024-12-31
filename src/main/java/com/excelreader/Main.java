package com.excelreader;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import org.json.JSONObject;
import java.text.DecimalFormat;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.HashMap;

public class Main{

    final private static Map<String, String> headerMap = new HashMap<>(){{
        this.put("EMAIL STATUS", "emailStatus");
        this.put("Please provide the first three characters of your postal code", "postalCode");
        this.put("What is your total annual household income before taxes?", "annualIncome");
        this.put("What is your age?", "age");
        this.put("What is your current employment status?", "employmentStatus");
        this.put("Select the highway that you will be using the most", "mostUsedHighway");
        this.put("Select any other HOT lanes that you will be using", "hotLanes");
        this.put("What is the main purpose for your trips on this highway?", "mainPurpose");
        this.put("Please indicate when you use the HOT lanes the most", "hotLaneTime");
    }};

    final private static List<String> highwayList = new ArrayList<>(){{
        this.add("QEW");
        this.add("403");
        this.add("410");
    }};

    final private static String [] westBoundPeaks = {
        "3:00 pm - 3:59 pm",
        "4:00 pm - 4:59 pm",
        "5:00 pm - 5:59 pm",
    };

    final private static int target = 250;

    private static JSONObject winningObject = new JSONObject();

    private static List<List<String>> parseCSV(String path) {
        List<List<String>> data = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new FileReader(path))) {
            List<String[]> allRows = reader.readAll();
            for (String[] row : allRows) {
                List<String> rowData = new ArrayList<>();
                if (row.length < 3 || row[3].trim().isEmpty()) {
                    continue; // Skip this row if cell at index 2 is empty
                }
                for (int i = 1; i < Math.min(row.length, 25); i++) {
                    rowData.add(row[i]);
                }
                data.add(rowData);
            }
        } catch (IOException | CsvException e) {
            e.printStackTrace();
        }
        return data;
    }

    private static JSONObject createJSONObject(List<List<String>> data, List<String> headers){
        JSONObject jsonObject = new JSONObject();
        for (int i = 0; i < data.size(); i++) {
            List<String> row = data.get(i);
            String id = row.remove(0);
            if(id.endsWith(".0")){
                id = id.substring(0, id.length() - 2);
            }
            JSONObject rowObject = new JSONObject();
            int j = 0;
            while (j < row.size()) {
                String headerName = headers.get(j);
                if (headerMap.containsKey(headerName)) {
                    rowObject.put(headerMap.get(headers.get(j)), row.get(j));
                }
                else{
                    // Expand to QEW EB
                    if (highwayList.contains(headerName.substring(0,3))){
                        JSONObject directionObject = new JSONObject();
                        directionObject.put("Monday", row.get(j));
                        directionObject.put("Tuesday", row.get(j+1));
                        directionObject.put("Wednesday", row.get(j+2));
                        directionObject.put("Thursday", row.get(j+3));
                        directionObject.put("Friday", row.get(j+4));
                        directionObject.put("Saturday", row.get(j+5));
                        directionObject.put("Sunday", row.get(j+6));
                        switch(headerName.charAt(4)){
                            case 'E':
                                rowObject.put("eastBound", directionObject);
                                break;
                            case 'W':
                                rowObject.put("westBound", directionObject);
                                break;
                        }
                        j += 6;
                    }
                }
                j++;
            }
            jsonObject.put(id, rowObject);
        }
        return jsonObject;
    }

    private static void determineWestboundPeaks (JSONObject object){
        for (String key : object.keySet()) {
            JSONObject row = object.getJSONObject(key);
            JSONObject westBound = row.getJSONObject("westBound");

            Iterator<String> keys = westBound.keys();
            while (keys.hasNext()) {
                boolean isPeak = false;
                String time = keys.next();
                String value = westBound.getString(time);
                for (String peak : westBoundPeaks) {
                    if (value.contains(peak)) {
                        row.put("westBoundPeak", true);
                        row.put("peakContra", "Peak");
                        isPeak = true;
                        break;
                    }
                }
                if (!isPeak)
                    row.put("westBoundPeak", false);
                    row.put("peakContra", "Contra");
            }
        }
    }

    private static void determineRands(JSONObject object){
        DecimalFormat df = new DecimalFormat("#.###");
        for (String key : object.keySet()) {
            JSONObject row = object.getJSONObject(key);
            double randomValue = Math.random();
            String formattedValue = df.format(randomValue);
            if (row.getString("peakContra").equals("Peak")){
                row.put("contraRand", 0);
                row.put("peakRand", Double.parseDouble(formattedValue));
            }
            else if (row.getString("peakContra").equals("Contra")){
                row.put("peakRand", 0);
                row.put("contraRand", Double.parseDouble(formattedValue));
            }
        }
    }

    /*
     * Rank the values based on the random values
     */
    private static void rankValues(JSONObject object, String valueType){
        int toTarget = 1;
        if (valueType == "Peak"){
            toTarget += (int) Math.ceil(target * 0.55);
        }
        else if (valueType == "Contra"){
            toTarget += (int) Math.ceil(target * 0.45);
        }

        PriorityQueue<Map.Entry<String, Double>> priorityQueue = new PriorityQueue<>(
            Comparator.comparing((Map.Entry<String, Double> entry) -> entry.getValue() == 0 ? Double.MIN_VALUE : -entry.getValue())
        );

        for (String key : object.keySet()) {
            JSONObject row = object.getJSONObject(key);
            priorityQueue.add(Map.entry(key, row.getDouble(valueType.toLowerCase() + "Rand")));
        }

        int rank = 1;
        while (!priorityQueue.isEmpty()) {
            Map.Entry<String, Double> entry = priorityQueue.poll();
            object.getJSONObject(entry.getKey()).put(valueType.toLowerCase() + "Rank", rank);
            // 0 values should remain the same rank
            if (entry.getValue() != 0){
                rank++;
            }
            if (rank < toTarget){
                object.getJSONObject(entry.getKey()).put("Status", "WIN");
                winningObject.put(entry.getKey(), object.getJSONObject(entry.getKey()));
            }
            else{
                object.getJSONObject(entry.getKey()).put("Status", "LOSE");
            }
        }
    }

    /*
     * Write headers, then write the values matching the headers
     */
    private static void writeToCSV(JSONObject data, List<String> headers, String path){
        headers.add(0, "Status");
        headers.add(1, "ACTIVITY ID");
        try (FileWriter writer = new FileWriter(path)) {
            for (String header : headers) {
                writer.append(header).append(',');
            }
            writer.append("\n");

            for (String key: data.keySet()) {
                JSONObject row = data.getJSONObject(key);
                for (int i = 0; i < headers.size(); i++) {
                    String currentHeader = headers.get(i);
                    if (highwayList.contains(currentHeader.substring(0,3))){
                        JSONObject directionObject = currentHeader.charAt(4) == 'E' ? row.getJSONObject("eastBound") : row.getJSONObject("westBound");
                        for (int j = i; j < i+7; j++) {
                            if(headers.get(j).contains("Monday")){
                                writer.append(escapeCSV(directionObject.getString("Monday"))).append(',');
                            }
                            else{
                                writer.append(escapeCSV(directionObject.getString(headers.get(j)))).append(',');
                            }
                        }
                        i += 6;
                    }
                    else{
                        if (currentHeader.equals("ACTIVITY ID")){
                            writer.append(key).append(',');
                        }
                        else if (currentHeader.equals("Status")){
                            writer.append(row.getString("Status")).append(',');
                        }
                        else{
                            writer.append(escapeCSV(row.optString(headerMap.get(currentHeader), ""))).append(',');
                        }
                    }
                }
                writer.append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
     * Ensure that when writing the data to the CSV file, lines with commas don't get split into multiple columns
     * Annual Income Strings were breaking the file
     */
    private static String escapeCSV(String text) {
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            text = text.replace("\"", "\"\"");
            return "\"" + text + "\"";
        }
        return text;
    }


    /*
     * Main Function
     *
     * Program reads the csv file, parses the data, creates a JSONObject, determines the westbound peaks,
     * determines the peak/contra, determines the random values, ranks the values, and writes the results to a new csv file.
     *
     */
    public static void main (String[] args) {
        String csvFilePath = "C:\\Users\\Epic1\\Downloads\\list1144083230993983212.survey.csv";
        // String csvFilePath = "C:\\Users\\Epic1\\Downloads\\test_data.csv";
        String resultsFilePath = csvFilePath.replace(".csv", "_results.csv");

        List<List<String>> data = parseCSV(csvFilePath);
        List<String> headers = data.remove(0);
        headers.remove(0);

        JSONObject jsonData = createJSONObject(data, headers);
        determineWestboundPeaks(jsonData);
        determineRands(jsonData);
        rankValues(jsonData, "Peak");
        rankValues(jsonData, "Contra");

        // System.out.println(winningObject.toString(4));
        writeToCSV(winningObject, headers, resultsFilePath);

        // Print the data
    }
}