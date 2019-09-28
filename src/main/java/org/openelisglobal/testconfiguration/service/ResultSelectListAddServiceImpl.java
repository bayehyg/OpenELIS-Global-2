package org.openelisglobal.testconfiguration.service;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.openelisglobal.common.util.IdValuePair;
import org.openelisglobal.dictionary.service.DictionaryService;
import org.openelisglobal.dictionary.valueholder.Dictionary;
import org.openelisglobal.localization.service.LocalizationService;
import org.openelisglobal.localization.valueholder.Localization;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.test.valueholder.Test;
import org.openelisglobal.testconfiguration.form.ResultSelectListForm;
import org.openelisglobal.testresult.service.TestResultService;
import org.openelisglobal.testresult.valueholder.TestResult;
import org.openelisglobal.typeoftestresult.service.TypeOfTestResultServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;


@Service
public class ResultSelectListAddServiceImpl implements ResultSelectListAddService {

    @Autowired
    private DictionaryService dictionaryService;
    @Autowired
    private TestService testService;
    @Autowired
    private TestResultService resultService;
    @Autowired
    private LocalizationService localizationService;

    @Override
    public Map<String, List<IdValuePair>> getTestSelectDictionary() {
        List<TestResult> testResults = resultService.getAllSortedTestResults();

        Map<String, List<IdValuePair>> testDictionary = new HashMap<>();
        String currentTestId = null;
        String dictionaryIdGroup = null;
        for (TestResult testResult : testResults) {
            if (TypeOfTestResultServiceImpl.ResultType.isDictionaryVariant(testResult.getTestResultType())) {
                if (testResult.getTest().getId().equals(currentTestId)) {
                    dictionaryIdGroup += "," + testResult.getValue();
                } else {
                    String previousTestId = currentTestId;
                    currentTestId = testResult.getTest().getId();
                    if (dictionaryIdGroup != null) {
                        List<IdValuePair> pairs = new ArrayList<>();
                        String[] dictionaryIds = dictionaryIdGroup.split(",");
                        for (String id : dictionaryIds) {
                            Dictionary dictionary = dictionaryService.getDictionaryById(id);
                            if (dictionary != null) {
                                pairs.add(new IdValuePair(id, dictionary.getLocalizedName()));
                            }
                        }
                        testDictionary.put(previousTestId, pairs);
                    }
                    dictionaryIdGroup = testResult.getValue();
                }
            }
        }
        return testDictionary;
    }

    @Override
    public boolean addResultSelectList(ResultSelectListForm form, String currentUserId) {

        Dictionary dictionary = new Dictionary();
        dictionary.setSortOrder(1);
        dictionary.setIsActive("Y");
        dictionary.setDictEntry(form.getNameEnglish());
        dictionary.setLocalAbbreviation(form.getNameEnglish());
        dictionary.setSysUserId(currentUserId);

        Localization localization = new Localization();
        localization.setEnglish(form.getNameEnglish());
        localization.setFrench(form.getNameFrench());
        localization.setSysUserId(currentUserId);
        localization = localizationService.save(localization);

        dictionary.setLocalizedDictionaryName(localization);
        dictionary = dictionaryService.save(dictionary);

        String s = form.getTestSelectListJson();

        JSONParser parser = new JSONParser();
        try {
            JSONObject obj = (JSONObject)parser.parse(s);
            String testsStr = (String) obj.get("tests");
            JSONArray tests = (JSONArray) parser.parse(testsStr);
            for(int j = 0; j < tests.size(); j++) {
                JSONObject testObject = (JSONObject)tests.get(j);

                String testId = (String)testObject.get("id");
                Test test = testService.getTestById(testId);
                JSONArray items = (JSONArray)testObject.get("items");

                for (int i = 0; i < items.size(); i++) {
                    JSONObject object = (JSONObject)items.get(i);

                    if (object.containsKey("id")) {
                        Map<String, Object> filter = new HashMap<>();
                        filter.put("test.id", testId);
                        filter.put("value", object.get("id"));
                        TestResult testResult = resultService.getMatch(filter).get(); // get((String) object.get("id"));
                        long order = (Long) object.get("order");
                        testResult.setSortOrder(String.valueOf(10 * order));
                        testResult.setSysUserId(currentUserId);
                        resultService.save(testResult);
                    } else {
                        TestResult testResult = new TestResult();
                        testResult.setIsQuantifiable((Boolean) object.get("qualifiable"));
                        testResult.setIsNormal((Boolean) object.get("normal"));
                        testResult.setValue(dictionary.getId());
                        long order = (Long)object.get("order");
                        testResult.setSortOrder(String.valueOf(order * 10));
                        testResult.setTest(test);
                        testResult.setTestResultType("D");
                        testResult.setResultGroup("");
                        testResult.setSysUserId(currentUserId);
                        resultService.save(testResult);
                    }
                }
            }
            return true;
        } catch (ParseException pe) {

        }
        return false;
    }
}
