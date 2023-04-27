package aggregatorinterface;

import org.junit.jupiter.api.Test;
import uk.co.dhl.smas.ui.view.dashboard.supervisor.OEELayout;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AggregatorTest implements FormattedZoneDateTimes, Aggregator {

    @Test
    void reOrderForTimePeriod() {
        LinkedHashMap<Integer, Double> reOrdered;
        List<Integer> keys = new ArrayList<>();

        ZonedDateTime thirtyFirst = ZonedDateTime.of(2022, 8, 31, 12, 0, 0, 0, ZoneId.systemDefault());
        ZonedDateTime thirty = ZonedDateTime.of(2022, 8, 30, 12, 0, 0, 0, ZoneId.systemDefault());
        ZonedDateTime twentyEighth = ZonedDateTime.of(2022, 8, 28, 12, 0, 0, 0, ZoneId.systemDefault());
        ZonedDateTime tenth = ZonedDateTime.of(2022, 8, 10, 12, 0, 0, 0, ZoneId.systemDefault());
        ZonedDateTime first = ZonedDateTime.of(2022, 8, 1, 12, 0, 0, 0, ZoneId.systemDefault());

        //Check the order is returned correctly with different days.
        reOrdered = reOrderForTimePeriod(getTestDataForMonth(true, true), OEELayout.MONTH, thirtyFirst);
        reOrdered.forEach((k, v) -> keys.add(k));
        assertEquals(1, keys.get(0));
        assertEquals(31, keys.get(30));

        reOrdered = reOrderForTimePeriod(getTestDataForMonth(true, true), OEELayout.MONTH, thirty);
        keys.clear();
        reOrdered.forEach((k, v) -> keys.add(k));
        assertEquals(31, keys.get(0));
        assertEquals(30, keys.get(30));

        reOrdered = reOrderForTimePeriod(getTestDataForMonth(true, true), OEELayout.MONTH, twentyEighth);
        keys.clear();
        reOrdered.forEach((k, v) -> keys.add(k));
        assertEquals(29, keys.get(0));
        assertEquals(28, keys.get(30));

        reOrdered = reOrderForTimePeriod(getTestDataForMonth(true, true), OEELayout.MONTH, tenth);
        keys.clear();
        reOrdered.forEach((k, v) -> keys.add(k));
        assertEquals(11, keys.get(0));
        assertEquals(10, keys.get(30));

        reOrdered = reOrderForTimePeriod(getTestDataForMonth(true, true), OEELayout.MONTH, first);
        keys.clear();
        reOrdered.forEach((k, v) -> keys.add(k));
        assertEquals(2, keys.get(0));
        assertEquals(1, keys.get(30));

        //Check the order is returned correctly and does not throw an exception with different month lengths.
        reOrdered = reOrderForTimePeriod(getTestDataForMonth(true, false), OEELayout.MONTH, thirty);
        keys.clear();
        reOrdered.forEach((k, v) -> keys.add(k));
        assertEquals(1, keys.get(0));
        assertEquals(30, keys.get(29));

        reOrdered = reOrderForTimePeriod(getTestDataForMonth(false, false), OEELayout.MONTH, thirtyFirst);
        keys.clear();
        reOrdered.forEach((k, v) -> keys.add(k));
        assertEquals(1, keys.get(0));
        assertEquals(28, keys.get(27));
    }

    private TreeMap<Integer, Double> getTestDataForMonth(boolean thirty,
                                                         boolean thirtyOne) {
        TreeMap<Integer, Double> testData = new TreeMap<>();
        testData.put(1, 1.0);
        testData.put(2, 0.0);
        testData.put(3, 0.0);
        testData.put(4, 0.0);
        testData.put(5, 0.0);
        testData.put(6, 0.0);
        testData.put(7, 0.0);
        testData.put(8, 0.0);
        testData.put(9, 0.0);
        testData.put(10, 0.0);
        testData.put(11, 0.0);
        testData.put(12, 0.0);
        testData.put(13, 0.0);
        testData.put(14, 0.0);
        testData.put(15, 0.0);
        testData.put(16, 0.0);
        testData.put(17, 0.0);
        testData.put(18, 0.0);
        testData.put(19, 0.0);
        testData.put(20, 0.0);
        testData.put(21, 0.0);
        testData.put(22, 0.0);
        testData.put(23, 0.0);
        testData.put(24, 0.0);
        testData.put(25, 0.0);
        testData.put(26, 0.0);
        testData.put(27, 0.0);
        testData.put(28, 28.0);
        if(thirty || thirtyOne) {
            testData.put(29, 29.0);
            testData.put(30, 30.0);
        }
        if(thirtyOne) {
            testData.put(31, 31.0);
        }
        return testData;
    }
}