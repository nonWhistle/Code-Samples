package uk.co.dhl.smas.ui.view.conditionmonitoring.predictivemaintenance;

import lombok.Getter;
import org.springframework.context.ApplicationEventPublisher;
import uk.co.dhl.smas.backend.alert.Alert;
import uk.co.dhl.smas.backend.alert.AlertService;
import uk.co.dhl.smas.backend.condition.AnalogSensor;
import uk.co.dhl.smas.backend.condition.AnalogSensorService;
import uk.co.dhl.smas.backend.user.UserDetailsServiceImpl;
import uk.co.dhl.smas.events.PostAlertEvent;
import uk.co.dhl.smas.ui.view.PermissionChecker;
import uk.co.dhl.smas.ui.view.SmasMaths;
import uk.co.dhl.smas.ui.view.utils.FormattedZoneDateTimes;

import java.time.ZonedDateTime;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Logger;

import static uk.co.dhl.smas.backend.alert.Alert.TypeOfAlert.*;

public class PredictiveMaintenanceAlertGenerator implements PermissionChecker, FormattedZoneDateTimes {

    private static final Logger log = Logger.getLogger(PredictiveMaintenanceAlertGenerator.class.getSimpleName());

    private static final double FIVE_PERCENT = 0.05;
    private static final double THREE_PERCENT = 0.03;
    private static final double ONE_PERCENT = 0.01;
    private static final double TWENTY_PERCENT = 0.2;
    private static final double FOUR = 4.0;
    private static final double TWO = 2.0;
    private static final int COUNTER_RESET = 0;
    private final AlertService alertService;
    private final UserDetailsServiceImpl userService;
    private final AnalogSensorService analogSensorService;
    private final ApplicationEventPublisher ape;
    private AnalogSensor sensor;
    @Getter
    private TreeMap<ZonedDateTime, Double> sensorData;
    @Getter
    private SortedMap<ZonedDateTime, Double> monthToDate;
    @Getter
    private SortedMap<ZonedDateTime, Double> lastMonth;
    @Getter
    private SortedMap<ZonedDateTime, Double> rolling1Day;
    @Getter
    private double mtdAvg;
    @Getter
    private double lastMonthAvg;
    @Getter
    private double rolling1DayAvg;

    /**
     * Creates a calculator which holds all calculations for creating alerts under different conditions
     * see the DATAHONE drop box for an overview of all alarms created.
     * <a href="https://www.dropbox.com/scl/fi/pi68otkdnen2lu5eedjnt/MaintenanceView-Alarms.pptx?dl=0&rlkey=udoam8x46zhum6e1xh044uah8">https://www.dropbox.com/scl/fi/pi68otkdnen2lu5eedjnt/MaintenanceView-Alarms.pptx?dl=0&rlkey=udoam8x46zhum6e1xh044uah8</a>
     */
    public PredictiveMaintenanceAlertGenerator(AlertService alertService, UserDetailsServiceImpl userService,
                                               AnalogSensorService analogSensorService, ApplicationEventPublisher ape) {
        this.alertService = alertService;
        this.userService = userService;
        this.analogSensorService = analogSensorService;
        this.ape = ape;
        sensorData = new TreeMap<>();
    }

    /**
     * Sorts the data into relevant sub maps and calculates averages of each.
     * This data is then used to check for various alerts.
     *
     * @param sensorDataFromDb The data from the Scheduled Service to be checked.
     * @param analogSensor  The sensor this data is related to.
     */
    public void applyDataAndCreateAlarms(TreeMap<ZonedDateTime, Double> sensorDataFromDb,
                                         AnalogSensor analogSensor) {
        //Populate tha data map ready to sort.
        sensorData = sensorDataFromDb;

        //Assign the sensor for this generator to apply the calculations to.
        sensor = analogSensor;

        if (!sensorData.isEmpty()) {
            //Create sub maps to extract data from.
            monthToDate = sensorData.subMap(startOfMtd(), true, now(), true);
            lastMonth = sensorData.subMap(startOfLastMonth(), true, endOfLastMonth(), true);
            rolling1Day = sensorData.subMap(startOfTwentyFourHoursAgo(), true, lastCheckedForMaintenanceAlerts(), true);

            //Extract data from the sub maps.
            mtdAvg = monthToDate.values().stream().mapToDouble(Number::doubleValue).average().orElse(0);
            lastMonthAvg = lastMonth.values().stream().mapToDouble(Number::doubleValue).average().orElse(0);
            rolling1DayAvg = rolling1Day.values().stream().mapToDouble(Number::doubleValue).average().orElse(0);

            //Check all conditions with the extracted data.
            checkMean();
            checkPeaksAndTroughs();
            checkFrequencyOfPeaksAndTroughs();
            checkForAnomalies();
        } else {
            log.info("sensor data map was empty in Predictive Maintenance Calculator");
        }
    }

