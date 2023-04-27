package uk.co.dhl.smas.ui.view.conditionmonitoring.predictivemaintenance;

import java.time.ZonedDateTime;
import java.util.TreeMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.co.dhl.smas.backend.alert.Alert;
import uk.co.dhl.smas.backend.alert.AlertService;
import uk.co.dhl.smas.backend.condition.AnalogSensor;
import uk.co.dhl.smas.backend.condition.AnalogSensorService;
import uk.co.dhl.smas.backend.machine.Machine;
import uk.co.dhl.smas.backend.user.UserDetailsServiceImpl;
import uk.co.dhl.smas.ui.view.utils.FormattedZoneDateTimes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.util.ReflectionTestUtils.getField;
import static org.springframework.test.util.ReflectionTestUtils.setField;

@ExtendWith(MockitoExtension.class)
class PredictiveMaintenanceAlertGeneratorTest implements FormattedZoneDateTimes {

    private final ZonedDateTime startOfToday = startOfToday();
    private final ZonedDateTime startOfLastMonth = startOfLastMonth();
    private final ZonedDateTime endOfLastMonth = endOfLastMonth();
    private final ZonedDateTime startOfMtd = startOfMtd();
    private AnalogSensor analogSensor;
    @Mock
    private AlertService alertService;
    @Mock
    private UserDetailsServiceImpl userService;
    @Mock
    private AnalogSensorService analogSensorService;
    @InjectMocks
    private PredictiveMaintenanceAlertGenerator pmag;
    private PredictiveMaintenanceAlertGenerator spy;

    @BeforeEach
    void setUp() {
        Machine machine = Machine.builder()
                .secondsSinceLastRun(0)
                .secondsDownRed(60)
                .secondsDownAmber(120)
                .build();
        analogSensor = AnalogSensor.builder()
                .lastReset(startOfYesterday())
                .peakCounter(0)
                .troughCounter(0)
                .highestValue(100)
                .lowestValue(-1)
                .timeDateHigh(startOfYesterday())
                .timeDateLow(startOfYesterday())
                .machine(machine)
                .build();
        spy = Mockito.spy(pmag);
    }

    @Test
    void testIsOutsideOfPercentage() {
        // Check constants.
        assertEquals(0.01, (double) getField(pmag, "ONE_PERCENT"));
        assertEquals(0.03, (double) getField(pmag, "THREE_PERCENT"));
        assertEquals(0.05, (double) getField(pmag, "FIVE_PERCENT"));

        double lastMonthAvg = 11.0;
        double percentage = 0.05;

        // Test method.
        assertTrue(pmag.isOutsideOfPercentage(10, lastMonthAvg, percentage));
        assertFalse(pmag.isOutsideOfPercentage(10.6, lastMonthAvg, percentage));
        assertFalse(pmag.isOutsideOfPercentage(11.5, lastMonthAvg, percentage));
        assertTrue(pmag.isOutsideOfPercentage(12, lastMonthAvg, percentage));
    }

    @Test
    void testTheDataIsCorrectlySorted() {
        doReturn(ZonedDateTime.now().minusMonths(2L)).when(spy)
                .getLastAlertForThisType(any(), any());
        //Test the averages have been calculated correctly for each period.
        spy.applyDataAndCreateAlarms(getTestDataMtdAvgs(), analogSensor);
        assertEquals(1.5, spy.getMtdAvg());
        spy.applyDataAndCreateAlarms(getTestDataLastMonthAvgs(), analogSensor);
        assertEquals(1.5, spy.getLastMonthAvg());
        spy.applyDataAndCreateAlarms(getTestDataRolling1DayAvg(), analogSensor);
        assertEquals(1.5, spy.getRolling1DayAvg());

        spy.applyDataAndCreateAlarms(getGreaterThanFivePercentDeviationTestData(), analogSensor);
        //Test the dates have been correctly sorted into last month
        assertEquals(startOfLastMonth, spy.getLastMonth().firstKey());
        assertEquals(endOfLastMonth, spy.getLastMonth().lastKey());

        //Test the dates have been correctly sorted into this month
        assertEquals(startOfMtd, spy.getMonthToDate().firstKey());
        assertEquals(startOfToday.plusHours(3L), spy.getMonthToDate().lastKey());

        //Test the dates have been correctly sorted for the last 1 day.
        assertTrue(spy.getRolling1Day().firstKey().isAfter(startOfYesterday().minusMinutes(1L)));
        assertEquals(startOfToday.plusHours(3L), spy.getRolling1Day().lastKey());
    }

