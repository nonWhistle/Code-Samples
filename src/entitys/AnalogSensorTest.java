package entitys;

import org.junit.Test;
import uk.co.dhl.smas.backend.alert.Alert;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AnalogSensorTest {

    @Test
    public void testIsCurrentOutOfLimits(){
        /*
        Check upper control limit
         */
        AnalogSensor underTest = AnalogSensor.builder().upperControlLimit(100).build();

        underTest.setCurrent(100);
        assertTrue(underTest.isCurrentOutOfLimits());

        underTest.setCurrent(99.99);
        assertFalse(underTest.isCurrentOutOfLimits());

        underTest.setCurrent(100.01);
        assertTrue(underTest.isCurrentOutOfLimits());

        /*
        Check lower control limit
         */
        underTest.setLowerControlLimit(10);

        underTest.setCurrent(10);
        assertTrue(underTest.isCurrentOutOfLimits());

        underTest.setCurrent(9.99);
        assertTrue(underTest.isCurrentOutOfLimits());

        underTest.setCurrent(10.01);
        assertFalse(underTest.isCurrentOutOfLimits());
    }

    @Test
    public void testIsSensorInRedState(){
        //Test high conditions
        AnalogSensor analogSensor = new AnalogSensor();
        analogSensor.setHighRedSetPoint(100);

        analogSensor.setCurrent(100);
        assertTrue(analogSensor.isSensorInRedState());

        analogSensor.setCurrent(99.99);
        assertFalse(analogSensor.isSensorInRedState());

        analogSensor.setCurrent(100.01);
        assertTrue(analogSensor.isSensorInRedState());

        // Test low conditions

        analogSensor.setLowRedSetPoint(10);

        analogSensor.setCurrent(10);
        assertTrue(analogSensor.isSensorInRedState());

        analogSensor.setCurrent(10.01);
        assertFalse(analogSensor.isSensorInRedState());

        analogSensor.setCurrent(9.99);
        assertTrue(analogSensor.isSensorInRedState());
    }

    @Test
    public void testIsSensorInAmberState(){
        /*
        Check high conditions
         */
        AnalogSensor analogSensor = new AnalogSensor();
        analogSensor.setDisplay_name("TEST");
        analogSensor.setCurrent(80);

        analogSensor.setHighAmberSetPoint(79);
        assertTrue(analogSensor.isSensorInAmberState());

        analogSensor.setHighAmberSetPoint(80);
        assertTrue(analogSensor.isSensorInAmberState());

        analogSensor.setHighAmberSetPoint(81);
        assertFalse(analogSensor.isSensorInAmberState());

        analogSensor.setHighAmberSetPoint(79.01);
        assertTrue(analogSensor.isSensorInAmberState());

        analogSensor.setHighAmberSetPoint(80.01);
        assertFalse(analogSensor.isSensorInAmberState());
        /*
        Check Low Conditions
         */
        analogSensor.setCurrent(20);
        analogSensor.setLowAmberSetPoint(19);
        assertFalse(analogSensor.isSensorInAmberState());

        analogSensor.setLowAmberSetPoint(20);
        assertTrue(analogSensor.isSensorInAmberState());

        analogSensor.setLowAmberSetPoint(21);
        assertTrue(analogSensor.isSensorInAmberState());

        analogSensor.setLowAmberSetPoint(19.01);
        assertFalse(analogSensor.isSensorInAmberState());

        analogSensor.setLowAmberSetPoint(21.01);
        assertTrue(analogSensor.isSensorInAmberState());
    }

    @Test
    public void testGetOpenAlerts() {
        AnalogSensor analogSensor = AnalogSensor.builder().alerts(getAlerts()).build();
        assertEquals(5, analogSensor.getAlerts().size());
        assertEquals(4, analogSensor.getOpenAlerts().size());
    }

    @Test
    public void testHasDetectedAlertType() {
        AnalogSensor analogSensor = AnalogSensor.builder().alerts(getAlerts()).build();
        assertEquals(5, analogSensor.getAlerts().size());
        assertTrue(analogSensor.hasDetectedAlertType(100));
    }

    List<Alert> getAlerts() {
        Alert a1 = Alert.builder().type(100).closed(null).build();
        Alert a2 = Alert.builder().type(101).closed(null).build();
        Alert a3 = Alert.builder().type(102).closed(null).build();
        Alert a4 = Alert.builder().type(103).closed(null).build();
        Alert a5 = Alert.builder().type(104).closed(ZonedDateTime.now()).build();
        return Arrays.asList(a1, a2, a3, a4, a5);
    }
}