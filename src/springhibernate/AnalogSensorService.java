package springhibernate;

import entitys.AnalogSensor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import uk.co.dhl.smas.backend.machine.Machine;
import uk.co.dhl.smas.ui.view.PermissionChecker;
import uk.co.dhl.smas.ui.view.dashboard.machineview.DashboardRAGColours;
import uk.co.dhl.smas.ui.view.utils.FormattedZoneDateTimes;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class AnalogSensorService implements FormattedZoneDateTimes, PermissionChecker {
    private static final Logger log = Logger.getLogger(AnalogSensorService.class.getName());

    private final AnalogSensorRepository analogSensorRepository;

    public AnalogSensorService(AnalogSensorRepository analogSensorRepository) {
        this.analogSensorRepository = analogSensorRepository;
    }

    public void save(AnalogSensor analogSensor) {
        analogSensorRepository.save(analogSensor);
    }

    public List<AnalogSensor> findAllByMachine(Machine machine) {
        return analogSensorRepository.findAllByMachine(machine, Sort.by("name"));
    }

    public Optional<AnalogSensor> findById(Long id) {
        return analogSensorRepository.findById(id);
    }

    public List<AnalogSensor> findAll() {
        return analogSensorRepository.findAll(Sort.by("name"));
    }

    /**
     * Gets all the sensors fitted to the machines that are included in supervisor view,
     * any blank sensors are filtered.
     * @return All real sensors that are on a machine that is included in supervisor view.
     */
    public List<AnalogSensor> findAllIncludedInSupervisorView() {
        return findAll().stream()
                .filter(sensor -> sensor.getMachine() != null)
                .filter(AnalogSensor::isMachineIncludedInSupervisorView)
                .collect(Collectors.toList());
    }

    public AnalogSensor findAllByName(String name) {
        return analogSensorRepository.findAllByName(name).isPresent() ?
                analogSensorRepository.findAllByName(name).get() : null;
    }

    /**
     * Updates the passed Condition sensor with all the passed values.
     *
     * @param sensor      The sensor to update.
     * @param current     The raw data value from the sensor, either 4 - 20mA or 0 - 10 VDC
     * @param average     The average value from the past 90 days.
     * @param stdDev      The standard deviation calculated from the past 90 days.
     *                    see scheduledConditionSensorService.getConvertedStdDevFromRawData
     * @param trendColour 2 for Red, 0 for Green.
     * @param ucl         upperControlLimit = (3 * stdDev) + average
     * @param lcl         lowerControlLimit = (3 * stdDev) - average
     */
    public void updateConditionSensor(AnalogSensor sensor, double current, double average,
                                      double stdDev, int trendColour, double ucl, double lcl) {
        analogSensorRepository.updateConditionSensor(sensor.getId(), current, average,
                stdDev, trendColour, ucl, lcl);
    }

    public void updateSensorPredictiveMaintenance(AnalogSensor sensor) {
        analogSensorRepository.updatePm(sensor.getId(), sensor.getPredictiveMaintenance());
    }

    public void updateHigh(AnalogSensor analogSensor, double high) {
        analogSensorRepository.updateHigh(analogSensor.getId(), high, now());
    }

    public void updateLow(AnalogSensor analogSensor, double low) {
        analogSensorRepository.updateLow(analogSensor.getId(), low, now());
    }

    /**
     * Resets *high* and *low* to -999.0 which turns the cell text transparent see conditionMonitoringGrid.
     * Resets *timeDateHigh* and *timeDateLow* to null
     * updates the last reset column with a formatted ZonedDateTime.now
     * Called when the reset button/ yes button in ConditonForm is clicked.
     *
     * @param sensor The sensor to reset
     */
    public void resetConditionSensor(AnalogSensor sensor) {
        analogSensorRepository.resetConditionSensor(sensor.getId(), now(), sensor.getCurrent(), getLoggedInUser());
    }

    /**
     * Updates the *x* and *y* of the AnalogSensor that has the passed name.
     * Offsets have been introduced because it was found that when moving the elements without
     * an offset they were not staying in the desired place
     * //TODO find a solution that doesnt require the offsets.
     *
     * @param name The *name* of the ConditionSensor.
     * @param newX The X coordinate for the svg.
     * @param newY The Y coordinate for the svg.
     */
    public void updateAnalogSensorXY(String name, double newX, double newY) {
        AnalogSensor sensor = findAllByName(name);
        sensor.setX(Sensor.getOffSet(newX, true));
        sensor.setY(Sensor.getOffSet(newY, false));
        save(sensor);
    }

    /**
     * Updates *blobColour* of the passed ConditionSensor to turn the cell yellow or green.
     *
     * @param analogSensor The sensor to update
     * @param ragColour    The rank number of the DashBoardRagColour either 0 for Green or 1 for Yellow
     */
    public void updateConditionSensorBlobColour(AnalogSensor analogSensor, DashboardRAGColours ragColour) {
        analogSensorRepository.updateConditionSensorBlobColour(analogSensor.getId(), ragColour);
    }

    public DashboardRAGColours getAncillaryColourCode() {
        return getDashboardRAGColours(findAllByMachine(null), DashboardRAGColours.RED);
    }

    /**
     * Gets the overall most sever CURRENT COLUMN from all analog sensors of the passed type.
     * @return Rag colour
     */
    public DashboardRAGColours getOverallMostSevereBlobColourForType(int type) {
        return getDashboardRAGColours(findAllByType(type), DashboardRAGColours.GRAY);
    }

    /**
     * Returns the most severe CURRENT COLUMN of any matching type sensors fitted to a machine.
     * @param machine The machine
     * @param type The type of sensor see Sensor class
     * @return Rag colour
     */
    public DashboardRAGColours getMostSevereBlobColourForType(Machine machine, int type) {
        List<AnalogSensor> sensors = findAllByMachineAndType(machine, type);
        log.info("This many sensors " + sensors.size() + " for machine: " + machine.getName());
        return getDashboardRAGColours(sensors, DashboardRAGColours.GRAY);
    }

    /**
     * Check all passed sensors to see which one has the worst blob colour.
     *
     * @param sensors The sensors to check
     * @param defaultRag The default if no sensors are present
     * @return rag colour
     */
    DashboardRAGColours getDashboardRAGColours(List<AnalogSensor> sensors, DashboardRAGColours defaultRag) {
        Optional<AnalogSensor> worst = sensors.stream().max(Comparator.comparing(s -> s.getBlobColour().rank));
        return worst.isPresent() ? worst.get().getBlobColour() : defaultRag;
    }

    /**
     * Gets the sensor with the worst alert type, which passes the colour to the top level traffic light.
     * If the the worst alert type is of type Orange then an amber is returned.
     *
     * @return The colour the traffic light should be.
     */
    public DashboardRAGColours getTheSensorWithTheWorstAlertType() {
        Optional<AnalogSensor> worst = findAllIncludedInSupervisorView().stream()
                .min(Comparator.comparing(AnalogSensor::getPredictiveMaintenance));
        if (worst.isPresent()) {
            if (worst.get().getPredictiveMaintenance() == DashboardRAGColours.ORANGE) {
                return DashboardRAGColours.AMBER;
            } else {
                return worst.get().getPredictiveMaintenance();
            }
        }
        return DashboardRAGColours.GRAY;
    }

    /**
     * Gets the sensor on a machine with the worst alert type, whcih is passed into the blob colour in the drill down.
     * If the worst alert type is an Orange then amber is returned.
     *
     * @param machine The machine to get sensors for.
     * @return The colour the drill down blow colour should be.
     */
    public DashboardRAGColours getTheSensorForAMachineWithTheWorstAlertType(Machine machine) {
        Optional<AnalogSensor> worst = findAllByMachine(machine).stream().min(Comparator.comparing(AnalogSensor::getPredictiveMaintenance));
        if (worst.isPresent()) {
            if (worst.get().getPredictiveMaintenance() == DashboardRAGColours.ORANGE) {
                return DashboardRAGColours.AMBER;
            } else {
                return worst.get().getPredictiveMaintenance();
            }
        }
        return DashboardRAGColours.GRAY;
    }

    public List<AnalogSensor> findAllByType(int type) {
        return analogSensorRepository.findAllByType(type);
    }

    public List<AnalogSensor> findAllByMachineAndType(Machine machine, int type) {
        return analogSensorRepository
                .findAllByMachineAndType(machine, type, Sort.by("name"));
    }

    public List<AnalogSensor> findAllForAncillaryEquipment() {
        return analogSensorRepository.findAllByMachineIsNull();
    }

    public List<AnalogSensor> getSensorsOnRunningMachines() {
        return analogSensorRepository.findAllByMachineIsNotNull().stream()
                .filter(sensor -> sensor.getMachine().isRunningStatusGreen())
                .collect(Collectors.toList());
    }

    public void updateSensorPeakCounter(AnalogSensor sensor, int counter) {
        analogSensorRepository.updateSensorPeakCounter(sensor.getId(), counter);
    }

    public void updateSensorTroughCounter(AnalogSensor sensor, int counter) {
        analogSensorRepository.updateSensorTroughCounter(sensor.getId(), counter);
    }

    public void updateCustomColumn(AnalogSensor analogSensor, String customValue) {
        analogSensorRepository.updateCustomColumn(analogSensor.getId(), customValue);
    }
}