    @Test
    void testMean() {
        //MEAN
        //Check a five percent alert is created for MTD mean 5% GREATER than last month mean
        doReturn(ZonedDateTime.now().minusMonths(2L)).when(spy)
                .getLastAlertForThisType(any(), any());
        spy.applyDataAndCreateAlarms(getGreaterThanFivePercentDeviationTestData(), analogSensor);
        verify(spy, times(1)).checkAndCreateAlert(Alert.TypeOfAlert.MEAN_FIVE_PERCENT, startOfMtd, 6);
        reset(spy);

        //Check a five percent alert is created for MTD mean 5% LESS than last month mean
        doReturn(ZonedDateTime.now().minusMonths(2L)).when(spy)
                .getLastAlertForThisType(any(), any());
        spy.applyDataAndCreateAlarms(getLessThanFivePercentDeviationTestData(), analogSensor);
        verify(spy, times(1)).checkAndCreateAlert(Alert.TypeOfAlert.MEAN_FIVE_PERCENT, startOfMtd, -6);
        reset(spy);

        //Check a three percent alert is created for MTD mean 3% GREATER than last month mean
        doReturn(ZonedDateTime.now().minusMonths(2L)).when(spy)
                .getLastAlertForThisType(any(), any());
        spy.applyDataAndCreateAlarms(getGreaterThanThreePercentDeviationTestData(), analogSensor);
        verify(spy, times(1)).checkAndCreateAlert(Alert.TypeOfAlert.MEAN_THREE_PERCENT, startOfMtd, 5);
        reset(spy);

        //Check a three percent alert is created for MTD mean 3% LESS than last month mean
        doReturn(ZonedDateTime.now().minusMonths(2L)).when(spy)
                .getLastAlertForThisType(any(), any());
        spy.applyDataAndCreateAlarms(getLessThanThreePercentDeviationTestData(), analogSensor);
        verify(spy, times(1)).checkAndCreateAlert(Alert.TypeOfAlert.MEAN_THREE_PERCENT, startOfMtd, -5);
        reset(spy);

        //Check a one percent alert is created for MTD mean 1% GREATER than last month mean
        doReturn(ZonedDateTime.now().minusMonths(2L)).when(spy)
                .getLastAlertForThisType(any(), any());
        spy.applyDataAndCreateAlarms(getGreaterThanOnePercentDeviationTestData(), analogSensor);
        verify(spy, times(1)).checkAndCreateAlert(Alert.TypeOfAlert.MEAN_ONE_PERCENT, startOfMtd, 2);
        reset(spy);

        //Check a one percent alert is created for MTD mean 1% LESS than last month mean
        doReturn(ZonedDateTime.now().minusMonths(2L)).when(spy)
                .getLastAlertForThisType(any(), any());
        spy.applyDataAndCreateAlarms(getLessThanOnePercentDeviationTestData(), analogSensor);
        verify(spy, times(1)).checkAndCreateAlert(Alert.TypeOfAlert.MEAN_ONE_PERCENT, startOfMtd, -2);
        reset(spy);
    }