    /**
     * Check how much the MTD mean has deviated by compared to last month.
     */
    public void checkMean() {
        //If the Mean has deviated by >5% or <5% alert the user.
        if (isOutsideOfPercentage(mtdAvg, lastMonthAvg, FIVE_PERCENT)) {
            checkAndCreateAlert(Alert.TypeOfAlert.MEAN_FIVE_PERCENT, startOfMtd(), getCalculatedPercentageDifference(mtdAvg, lastMonthAvg));
        }
        //If the Mean has deviated by >3% or <3% alert the user.
        else if (isOutsideOfPercentage(mtdAvg, lastMonthAvg, THREE_PERCENT)) {
            checkAndCreateAlert(Alert.TypeOfAlert.MEAN_THREE_PERCENT, startOfMtd(), getCalculatedPercentageDifference(mtdAvg, lastMonthAvg));
        }
        //If the Mean has deviated by >1% or <1% alert the user.
        else if (isOutsideOfPercentage(mtdAvg, lastMonthAvg, ONE_PERCENT)) {
            checkAndCreateAlert(MEAN_ONE_PERCENT, startOfMtd(), getCalculatedPercentageDifference(mtdAvg, lastMonthAvg));
        }
    }

    /**
     * Checks if the value lies outside +/- the supplied percentage of the reference value.
     *
     * @param value      The value to check.
     * @param reference  The reference value to compare with the percentage.
     * @param percentage The percentage to
     * @return True if the value is outside the supplied percentage of the reference.
     */
    boolean isOutsideOfPercentage(double value, double reference, double percentage) {
        return value > (reference * (1.0 + percentage)) || value < (reference * (1.0 - percentage));
    }

    /**
     * Calculates the percentage difference of two values, this is used as the value to Post via RestAPI.
     *
     * @param value     MTD average value
     * @param reference Last month average value
     * @return          The percentage difference of the two
     */
    public double getCalculatedPercentageDifference(double value, double reference) {
        return SmasMaths.round(((value - reference) / reference) * 100, 1);
    }

    /**
     * Check for new peaks and new troughs
     */
    public void checkPeaksAndTroughs() {
        //Check for new peaks
        if (sensor.getTimeDateHigh().isAfter(lastCheckedForMaintenanceAlerts())) {
            checkAndCreateAlert(Alert.TypeOfAlert.NEW_PEAK, null, sensor.getHighestValue());
            analogSensorService.updateSensorPeakCounter(sensor, sensor.getPeakCounter() + 1);
        }

        //Check for new troughs
        if (sensor.getTimeDateLow().isAfter(lastCheckedForMaintenanceAlerts())) {
            checkAndCreateAlert(Alert.TypeOfAlert.NEW_TROUGH, null, sensor.getLowestValue());
            analogSensorService.updateSensorTroughCounter(sensor, sensor.getTroughCounter() + 1);
        }
    }

    /**
     * Checks how many peaks and troughs a sensor is getting.
     */
    public void checkFrequencyOfPeaksAndTroughs() {
        //Check for multiple peaks. If the peak counter is 2 then we should create a TWO_PEAK alert
        //but only if a FOUR_PEAK alert has been created since the last TWO_PEAK alert because the
        //counter will have been reset.
        if (sensor.getPeakCounter() >= FOUR) {
            checkAndCreateAlert(Alert.TypeOfAlert.FOUR_PEAKS, null, sensor.getPeakCounter());
            analogSensorService.updateSensorPeakCounter(sensor, COUNTER_RESET);
        } else if (sensor.getPeakCounter() >= TWO && getLastAlertForThisType(sensor, Alert.TypeOfAlert.FOUR_PEAKS)
                .isAfter(getLastAlertForThisType(sensor, Alert.TypeOfAlert.TWO_PEAKS))) {
            checkAndCreateAlert(Alert.TypeOfAlert.TWO_PEAKS, null, sensor.getPeakCounter());
        }

        //Check for multiple troughs. If the trough counter is 2 then we should create a TWO_TROUGHS alert
        //but only if a FOUR_TROUGHS alert has been created since the last TWO_TROUGHS alert because the
        //counter will have been reset.
        if (sensor.getTroughCounter() >= FOUR) {
            checkAndCreateAlert(Alert.TypeOfAlert.FOUR_TROUGHS, null, sensor.getTroughCounter());
            analogSensorService.updateSensorTroughCounter(sensor, COUNTER_RESET);
        } else if (sensor.getTroughCounter() >= TWO && getLastAlertForThisType(sensor, Alert.TypeOfAlert.FOUR_TROUGHS)
                .isAfter(getLastAlertForThisType(sensor, Alert.TypeOfAlert.TWO_TROUGHS))) {
            checkAndCreateAlert(Alert.TypeOfAlert.TWO_TROUGHS, null, sensor.getTroughCounter());
        }
    }

