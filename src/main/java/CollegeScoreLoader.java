import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

/**
 * The type College score loader.
 */
public class CollegeScoreLoader {

    File csvFile; //CSV file for storing college information
    String apiFields, apiEarnings, apiDefRate, apiState, apiKey; //Strings to build API URL
    int apiYear,apiPage,apiPerPage; //ints to specify API URL at specific year, page, results
    int numPassed, numFailed; //number of schools that passed and failed for specified year
    JsonArray results; //master array of colleges within a year

    /**
     * Instantiates a new College score loader.
     */
    CollegeScoreLoader() {
        apiFields = "fields=id,school.name,school.state,";
        apiYear = 1996;
        apiPerPage = 100;
        numPassed = 0;
        numFailed = 0;
        apiEarnings = ".earnings.10_yrs_after_entry.working_not_enrolled.mean_earnings";
        apiDefRate = ".repayment.3_yr_default_rate";
        apiState = "&school.state=IL";
        apiKey = "&api_key=" + System.getenv("COLLEGE_API");
        results = new JsonArray();
    }

    /**
     * Returns whether the mean earnings for students not enrolled 10 years after entry is
     * greater than $45, 000 and the 3-year default rate is less than 2.5%
     *
     * @param index the college at specified index in {@code results}
     * @return 1 for true; otherwise 0
     */
    int doesCollegePass(int index) {
        if (getEarnings(index) > 45000 && getDefaultRate(index) < .025) {
            numPassed++;
            return 1;
        }
        numFailed++;
        return 0;
    }

    /**
     * Gets the mean earnings for students not enrolled 10 years after entry.
     *  Checks if value is null. If so, set mean earnings to -1 (less than 45,000) to
     *  disqualify the entry.
     *
     * @param index the college at specified index in {@code results}
     * @return the mean earnings
     */
    int getEarnings(int index) {
        JsonObject result = results.get(index).getAsJsonObject();
        JsonElement earningElement = result.get(apiYear + apiEarnings);
        return !earningElement.isJsonNull() ? earningElement.getAsInt() : -1;
    }

    /**
     * Gets the 3-year cohort default rate.
     * Checks if value is null. If so, set rate to 1 (greater than 0.025) to disqualify the
     * entry.
     *
     * @param index the college at specified index in {@code results}
     * @return the default rate
     */
    double getDefaultRate(int index) {
        JsonObject result = results.get(index).getAsJsonObject();
        JsonElement defRateElement = result.get(apiYear + apiDefRate);
        return !defRateElement.isJsonNull() ? defRateElement.getAsDouble() : 1;
    }

    /**
     * Gets id of specified college index.
     *
     * @param index the college at specified index in {@code results}
     * @return the college id
     */
    String getID(int index) {
        JsonObject result = results.get(index).getAsJsonObject();
        JsonElement id = result.get("id");
        return id.getAsString();
    }

    /**
     * Gets school name.
     *
     * @param index the college at specified index in {@code results}
     * @return the school name
     */
    String getSchoolName(int index) {
        JsonObject result = results.get(index).getAsJsonObject();
        JsonElement schoolName = result.get("school.name");
        return schoolName.getAsString();
    }

    /**
     * Call College Score Card API to retrieve and return Json Object for parsing.
     *
     * @return the json object
     */
    JsonObject callApiForRootObject() {
        URL encodedUrl;
        InputStreamReader reader;
        JsonElement je;
        JsonObject jo = new JsonObject();
        String apiURL = "https://api.data.gov/ed/collegescorecard/v1/schools?sort" +
                        "=school.name&per_page="
                        + apiPerPage
                        + "&page="
                        + apiPage
                        + "&"
                        + apiFields
                        + apiYear
                        + apiEarnings
                        + ","
                        + apiYear
                        + apiDefRate
                        + apiState
                        + apiKey;
        try {
            //encodes URL and hits endpoint
            encodedUrl = new URL(apiURL);
            reader = new InputStreamReader(encodedUrl.openStream());
            //Converts data to Json and assigns to Json Object
            je = JsonParser.parseReader(reader);
            jo = je.getAsJsonObject();
            reader.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return jo;
    }

    /**
     * Builds (@code results} array of all colleges within the specified year.
     *
     * @param year the specified year
     */
    void getResultsObjectForYear(int year) {

        apiYear = year;
        apiPage = 0;
        numPassed = 0;
        numFailed = 0;
        //Make first API call to get total number of colleges and pages to parse
        JsonObject root = callApiForRootObject();
        JsonObject metadata = root.getAsJsonObject("metadata");
        int totalPages =
                (Integer.parseInt(metadata.get("total")
                                          .getAsString()) / apiPerPage) + 1;
        //Iterate through Json on each page, building up the array
        do {
            root = callApiForRootObject();
            results.addAll(root.getAsJsonArray("results"));
            apiPage++;
        } while (apiPage < totalPages);
    }

    /**
     * Write to csv.
     *
     * @param fw    FileWrite to write to CSV
     * @param index the college at specified index in {@code results}
     * @throws IOException If an error is thrown while writing to file
     */
    void writeToCSV(FileWriter fw, int index) throws IOException {
        String collegeInfoHeader =
                getSchoolName(index) + "," + getID(index) + "," + doesCollegePass(index) +
                "\n";
        fw.write(collegeInfoHeader);
    }

    /**
     * Create csv in user's Desktop folder named CollegeScoreCard.csv
     * 3 column table with year, college id, and boolean result of conditional
     */
    void createCSV() {
        try {
            String homePath = System.getProperty("user.home");
            String desktopPath = homePath + File.separator + "Desktop" + File.separator +
                                 "CollegeScoreCard.csv";
            csvFile = new File(desktopPath);
            FileWriter fw = new FileWriter(csvFile,true);
            for (int year = 0; year < 22; year++) {
                fw.write(apiYear + " schools" + ",id,pass" +
                         "\n");
                getResultsObjectForYear(apiYear);
                for (int elem = 0; elem < results.size(); elem++) {
                    writeToCSV(fw,elem);
                }
                fw.write("\n" + apiYear + " number of passed schools: " + numPassed
                         + "\n" + apiYear + " number of failed schools: " + numFailed + "\n");
                apiYear++;
                results = new JsonArray();
            }
            fw.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}