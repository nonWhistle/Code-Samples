package springasyncthreads;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uk.co.dhl.smas.backend.alert.Alert;
import uk.co.dhl.smas.backend.alert.AlertService;
import uk.co.dhl.smas.backend.condition.*;
import uk.co.dhl.smas.backend.order.OrderService;
import uk.co.dhl.smas.backend.user.UserDetailsServiceImpl;
import uk.co.dhl.smas.data.SensorMonitoringDataProcessorFactory;
import uk.co.dhl.smas.data.conditionmonitoring.dataprocessors.AbstractSensorMonitoringDataProcessor;
import uk.co.dhl.smas.ui.view.SmasMaths;
import uk.co.dhl.smas.ui.view.conditionmonitoring.predictivemaintenance.PredictiveMaintenanceAlertGenerator;
import uk.co.dhl.smas.ui.view.dashboard.machineview.DashboardRAGColours;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.lang.Double.NaN;
import static uk.co.dhl.smas.backend.condition.Sensor.*;
import static uk.co.dhl.smas.ui.view.dashboard.machineview.DashboardRAGColours.*;

@Service
@ConditionalOnProperty(prefix = "scheduled.condition.sensor", name = "enabled", havingValue = "true")
public class ScheduledSensorService {

    private final static Logger log = Logger.getLogger(ScheduledSensorService.class.getSimpleName());

    //Services
    private final AnalogSensorService analogSensorService;
    private final DigitalSensorService digitalSensorService;
    private final AlertService alertService;
    private final AnalogSensorDataEntryService analogSensorDataEntryService;
    private final OrderService orderService;

    //Tools
    private final PredictiveMaintenanceAlertGenerator pmag;

    public ScheduledSensorService(AnalogSensorService analogSensorService,
                                  DigitalSensorService digitalSensorService, AnalogSensorDataEntryService analogSensorDataEntryService,
                                  AlertService alertService, OrderService orderService, UserDetailsServiceImpl userService, ApplicationEventPublisher ape) {
        this.analogSensorService = analogSensorService;
        this.digitalSensorService = digitalSensorService;
        this.analogSensorDataEntryService = analogSensorDataEntryService;
        this.alertService = alertService;
        this.orderService = orderService;
        pmag = new PredictiveMaintenanceAlertGenerator(alertService, userService, analogSensorService, ape);
    }

    /**
     * Gets a list of all sensors that are included in Supervisor view, if all types is
     * True then digital sensors will also be included.
     *
     * @param allTypes True if digital sensors is required.
     * @return A list of sensors.
     */
    public List<Sensor> getSensors(boolean allTypes) {
        List<Sensor> sensors = new ArrayList<>(analogSensorService.findAllIncludedInSupervisorView());
        if (allTypes) {
            sensors.addAll(digitalSensorService.findAllIncludedInSupervisorView());
        }
        return sensors;
    }

    /**
     * Retrieves the correct sensor monitoring data processor for the client.
     *
     * @return A sensor monitoring data processor.
     */
    private AbstractSensorMonitoringDataProcessor getProcessor() {
        return SensorMonitoringDataProcessorFactory.getSensorMonitoringDataProcessor();
    }

    @Scheduled(fixedRate = 60000)
    @Async("asyncExecutor")
    public void updateSensorTables() {
        log.info("updateConditionSensorTable has started" + "\t" + "<<<--------------------------------<<<");
        getSensors(true).forEach(sensor -> {
            if (sensor instanceof AnalogSensor) {
                updateAnalogSensor((AnalogSensor) sensor);
            } else if (sensor instanceof DigitalSensor) {
                updateDigitalSensor((DigitalSensor) sensor);
            }
        });
    }

    /**
     * Checks all sensors every 15minutes for potential maintenance requirements
     * on the machines that they are monitoring.
     */
    @Scheduled(cron = "0 */15 * * * ?")
    @Async("asyncExecutor")
    public void checkForMaintenance() {
        ZonedDateTime startOfLastMonth = ZonedDateTime.now()
                .minusDays(ZonedDateTime.now().getDayOfMonth() - 1)
                .truncatedTo(ChronoUnit.DAYS)
                .minusMonths(1L);
        ZonedDateTime now = ZonedDateTime.now().truncatedTo(ChronoUnit.HOURS);
        if (!getSensors(false).isEmpty()) {
            getSensors(false).forEach(sensor -> {
                if (sensor instanceof AnalogSensor && sensor.getType() == MAINTENANCE_TYPE) {
                    pmag.applyDataAndCreateAlarms(getProcessor().getValuesAndDatesBetweenFromAndTo((AnalogSensor) sensor,
                            startOfLastMonth, now), (AnalogSensor) sensor);
                }
            });
        }
    }

