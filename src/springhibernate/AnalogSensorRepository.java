package springhibernate;

import entitys.AnalogSensor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uk.co.dhl.smas.backend.machine.Machine;
import uk.co.dhl.smas.backend.user.User;
import uk.co.dhl.smas.ui.view.dashboard.machineview.DashboardRAGColours;

import javax.transaction.Transactional;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface AnalogSensorRepository extends JpaRepository<AnalogSensor, Long> {

    List<AnalogSensor> findAllByMachine(Machine machine, Sort var1);

    List<AnalogSensor> findAllByMachineIsNotNull();

    Optional<AnalogSensor> findAllByName(String name);

    List<AnalogSensor> findAllByType(int type);

    List<AnalogSensor> findAllByMachineAndType(Machine machine, int type,
                                                          Sort var1);

    @Modifying(flushAutomatically = true)
    @Query("update AnalogSensor c set " +
            "c.current = :current, " +
            "c.avValue = :average, " +
            "c.stddev = :stdDev, " +
            "c.trend = :rankNumber, " +
            "c.upperControlLimit = :ucl, " +
            "c.lowerControlLimit = :lcl " +
            "WHERE c.id = :id")
    @Transactional
    void updateConditionSensor(@Param("id") Long id,
                               @Param("current") double current,
                               @Param("average") double average,
                               @Param("stdDev") double stdDev,
                               @Param("rankNumber") int rankNumber,
                               @Param("ucl") double ucl,
                               @Param("lcl") double lcl);

    @Modifying(flushAutomatically = true)
    @Query("update AnalogSensor c set " +
            "c.highestValue = :high, " +
            "c.timeDateHigh = :timedatehigh " +
            "WHERE c.id = :id")
    @Transactional
    void updateHigh(@Param("id") Long id,
                       @Param("high") double high,
                       @Param("timedatehigh") ZonedDateTime now);

    @Modifying(flushAutomatically = true)
    @Query("update AnalogSensor c set " +
            "c.lowestValue = :low, " +
            "c.timeDateLow = :timedatelow " +
            "WHERE c.id = :id")
    @Transactional
    void updateLow(@Param("id") Long id,
                    @Param("low") double low,
                    @Param("timedatelow") ZonedDateTime now);

    @Modifying(flushAutomatically = true)
    @Query("update AnalogSensor c set " +
            "c.blobColour = :rankNumber " +
            "WHERE c.id = :id")
    @Transactional
    void updateConditionSensorBlobColour(@Param("id") Long id,
                                         @Param("rankNumber") DashboardRAGColours rankNumber);

    @Modifying(flushAutomatically = true)
    @Query("update AnalogSensor c set " +
            "c.lastReset = :lastreset, " +
            "c.timeDateLow = :lastreset, " +
            "c.timeDateHigh = :lastreset, " +
            "c.lowestValue = :sensorCurrent, " +
            "c.highestValue = :sensorCurrent," +
            "c.lastResetBy = :user " +
            "WHERE c.id = :id")
    @Transactional
    void resetConditionSensor(@Param("id") Long id,
                              @Param("lastreset") ZonedDateTime now,
                              @Param("sensorCurrent") double sensorCurrent,
                              @Param("user")User user);

    @Modifying(flushAutomatically = true)
    @Query("update AnalogSensor c set " +
            "c.predictiveMaintenance = :pm " +
            "WHERE c.id = :id")
    @Transactional
    void updatePm(@Param("id") Long id,
                  @Param("pm") DashboardRAGColours predictiveMaintenance);

    List<AnalogSensor> findAllByMachineIsNull();

    @Modifying(flushAutomatically = true)
    @Query("update AnalogSensor c set " +
            "c.peakCounter = :counter " +
            "WHERE c.id = :id")
    @Transactional
    void updateSensorPeakCounter(@Param("id") Long id, @Param("counter") int counter);

    @Modifying(flushAutomatically = true)
    @Query("update AnalogSensor c set " +
            "c.troughCounter = :counter " +
            "WHERE c.id = :id")
    @Transactional
    void updateSensorTroughCounter(@Param("id") Long id, @Param("counter") int counter);

    @Modifying(flushAutomatically = true)
    @Query("update AnalogSensor c set " +
            "c.customColumn = :customValue " +
            "WHERE c.id = :id")
    @Transactional
    void updateCustomColumn(@Param("id") Long id, @Param("customValue") String customValue);
}