    @Test
    void testPeakAndTroughs() {
        //PEAKS & TROUGHS
        //Check a new peak alert is created when a new peak has been detected today.
        analogSensor.setTimeDateHigh(now().minusMinutes(14).minusSeconds(59));
        analogSensor.setTimeDateLow(startOfYesterday());
        doReturn(ZonedDateTime.now().minusMonths(2L)).when(spy)
                .getLastAlertForThisType(any(), any());
        spy.applyDataAndCreateAlarms(getStandardTestData(), analogSensor);
        verify(spy, times(1)).checkAndCreateAlert(Alert.TypeOfAlert.NEW_PEAK, null, 100);
        reset(spy);

        //Check a new Trough alert is created when a new trough has been detected today.
        analogSensor.setTimeDateHigh(startOfYesterday());
        analogSensor.setTimeDateLow(now().minusMinutes(14).minusSeconds(59));
        doReturn(ZonedDateTime.now().minusMonths(2L)).when(spy)
                .getLastAlertForThisType(any(), any());
        spy.applyDataAndCreateAlarms(getStandardTestData(), analogSensor);
        verify(spy, times(1)).checkAndCreateAlert(Alert.TypeOfAlert.NEW_TROUGH, null, -1);
        reset(spy);
    }

    @Test
    void testFrequency() {
        //PEAKS AND TROUGHS FREQUENCY
        //Check a new four peaks alert is created when four peaks have been detected.
        analogSensor.setTimeDateLow(startOfYesterday());
        when(alertService.getDateOpenedForMostRecentAlertForSensorAndType(analogSensor,
                Alert.TypeOfAlert.FOUR_PEAKS.type)).thenReturn(ZonedDateTime.now().minusMonths(2L));
        analogSensor.setPeakCounter(4);
        spy.applyDataAndCreateAlarms(getStandardTestData(), analogSensor);
        verify(spy, times(1)).checkAndCreateAlert(Alert.TypeOfAlert.FOUR_PEAKS, null, 4);
        reset(spy);

        //Check a new two peak alert is created when two peaks have been detected.
        when(alertService.getDateOpenedForMostRecentAlertForSensorAndType(analogSensor,
                Alert.TypeOfAlert.TWO_PEAKS.type)).thenReturn(ZonedDateTime.now().minusMonths(2L));
        when(alertService.getDateOpenedForMostRecentAlertForSensorAndType(analogSensor,
                Alert.TypeOfAlert.FOUR_PEAKS.type)).thenReturn(ZonedDateTime.now());
        analogSensor.setPeakCounter(2);
        spy.applyDataAndCreateAlarms(getStandardTestData(), analogSensor);
        verify(spy, times(1)).checkAndCreateAlert(Alert.TypeOfAlert.TWO_PEAKS, null, 2);
        reset(spy);
        //Check a new two troughs alert is NOT created when two troughs have been detected and the latest four peak alert
        //is before the latest two peak alert.
        when(alertService.getDateOpenedForMostRecentAlertForSensorAndType(analogSensor,
                Alert.TypeOfAlert.TWO_PEAKS.type)).thenReturn(ZonedDateTime.now());
        when(alertService.getDateOpenedForMostRecentAlertForSensorAndType(analogSensor,
                Alert.TypeOfAlert.FOUR_PEAKS.type)).thenReturn(ZonedDateTime.now().minusMonths(2L));
        verify(spy, times(0)).checkAndCreateAlert(Alert.TypeOfAlert.TWO_PEAKS, null, 2);
        reset(spy);

        //Check a new four troughs alert is created when four troughs have been detected.
        analogSensor.setTimeDateLow(startOfYesterday());
        when(alertService.getDateOpenedForMostRecentAlertForSensorAndType(analogSensor,
                Alert.TypeOfAlert.FOUR_TROUGHS.type)).thenReturn(ZonedDateTime.now().minusMonths(2L));
        analogSensor.setTroughCounter(4);
        spy.applyDataAndCreateAlarms(getStandardTestData(), analogSensor);
        verify(spy, times(1)).checkAndCreateAlert(Alert.TypeOfAlert.FOUR_TROUGHS, null, 4);
        reset(spy);

        //Check a new two troughs alert is created when two troughs have been detected.
        when(alertService.getDateOpenedForMostRecentAlertForSensorAndType(analogSensor,
                Alert.TypeOfAlert.TWO_TROUGHS.type)).thenReturn(ZonedDateTime.now().minusMonths(2L));
        when(alertService.getDateOpenedForMostRecentAlertForSensorAndType(analogSensor,
                Alert.TypeOfAlert.FOUR_TROUGHS.type)).thenReturn(ZonedDateTime.now());
        analogSensor.setTroughCounter(2);
        spy.applyDataAndCreateAlarms(getStandardTestData(), analogSensor);
        verify(spy, times(1)).checkAndCreateAlert(Alert.TypeOfAlert.TWO_TROUGHS, null, 2);
        reset(spy);
        //Check a new two troughs alert is NOT created when two troughs have been detected and the latest four peak alert
        //is before the latest two peak alert.
        doReturn(ZonedDateTime.now().minusMonths(2L)).when(spy)
                .getLastAlertForThisType(analogSensor, Alert.TypeOfAlert.TWO_TROUGHS);
        verify(spy, times(0)).checkAndCreateAlert(Alert.TypeOfAlert.TWO_TROUGHS, null, 2);
        reset(spy);
    }

