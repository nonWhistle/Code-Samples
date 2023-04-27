package entitys;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import lombok.*;
import uk.co.dhl.smas.backend.alert.Alert;
import uk.co.dhl.smas.backend.machine.Machine;
import uk.co.dhl.smas.backend.user.User;
import uk.co.dhl.smas.ui.view.dashboard.machineview.DashboardRAGColours;

import javax.persistence.*;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AnalogSensor extends Sensor {
    public final static double LOWEST_MILLIAMP = 4.0;
    public final static double HIGHEST_MILLIAMP = 20.0;

    private static final Logger log = Logger.getLogger(AnalogSensor.class.getSimpleName());

    /**
     * A List of all alerts this sensor has detected.
     */
    @Setter
    @OneToMany(fetch = FetchType.EAGER, mappedBy = "sensor", cascade = CascadeType.ALL)
    private List<Alert> alerts;

    /**
     * This has to be annotated as a pair with the Alert, otherwise a recursive error occurs when Posting via REST API
     * @return The list of Alerts this sensor has generated.
     */
    @JsonManagedReference
    public List<Alert> getAlerts() {
        return alerts;
    }

    /**
     * The average value from the past 30 days.
     */
    @Setter
    @Getter
    @Column(columnDefinition = "double precision default 0")
    private double avValue;

    /**
     * The highest value at which the Current column cell should turn yellow, this is set by the client in ConditionForm.
     */
    @Setter
    @Getter
    @Column(columnDefinition = "double precision default 80")
    private double highAmberSetPoint;

    /**
     * The maximum value *current* can go to before a red condition is triggered. Set by the client.
     */
    @Setter
    @Getter
    @Column(columnDefinition = "double precision default 100")
    private double highRedSetPoint;

    /**
     * The highest value a sensor can detect, for example a sensor with a range of 0-100 BAR
     * the highest actual value would equal 100.
     */
    @Column(columnDefinition = "double precision default 100")
    @Getter
    @Setter
    private double highestActualValue;

    /**
     * Stores the highest recorded value since last reset.
     */
    @Setter
    @Getter
    @Column(columnDefinition = "double precision default 0")
    private double highestValue;

    /**
     * When reset / yes are clicked in ConditonForm *lastReset* is timestamped, this is then
     * displayed back to the client to show the last time this sensor was reset.
     */
    @Getter
    @Setter
    @Column(columnDefinition = "timestamp with time zone")
    private ZonedDateTime lastReset;

    /**
     * Stores which user last reset this sensor
     */
    @ManyToOne
    @JoinColumn(name = "user_id")
    @Getter
    @Setter
    private User lastResetBy;

    /**
     * The lowest value at which the Current column cell should turn yellow, this is set by the client in ConditionForm.
     */
    @Getter
    @Setter
    @Column(columnDefinition = "double precision default 20")
    private double lowAmberSetPoint;

    /**
     * The lowest value *current* can go to before a red condition is triggered. Set by the client.
     */
    @Setter
    @Getter
    @Column(columnDefinition = "double precision default 0")
    private double LowRedSetPoint;

    /**
     * The lower control limit for the trend graph, equals ((3 * stdDev) - average)
     */
    @Setter
    @Getter
    @Column(columnDefinition = "double precision default 0")
    private double lowerControlLimit;

    /**
     * The lowest value a sensor can detect, for example a sensor with a range of 0-100 BAR
     * the pvLow would equal 0.
     */
    @Column(columnDefinition = "double precision default 0")
    @Getter
    @Setter
    private double lowestActualValue;

    /**
     * Stores the lowest recorded value since last reset.
     */
    @Setter
    @Getter
    @Column(columnDefinition = "double precision default 0")
    private double lowestValue;

    /**
     * The machine this sensor is fitted to.
     */
    @ManyToOne
    @JoinColumn(name = "machine_id")
    @Getter
    @Setter
    private Machine machine;

    /**
     * Determines if the sensor is detecting some maintenance is required.
     */
    @Getter
    @Setter
    @Column(columnDefinition = "integer default 4")
    private DashboardRAGColours predictiveMaintenance;

    /**
     * Counts the Number of peak this sensor has had.
     */
    @Getter
    @Setter
    @Column(columnDefinition = "integer default 0")
    private Integer peakCounter;

    /**
     * The standard deviation value based on the last 90 days. set in ScheduledConditionSensorService.
     */
    @Getter
    @Setter
    @Column(columnDefinition = "double precision default 0")
    private double stddev;

    /**
     * Counts the Number of troughs this sensor has had.
     */
    @Getter
    @Setter
    @Column(columnDefinition = "integer default 0")
    private Integer troughCounter;

    /**
     * Stores the date of the highest recorded value
     */
    @Setter
    @Getter
    @Column(columnDefinition = "timestamp with time zone")
    private ZonedDateTime timeDateHigh;

    /**
     * Stores the date of the lowest recorded value
     */
    @Setter
    @Getter
    @Column(columnDefinition = "timestamp with time zone")
    private ZonedDateTime timeDateLow;

    /**
     * 2 for red or 0 for green. see ConditionMonitoringGrid.
     * Todo: Decide if this feature is to be kept, the trend column was taken out of the app on the 5th Sept 22.
     */
    @Getter
    @Setter
    @Column(columnDefinition = "integer default 0")
    private Integer trend;

    /**
     * The unit of measure for this sensor.
     */
    @Getter
    @Setter
    private String unitOfMeasure;

    /**
     * The upper control limit for the trend graph, equals ((3 * stdDev) + average)
     */
    @Setter
    @Getter
    @Column(columnDefinition = "double precision default 0")
    private double upperControlLimit;


    public boolean isCurrentOutOfLimits() {
        return getCurrent() >= upperControlLimit || getCurrent() <= lowerControlLimit;
    }

    public boolean isSensorInRedState() {
        return getCurrent() >= highRedSetPoint || getCurrent() <= LowRedSetPoint;
    }

    public boolean isSensorInAmberState() {
        return getCurrent() >= highAmberSetPoint || getCurrent() <= lowAmberSetPoint;
    }

    public boolean isCurrentGreaterThanHigh() {
        return getCurrent() > highestValue;
    }

    public boolean isCurrentLessThanLow() {
        return getCurrent() < lowestValue;
    }

    public List<Alert> getOpenAlerts() {
        return alerts.stream()
                .filter(alert -> alert.getClosed() == null)
                .collect(Collectors.toList());
    }

    public boolean hasDetectedAlertType(int alertType) {
        List<Alert> alertTypes = getOpenAlerts().stream()
                .filter(alert -> alert.getType() == alertType)
                .collect(Collectors.toList());
        return !alertTypes.isEmpty();
    }

    public boolean isAlertsEnabled() {
        return lastReset.isBefore(ZonedDateTime.now().minusMinutes(30L));
    }

    public boolean isMachineIncludedInSupervisorView() {
        return machine.getIncludeInSupervisorView();
    }
}