    /**
     * Creates a new data entry of type 1 for Daily Average every day at 00:10AM,
     * uses the service to get the average of all type 2 hourly entries
     */
    @Scheduled(cron = "0 10 0 * * ?")
    @Async("asyncExecutor")
    public void createDailyAvgEntry() {
        if (!getSensors(false).isEmpty()) {
            getSensors(false).forEach(sensor -> {
                if (sensor instanceof AnalogSensor) {
                    AnalogSensorDataEntry entry = AnalogSensorDataEntry.builder()
                            .analogSensor((AnalogSensor) sensor)
                            .type(AnalogSensorDataEntry.DAILY)
                            .zonedDateTime(ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS).minusDays(1))
                            .averageValue(analogSensorDataEntryService.getDailyAverageForSensor((AnalogSensor) sensor))
                            .build();
                    analogSensorDataEntryService.save(entry);
                }
            });
        }
    }

    /**
     * Creates a new data entry of type 2 for hourly Average every hour at 00:05,
     * it collects the average value from all live readings in the past hour from the production DB
     */
    @Scheduled(cron = "0 5 * * * ?")
    @Async("asyncExecutor")
    public void createHourlyAvgEntry() {
        ZonedDateTime startOfThisHour = ZonedDateTime.now().truncatedTo(ChronoUnit.HOURS);
        ZonedDateTime startOfThePreviousHour = startOfThisHour.minusHours(1);

        if (!getSensors(false).isEmpty()) {
            getSensors(false).forEach(sensor -> {
                if (sensor instanceof AnalogSensor) {
                    AnalogSensorDataEntry entry = AnalogSensorDataEntry.builder()
                            .analogSensor((AnalogSensor) sensor)
                            .type(AnalogSensorDataEntry.HOURLY)
                            .zonedDateTime(ZonedDateTime.now().truncatedTo(ChronoUnit.HOURS).minusHours(1))
                            .averageValue(getProcessor().getSensorAverage((AnalogSensor) sensor,
                                    startOfThePreviousHour, startOfThisHour))
                            .build();
                    analogSensorDataEntryService.save(entry);
                }
            });
        }
    }

    /**
     * Updates any digital sensors blob colour and current value, if the DP fails to connect to the DB
     * then NaN will be returned.
     *
     * @param digitalSensor The sensor to update.
     */
    private void updateDigitalSensor(DigitalSensor digitalSensor) {
        double couldBeNaN = getProcessor().getSensorCurrent(digitalSensor);
        double current = Double.isNaN(couldBeNaN) ? -1 : couldBeNaN;

        DashboardRAGColours digitalSensorBlobColour = current >= digitalSensor.getGreenValue() ? GREEN : RED;
        digitalSensorService.updateDigitalSensor(digitalSensor, current, digitalSensorBlobColour);
    }

    /**
     * Updates *current* *average* *stdDev* *trend* *upperControlLimit* and *lowerControlLimit* of the passed
     * ConditionSensor. Retrieves all values from the database using the Data Processor, checks that the current
     * and average value do not equal null due to a failed connection attempt, and passes them to the
     * ConditionSensorService.
     *
     * @param analogSensor The sensor to update
     */
    public void updateAnalogSensor(AnalogSensor analogSensor) {

        if (analogSensor.getType().equals(IDENTIFICATION_TYPE)) {
            // The last UID reading for this sensor, If it is of type 3 (Identification)
            String uidValue = getProcessor().getLastUidReadingFromSensor(analogSensor);
            log.info("Updating ID type sensor: " + analogSensor.getDisplay_name() + " with value: " + uidValue);
            analogSensorService.updateCustomColumn(analogSensor, uidValue);
            checkLastUidValueWithOrderAncillaryValues(analogSensor);
        } else {
            ZonedDateTime now = ZonedDateTime.now();
            ZonedDateTime startOfThirtyDaysAgo = now.truncatedTo(ChronoUnit.DAYS).minusDays(30L);

            // A list of converted values from the past 30 days.
            TreeMap<ZonedDateTime, Double> last30Days = getProcessor()
                    .getValuesAndDatesBetweenFromAndTo(analogSensor, startOfThirtyDaysAgo, now);
            // The current value of the sensor regardless if the machine is running.
            double current = getProcessor().getSensorCurrent(analogSensor);
            // The average value of the sensor when the machine has been running for the past thirty days.
            double average = last30Days.values().stream().mapToDouble(Double::doubleValue).average().orElse(NaN);
            // The standard deviation from the last 30 days.
            double stdDev = SmasMaths.SD(new ArrayList<>(last30Days.values()));
            // The upper control which is plus 3 stdv from the mean.
            double ucl = analogSensor.getAvValue() + 3 * analogSensor.getStddev();
            // The lower control limit which is minus 3 stdv from the mean.
            double lcl = analogSensor.getAvValue() - 3 * analogSensor.getStddev();
            // The colour of the trend cell if the current is out of the ucl / lcl limits.
            int trendColour = analogSensor.isCurrentOutOfLimits() ? 2 : 0;

            // Update the highest or lowest value if it is detected.
            checkHighLowValues(analogSensor);

            // If the Data processor fails to get a value from the DB it will return NaN
            if (!Double.isNaN(current) && !Double.isNaN(average)) {
                analogSensorService.updateConditionSensor(analogSensor,
                        SmasMaths.round(current, 1),
                        SmasMaths.round(average, 1),
                        stdDev, trendColour, ucl, lcl);
            }
            updateCurrentColumnCellColours(analogSensor);
            updatePredictiveMaintenanceColumnCellColour(analogSensor);
        }
    }

    /**
     * Checks the last Uid reading from the sensor, this currently checks to ensure the value equals "1"
     * if it does then the actual vs expected matched, this check was done in the Database view.
     * In future this will likely evolve to use something stored on the order or machine
     *
     * @param analogSensor The analog sensor reading the Uid. Could be barcode scanner or RFID reader. Etc
     */
    private void checkLastUidValueWithOrderAncillaryValues(AnalogSensor analogSensor) {

        DashboardRAGColours doExpectedAndActualUidsMatch = RED;
        if (analogSensor.getCustomColumn().equals("1")) {
            doExpectedAndActualUidsMatch = GREEN;
        }

        //Todo: MTC were the first client to use this functionality and the sensor value is brought into our system
        // having already being checked for a match, if this is used again the comparison of the
        // actual UID vs the expected UID will likely be done in our system, and below is a template of how this may be achieved.
//        // Gets the in-progress order if one exists for the machine the passed sensor is attached too.
//        Order couldBeNullOrder = orderService.findInProgressOrder(analogSensor.getMachine()).orElse(null);
//        //Checks if the order is null, if it is then set the blob colour too amber to indicate there is no in-progress order.
//        if(couldBeNullOrder != null) {
//            for(String id : couldBeNullOrder.getAncillaryIds()) {
//                if (analogSensor.getCustomColumn().equals(id)) {
//                    doExpectedAndActualUidsMatch = GREEN;
//                    break;
//                }
//            }
//        } else {
//            doExpectedAndActualUidsMatch = AMBER;
//        }
        analogSensorService.updateConditionSensorBlobColour(analogSensor, doExpectedAndActualUidsMatch);
    }

    /**
     * Checks if the current value is greater than the highest ever value or the lowest ever value.
     *
     * @param analogSensor The sensor to check against.
     */
    private void checkHighLowValues(AnalogSensor analogSensor) {
        if (analogSensor.isCurrentGreaterThanHigh()) {
            analogSensorService.updateHigh(analogSensor, analogSensor.getCurrent());
        }
        if (analogSensor.isCurrentLessThanLow()) {
            analogSensorService.updateLow(analogSensor, analogSensor.getCurrent());
        }
    }

    /**
     * Checks the passed conditon sensor is not out of its set limits, if it is then the grid is
     * updated to reflect this.
     *
     * @param analogSensor The sensor to check against.
     */
    private void updateCurrentColumnCellColours(AnalogSensor analogSensor) {
        if (analogSensor.isSensorInRedState()) {
            analogSensorService.updateConditionSensorBlobColour
                    (analogSensor, RED);
        } else if (analogSensor.isSensorInAmberState()) {
            analogSensorService.updateConditionSensorBlobColour
                    (analogSensor, DashboardRAGColours.AMBER);
        } else {
            analogSensorService.updateConditionSensorBlobColour
                    (analogSensor, GREEN);
        }
    }

    private void updatePredictiveMaintenanceColumnCellColour(AnalogSensor sensor) {
        List<Alert> openAlerts =
                alertService.findAllOpenAlertsForSensor(sensor);
        List<Alert> redAlerts = openAlerts.stream()
                .filter(alert -> alert.getCategoryString().equals(Alert.RED_ALERT))
                .collect(Collectors.toList());

        List<Alert> orangeAlerts = openAlerts.stream()
                .filter(alert -> alert.getCategoryString().equals(Alert.ORANGE_ALERT))
                .collect(Collectors.toList());

        List<Alert> amberAlerts = openAlerts.stream()
                .filter(alert -> alert.getCategoryString().equals(Alert.AMBER_ALERT))
                .collect(Collectors.toList());

        if (!redAlerts.isEmpty()) {
            sensor.setPredictiveMaintenance(RED);
        } else if (!orangeAlerts.isEmpty()) {
            sensor.setPredictiveMaintenance(DashboardRAGColours.ORANGE);
        } else if (!amberAlerts.isEmpty()) {
            sensor.setPredictiveMaintenance(DashboardRAGColours.AMBER);
        } else {
            sensor.setPredictiveMaintenance(GREEN);
        }
        analogSensorService.updateSensorPredictiveMaintenance(sensor);
    }
}