    @Test
    void testAnomalies() {
        //ANOMALIES
        //Check a peak anomaly is created when one is detected.
        analogSensor.setTimeDateHigh(now());
        analogSensor.setHighestValue(1.81);
        doReturn(ZonedDateTime.now().minusMonths(2L)).when(spy)
                .getLastAlertForThisType(any(), any());
        spy.applyDataAndCreateAlarms(getTestDataRolling1DayAvg(), analogSensor);
        verify(spy, times(1)).checkAndCreateAlert(Alert.TypeOfAlert.PEAK_ANOMALY, startOfTwentyFourHoursAgo(), 1.81);
        reset(spy);

        //Check a trough anomaly is created when one is detected.
        analogSensor.setTimeDateHigh(startOfYesterday());
        analogSensor.setTimeDateLow(now());
        analogSensor.setLowestValue(1.19);
        doReturn(ZonedDateTime.now().minusMonths(2L)).when(spy)
                .getLastAlertForThisType(any(), any());
        spy.applyDataAndCreateAlarms(getTestDataRolling1DayAvg(), analogSensor);
        verify(spy, times(1)).checkAndCreateAlert(Alert.TypeOfAlert.TROUGH_ANOMALY, startOfTwentyFourHoursAgo(), 1.19);
        reset(spy);
    }

    @Test
    void testGetCalculatedPercentageDifference() {
        assertEquals(20 ,spy.getCalculatedPercentageDifference(120, 100));
        assertEquals(6 ,spy.getCalculatedPercentageDifference(1.06, 1), 0.1);
        assertEquals(5 ,spy.getCalculatedPercentageDifference(1.05, 1), 0.1);
        assertEquals(2 ,spy.getCalculatedPercentageDifference(1.02, 1), 0.1);

        assertEquals(-20 ,spy.getCalculatedPercentageDifference(80, 100));
        assertEquals(-6 ,spy.getCalculatedPercentageDifference(0.94, 1), 0.1);
        assertEquals(-5 ,spy.getCalculatedPercentageDifference(0.95, 1), 0.1);
        assertEquals(-2 ,spy.getCalculatedPercentageDifference(0.98, 1), 0.1);
    }

    private TreeMap<ZonedDateTime, Double> getTestDataMtdAvgs() {
        TreeMap<ZonedDateTime, Double> testData = new TreeMap<>();
        testData.put(startOfToday.plusMinutes(1L), 1.0);
        testData.put(startOfToday.plusMinutes(2L), 1.0);
        testData.put(startOfToday.plusMinutes(3L), 1.0);
        testData.put(startOfToday.plusMinutes(4L), 1.0);
        testData.put(startOfToday.plusMinutes(5L), 1.0);
        testData.put(startOfMtd, 2.0);
        testData.put(startOfMtd.plusHours(1L), 2.0);
        testData.put(startOfMtd.plusHours(2L), 2.0);
        testData.put(startOfMtd.plusHours(3L), 2.0);
        testData.put(startOfMtd.plusHours(4L), 2.0);
        return testData;
    }