    /**
     * Checks for anomaly's which are values that deviate 20% from the daily average
     * minus the last hours running to avoid an inaccurate average value.
     */
    public void checkForAnomalies() {
        //Check for peak anomaly's
        if (sensor.getTimeDateHigh().isAfter(lastCheckedForMaintenanceAlerts()) &&
                sensor.getHighestValue() > rolling1DayAvg + (rolling1DayAvg * TWENTY_PERCENT)) {
            checkAndCreateAlert(Alert.TypeOfAlert.PEAK_ANOMALY, startOfTwentyFourHoursAgo(), sensor.getHighestValue());
        }

        //Check for trough anomaly's
        if (sensor.getTimeDateLow().isAfter(lastCheckedForMaintenanceAlerts()) &&
                sensor.getLowestValue() < rolling1DayAvg - (rolling1DayAvg * TWENTY_PERCENT)) {
            checkAndCreateAlert(Alert.TypeOfAlert.TROUGH_ANOMALY, startOfTwentyFourHoursAgo(), sensor.getLowestValue());
        }
    }

    /**
     * Checks if the alert has been created within the no alerts after param
     * and if the sensor is enabled for alerts. If both true then a new alert is created.
     *
     * @param alertType     The alert to be created
     * @param noAlertsAfter The period in which we dont want this alert to be created again.
     */
    public void checkAndCreateAlert(Alert.TypeOfAlert alertType, ZonedDateTime noAlertsAfter, double alertValue) {
        noAlertsAfter = noAlertsAfter == null? now() : noAlertsAfter;
        ZonedDateTime lastAlertForThisType = getLastAlertForThisType(sensor, alertType);

        if (lastAlertForThisType.isBefore(noAlertsAfter) && sensor.isAlertsEnabled() &&
                sensor.getMachine().isRunningStatusGreen()) {
            Alert alert = Alert.builder()
                    .opened(now())
                    .machine(sensor.getMachine())
                    .sensor(sensor)
                    .user(userService.defaultUserForAlerts())
                    .type(alertType.type)
                    .alertMetricValue(getFormattedValue(alertType, alertValue))
                    .build();
            alertService.saveAndEmail(alert);
            if(ape != null) {
                ape.publishEvent(new PostAlertEvent(this, alert));
            }
        }
    }

    /**
     * Formats the alertMetricValue field in {@link Alert}, If the alert is a mean alert then a percent is appended
     * otherwise the sensors unit of measure is appended.
     * @param alert      The alert type
     * @param alertValue The value alert metric value
     * @return           The value alert metric with the correct character(s) appended.
     */
    private String getFormattedValue(Alert.TypeOfAlert alert, double alertValue) {
        if(alert.equals(MEAN_ONE_PERCENT) || alert.equals(MEAN_THREE_PERCENT) || alert.equals(MEAN_FIVE_PERCENT)) {
            return alertValue + " %";
        } else {
            return alertValue + " " + sensor.getUnitOfMeasure();
        }
    }

    /**
     * Checks when the last alert was created for this type.
     * @param sensor    The sensor to check against
     * @param alertType The alert type. see Alerts.Type
     * @return          The Zoned date time when the alert was opened.
     */
    public ZonedDateTime getLastAlertForThisType(AnalogSensor sensor, Alert.TypeOfAlert alertType) {
        return alertService.getDateOpenedForMostRecentAlertForSensorAndType(sensor,
                alertType.type);
    }
}
