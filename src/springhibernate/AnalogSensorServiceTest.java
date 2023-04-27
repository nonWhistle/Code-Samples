package springhibernate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.co.dhl.smas.backend.machine.Machine;
import uk.co.dhl.smas.ui.view.dashboard.machineview.DashboardRAGColours;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalogSensorServiceTest {

    @Mock
    AnalogSensorRepository analogSensorRepository;
    AnalogSensorService underTest;
    AnalogSensor analogSensor;
    AnalogSensorService spy;
    Machine runningMachine = Machine.builder()
            .includeInSupervisorView(true)
            .secondsSinceLastRun(0)
            .secondsDownRed(60)
            .secondsDownAmber(50)
            .build();
    Machine notRunningMachine = Machine.builder()
            .includeInSupervisorView(true)
            .secondsSinceLastRun(61)
            .secondsDownRed(60)
            .secondsDownAmber(50)
            .build();
    Machine notInSupervisorView = Machine.builder()
            .includeInSupervisorView(false)
            .build();
    AnalogSensor one = AnalogSensor.builder().machine(runningMachine)
            .predictiveMaintenance(DashboardRAGColours.RED).build();
    AnalogSensor two = AnalogSensor.builder().machine(runningMachine)
            .predictiveMaintenance(DashboardRAGColours.ORANGE).build();
    AnalogSensor three = AnalogSensor.builder().machine(runningMachine)
            .predictiveMaintenance(DashboardRAGColours.AMBER).build();
    AnalogSensor four = AnalogSensor.builder().machine(runningMachine)
            .predictiveMaintenance(DashboardRAGColours.GREEN).build();
    AnalogSensor five = AnalogSensor.builder().machine(runningMachine)
            .predictiveMaintenance(DashboardRAGColours.GRAY).build();
    AnalogSensor six = AnalogSensor.builder().machine(runningMachine)
            .predictiveMaintenance(DashboardRAGColours.GREEN).build();
    AnalogSensor seven = AnalogSensor.builder().machine(notInSupervisorView)
            .predictiveMaintenance(DashboardRAGColours.AMBER).build();
    AnalogSensor eight = AnalogSensor.builder()
            .predictiveMaintenance(DashboardRAGColours.AMBER).build();

    @BeforeEach
    void setUp(){
        underTest = new AnalogSensorService(analogSensorRepository);
        analogSensor = new AnalogSensor();
        spy = Mockito.spy(underTest);
        underTest.save(analogSensor);

        one.setBlobColour(DashboardRAGColours.GREEN);
        one.setDisplay_name("TEST");
        two.setBlobColour(DashboardRAGColours.AMBER);
        two.setDisplay_name("TEST");
        three.setBlobColour(DashboardRAGColours.RED);
        three.setDisplay_name("TEST");
        four.setBlobColour(DashboardRAGColours.GREEN);
        four.setDisplay_name("TEST");
        five.setBlobColour(DashboardRAGColours.GREEN);
        five.setDisplay_name("TEST");
        six.setBlobColour(DashboardRAGColours.GREEN);
        six.setDisplay_name("TEST");
    }

    @Test
    void testFindAllIncludedInSupervisorView() {
        //Check the correct sensor is returned when a machine is not included in supervisor view
        doReturn(Arrays.asList(six, seven)).when(spy).findAll();
        assertEquals(six, spy.findAllIncludedInSupervisorView().get(0));

        //Check both sensors are returned when the machines are in supervisor view
        doReturn(Arrays.asList(six, one)).when(spy).findAll();
        assertEquals(2, spy.findAllIncludedInSupervisorView().size());

        //Check an empty list is handled correctly
        doReturn(Collections.emptyList()).when(spy).findAll();
        assertEquals(Collections.emptyList(), spy.findAllIncludedInSupervisorView());

        //Check a null machine is handled correctly
        doReturn(List.of(eight)).when(spy).findAll();
        assertEquals(Collections.emptyList(), spy.findAllIncludedInSupervisorView());
    }

    @Test
    void testgetAncillaryColourCode(){
        // Check worst of three is correctly returned.
        doReturn(Arrays.asList(one, two, three)).when(spy).findAllByMachine(null);
        assertEquals(DashboardRAGColours.RED, spy.getAncillaryColourCode());

        // Check when all three are Green that Green is returned and not default RED or GRAY.
        doReturn(Arrays.asList(four, five, six)).when(spy).findAllByMachine(null);
        assertEquals(DashboardRAGColours.GREEN, spy.getAncillaryColourCode());

        // Check empty list is handled correctly.
        doReturn(Collections.emptyList()).when(spy).findAllByMachine(null);
        assertEquals(DashboardRAGColours.RED, spy.getAncillaryColourCode());
    }

    @Test
    void testGetOverallWorstConditionSensor(){
        // Check red returned.
        when(spy.findAllByType(0)).thenReturn(List.of(one, two, three));
        assertEquals(DashboardRAGColours.RED, spy.getOverallMostSevereBlobColourForType(0));

        // Check amber is returned.
        three.setBlobColour(DashboardRAGColours.GREEN);
        when(spy.findAllByType(0)).thenReturn(List.of(one, two, three));
        assertEquals(DashboardRAGColours.AMBER, spy.getOverallMostSevereBlobColourForType(0));

        // Check when all three are Green that Green is returned and not default RED or GRAY.
        when(spy.findAllByType(0)).thenReturn(List.of(four, five, six));
        assertEquals(DashboardRAGColours.GREEN, spy.getOverallMostSevereBlobColourForType(0));

        // Check empty list is handled correctly, should return default rag RED.
        when(spy.findAllByType(0)).thenReturn(Collections.emptyList());
        assertEquals(DashboardRAGColours.GRAY, spy.getOverallMostSevereBlobColourForType(0));

        //The traffic light used to respond only to machines running, see SMAS-308.
        //When only one machine isnt running
        one.setBlobColour(DashboardRAGColours.RED);
        one.setMachine(notRunningMachine);
        when(spy.findAllByType(0)).thenReturn(List.of(one, two, three));
        assertEquals(DashboardRAGColours.RED, spy.getOverallMostSevereBlobColourForType(0));

        // When all machines arnt running
        two.setMachine(notRunningMachine);
        three.setMachine(notRunningMachine);
        when(spy.findAllByType(0)).thenReturn(List.of(one, two, three));
        assertEquals(DashboardRAGColours.RED, spy.getOverallMostSevereBlobColourForType(0));
    }
    @Test
    void testGetWorstSensorForMachine(){

        // Check worst of three is correctly returned.
        doReturn(Arrays.asList(one, two, three)).when(spy).findAllByMachineAndType(runningMachine, 0);
        assertEquals(DashboardRAGColours.RED, spy.getMostSevereBlobColourForType(runningMachine, 0));

        // Check when all three are Green that Green is returned and not default RED or GRAY.
        doReturn(Arrays.asList(four, five, six)).when(spy).findAllByMachineAndType(runningMachine, 0);
        assertEquals(DashboardRAGColours.GREEN, spy.getMostSevereBlobColourForType(runningMachine, 0));

        // Check empty list is handled correctly.
        doReturn(Collections.emptyList()).when(spy).findAllByMachineAndType(runningMachine, 0);
        assertEquals(DashboardRAGColours.GRAY, spy.getMostSevereBlobColourForType(runningMachine, 0));

        //The machine blob used to respond only to machines running, see SMAS-308.
        // Check if the machine is not running then gray is returned
        assertEquals(DashboardRAGColours.GRAY, spy.getMostSevereBlobColourForType(notRunningMachine, 0));
    }

    @Test
    void testGetTheSensorWithTheWorstAlertType() {
        // Check worst of three is correctly returned. Red being the worst.
        doReturn(Arrays.asList(one, two, three)).when(spy).findAllIncludedInSupervisorView();
        assertEquals(DashboardRAGColours.RED, spy.getTheSensorWithTheWorstAlertType());

        // Check worst of three is correctly returned. When Orange is the worst then Amber should be returned.
        doReturn(Arrays.asList(four, two, three)).when(spy).findAllIncludedInSupervisorView();
        assertEquals(DashboardRAGColours.AMBER, spy.getTheSensorWithTheWorstAlertType());

        // Check worst of three is correctly returned. Amber being the worst.
        doReturn(Arrays.asList(four, six, three)).when(spy).findAllIncludedInSupervisorView();
        assertEquals(DashboardRAGColours.AMBER, spy.getTheSensorWithTheWorstAlertType());

        // Check worst of three is correctly returned. All green.
        doReturn(Arrays.asList(four, six)).when(spy).findAllIncludedInSupervisorView();
        assertEquals(DashboardRAGColours.GREEN, spy.getTheSensorWithTheWorstAlertType());

        // Check empty list is handled correctly. No sensors found.
        doReturn(Collections.emptyList()).when(spy).findAllIncludedInSupervisorView();
        assertEquals(DashboardRAGColours.GRAY, spy.getTheSensorWithTheWorstAlertType());
    }

    @Test
    void testGetTheSensorForAMachineWithTheWorstAlertType() {
        // Check worst of three is correctly returned. Red being the worst.
        doReturn(Arrays.asList(one, two, three)).when(spy).findAllByMachine(runningMachine);
        assertEquals(DashboardRAGColours.RED, spy.getTheSensorForAMachineWithTheWorstAlertType(runningMachine));

        // Check worst of three is correctly returned. When Orange is the worst then Amber should be returned.
        doReturn(Arrays.asList(four, two, three)).when(spy).findAllByMachine(runningMachine);
        assertEquals(DashboardRAGColours.AMBER, spy.getTheSensorForAMachineWithTheWorstAlertType(runningMachine));

        // Check worst of three is correctly returned. Amber being the worst.
        doReturn(Arrays.asList(four, six, three)).when(spy).findAllByMachine(runningMachine);
        assertEquals(DashboardRAGColours.AMBER, spy.getTheSensorForAMachineWithTheWorstAlertType(runningMachine));

        // Check worst of three is correctly returned. All green.
        doReturn(Arrays.asList(four, six)).when(spy).findAllByMachine(runningMachine);
        assertEquals(DashboardRAGColours.GREEN, spy.getTheSensorForAMachineWithTheWorstAlertType(runningMachine));

        // Check empty list is handled correctly. No sensors found.
        doReturn(Collections.emptyList()).when(spy).findAllByMachine(runningMachine);
        assertEquals(DashboardRAGColours.GRAY, spy.getTheSensorForAMachineWithTheWorstAlertType(runningMachine));

        // Check if the machine is not running then gray is returned.
        assertEquals(DashboardRAGColours.GRAY, spy.getTheSensorForAMachineWithTheWorstAlertType(notRunningMachine));
    }
}