    private TreeMap<ZonedDateTime, Double> getTestDataLastMonthAvgs() {
        TreeMap<ZonedDateTime, Double> testData = new TreeMap<>();
        testData.put(endOfLastMonth, 1.0);
        testData.put(endOfLastMonth.minusMinutes(2L), 1.0);
        testData.put(endOfLastMonth.minusMinutes(3L), 1.0);
        testData.put(endOfLastMonth.minusMinutes(4L), 1.0);
        testData.put(endOfLastMonth.minusMinutes(5L), 1.0);
        testData.put(startOfLastMonth, 2.0);
        testData.put(startOfLastMonth.plusMinutes(1L), 2.0);
        testData.put(startOfLastMonth.plusMinutes(2L), 2.0);
        testData.put(startOfLastMonth.plusMinutes(3L), 2.0);
        testData.put(startOfLastMonth.plusMinutes(4L), 2.0);
        return testData;
    }

    private TreeMap<ZonedDateTime, Double> getTestDataRolling1DayAvg() {
        TreeMap<ZonedDateTime, Double> testData = new TreeMap<>();
        testData.put(startOfTwentyFourHoursAgo().plusSeconds(1L), 1.0);

        testData.put(startOfTwentyFourHoursAgo().plusMinutes(1L), 1.0);
        testData.put(startOfTwentyFourHoursAgo().plusMinutes(2L), 1.0);
        testData.put(startOfTwentyFourHoursAgo().plusMinutes(3L), 1.0);
        testData.put(startOfTwentyFourHoursAgo().plusMinutes(4L), 1.0);
        testData.put(startOfTwentyFourHoursAgo().plusMinutes(5L), 2.0);
        testData.put(startOfTwentyFourHoursAgo().plusMinutes(6L), 2.0);
        testData.put(startOfTwentyFourHoursAgo().plusMinutes(7L), 2.0);
        testData.put(startOfTwentyFourHoursAgo().plusMinutes(8L), 2.0);

        testData.put(lastCheckedForMaintenanceAlerts().minusSeconds(1), 2.0);
        return testData;
    }

    private TreeMap<ZonedDateTime, Double> getGreaterThanFivePercentDeviationTestData() {
        TreeMap<ZonedDateTime, Double> testData = new TreeMap<>();

        testData.put(startOfToday.plusHours(2L), 1.06);
        testData.put(startOfToday.plusHours(3L), 1.06);
        testData.put(startOfToday, 1.06);
        testData.put(startOfToday.plusHours(1L), 1.06);
        testData.put(startOfMtd, 1.06);

        testData.put(startOfLastMonth, 1.0);
        testData.put(startOfLastMonth.plusDays(1L), 1.0);
        testData.put(startOfLastMonth.plusDays(3L), 1.0);
        testData.put(endOfLastMonth.minusDays(1), 1.0);
        testData.put(endOfLastMonth, 1.0);

        return testData;
    }

    private TreeMap<ZonedDateTime, Double> getLessThanFivePercentDeviationTestData() {
        TreeMap<ZonedDateTime, Double> testData = new TreeMap<>();

        testData.put(startOfToday.plusHours(2L), 0.94);
        testData.put(startOfToday.plusHours(3L), 0.94);
        testData.put(startOfToday, 0.94);
        testData.put(startOfToday.plusHours(1L), 0.94);
        testData.put(startOfMtd, 0.94);

        testData.put(startOfLastMonth, 1.0);
        testData.put(startOfLastMonth.plusDays(1L), 1.0);
        testData.put(startOfLastMonth.plusMonths(1L).minusMinutes(1L), 1.0);
        testData.put(startOfLastMonth.plusDays(2L), 1.0);
        testData.put(startOfLastMonth.plusDays(3L), 1.0);

        return testData;
    }

    private TreeMap<ZonedDateTime, Double> getGreaterThanThreePercentDeviationTestData() {
        TreeMap<ZonedDateTime, Double> testData = new TreeMap<>();

        testData.put(startOfToday.plusHours(2L), 1.05);
        testData.put(startOfToday.plusHours(3L), 1.05);
        testData.put(startOfToday, 1.05);
        testData.put(startOfToday.plusHours(1L), 1.05);
        testData.put(startOfMtd, 1.05);

        testData.put(startOfLastMonth.plusDays(1L), 1.0);
        testData.put(startOfLastMonth.plusDays(2L), 1.0);
        testData.put(startOfLastMonth, 1.0);
        testData.put(startOfLastMonth.plusDays(3L), 1.0);
        testData.put(startOfLastMonth.plusMonths(1L).minusMinutes(1L), 1.0);

        return testData;
    }

    private TreeMap<ZonedDateTime, Double> getLessThanThreePercentDeviationTestData() {
        TreeMap<ZonedDateTime, Double> testData = new TreeMap<>();

        testData.put(startOfToday.plusHours(2L), 0.95);
        testData.put(startOfToday.plusHours(3L), 0.95);
        testData.put(startOfToday, 0.95);
        testData.put(startOfToday.plusHours(1L), 0.95);
        testData.put(startOfMtd, 0.95);

        testData.put(startOfLastMonth.plusDays(1L), 1.0);
        testData.put(startOfLastMonth.plusDays(2L), 1.0);
        testData.put(startOfLastMonth, 1.0);
        testData.put(startOfLastMonth.plusDays(3L), 1.0);
        testData.put(startOfLastMonth.plusMonths(1L).minusMinutes(1L), 1.0);

        return testData;
    }

    private TreeMap<ZonedDateTime, Double> getGreaterThanOnePercentDeviationTestData() {
        TreeMap<ZonedDateTime, Double> testData = new TreeMap<>();

        testData.put(startOfToday.plusHours(2L), 1.02);
        testData.put(startOfToday.plusHours(3L), 1.02);
        testData.put(startOfToday, 1.02);
        testData.put(startOfToday.plusHours(1L), 1.02);
        testData.put(startOfMtd, 1.02);

        testData.put(startOfLastMonth.plusDays(1L), 1.0);
        testData.put(startOfLastMonth.plusDays(2L), 1.0);
        testData.put(startOfLastMonth, 1.0);
        testData.put(startOfLastMonth.plusDays(3L), 1.0);
        testData.put(startOfLastMonth.plusMonths(1L).minusMinutes(1L), 1.0);

        return testData;
    }

    private TreeMap<ZonedDateTime, Double> getLessThanOnePercentDeviationTestData() {
        TreeMap<ZonedDateTime, Double> testData = new TreeMap<>();

        testData.put(startOfToday.plusHours(2L), 0.98);
        testData.put(startOfToday.plusHours(3L), 0.98);
        testData.put(startOfToday, 0.98);
        testData.put(startOfToday.plusHours(1L), 0.98);
        testData.put(startOfMtd, 0.98);

        testData.put(startOfLastMonth.plusDays(1L), 1.0);
        testData.put(startOfLastMonth.plusDays(2L), 1.0);
        testData.put(startOfLastMonth, 1.0);
        testData.put(startOfLastMonth.plusDays(3L), 1.0);
        testData.put(startOfLastMonth.plusMonths(1L).minusMinutes(1L), 1.0);

        return testData;
    }

    private TreeMap<ZonedDateTime, Double> getStandardTestData() {
        TreeMap<ZonedDateTime, Double> testData = new TreeMap<>();
        testData.put(startOfToday, 0.0);
        return testData;
    }
}