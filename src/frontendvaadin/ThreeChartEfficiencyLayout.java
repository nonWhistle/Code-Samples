package frontendvaadin;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import uk.co.dhl.smas.backend.event.EventDescriptionService;
import uk.co.dhl.smas.backend.event.EventService;
import uk.co.dhl.smas.backend.machine.Machine;
import uk.co.dhl.smas.backend.machine.MachineService;
import uk.co.dhl.smas.backend.order.Order;
import uk.co.dhl.smas.backend.order.OrderService;
import uk.co.dhl.smas.backend.perfomance.PerformanceMetric;
import uk.co.dhl.smas.backend.perfomance.PerformanceMetricService;
import uk.co.dhl.smas.backend.shift.ShiftService;
import uk.co.dhl.smas.ui.view.dashboard.CenteredRowItemLayout;
import uk.co.dhl.smas.ui.view.dashboard.supervisor.OrderedMachineNameList;
import uk.co.dhl.smas.ui.view.utils.FormattedZoneDateTimes;
import uk.co.dhl.smas.ui.view.utils.OrderAggregator;
import uk.co.dhl.smas.ui.view.utils.PerformanceMetricAggregator;
import uk.co.dhl.smas.ui.view.utils.ZonedDateTimeFromTimePeriod;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static uk.co.dhl.smas.backend.perfomance.PerformanceMetric.THROUGHPUT;
import static uk.co.dhl.smas.backend.perfomance.PerformanceMetric.UPTIME;
import static uk.co.dhl.smas.ui.view.dashboard.supervisor.OEELayout.*;
import static uk.co.dhl.smas.ui.view.dashboard.supervisor.configuration.SupervisorViewConfiguration.SHIFT_SELECTABLE;

public class ThreeChartEfficiencyLayout extends CenteredRowItemLayout implements OrderedMachineNameList,
        PerformanceMetricAggregator, OrderAggregator, FormattedZoneDateTimes, ZonedDateTimeFromTimePeriod {

    private static final Logger log = Logger.getLogger(ThreeChartEfficiencyLayout.class.getSimpleName());
    private final ComboBox dropDownMachines = new ComboBox();
    private final ComboBox<String> timeComboBox = new ComboBox<>();
    private final OrderService orderService;
    private final MachineService machineService;
    private final ShiftService shiftService;
    private final EventService eventService;
    private final EventDescriptionService eventDescriptionService;
    private final PerformanceMetricService performanceMetricService;
    private final CurrentUptimeGenerator cug;
    private AggregatedDataChartTile setUpEfficiency;
    private AggregatedDataChartTile productionEfficiency;
    private AggregatedDataChartTile utilisation;

    public ThreeChartEfficiencyLayout(String title, String stylingClassName, boolean homeButton,
                                      OrderService orderService, MachineService machineService,
                                      ShiftService shiftService, EventService eventService, EventDescriptionService eventDescriptionService, PerformanceMetricService performanceMetricService) {
        super(title, stylingClassName, homeButton);
        super.setPadding(false);
        this.orderService = orderService;
        this.machineService = machineService;
        this.shiftService = shiftService;
        this.eventService = eventService;
        this.eventDescriptionService = eventDescriptionService;
        this.performanceMetricService = performanceMetricService;
        cug = new CurrentUptimeGenerator();
        addClassName("thin-white-border-right");
        configureHeader();
        configureCharts();
    }

    public void configureHeader() {

        HashMap<String, Long> machineData = getMachineDataFromService(machineService.findAllByIncludeInSupervisorViewIsTrue(), true);

        ArrayList<String> machineNameList = getOrderedMachineNameList(machineData.keySet(), true);
        dropDownMachines.setItems(machineNameList);
        dropDownMachines.setLabel("Select Machine");
        dropDownMachines.setWidth("29%");
        dropDownMachines.setValue(machineNameList.get(0));
        dropDownMachines.addValueChangeListener(machineName -> updateLayout());

        String[] timeValues = SHIFT_SELECTABLE ?
                new String[]{SHIFT, DAY, WEEK, MONTH, YEAR} :
                new String[]{DAY, WEEK, MONTH, YEAR};
        timeComboBox.setItems(timeValues);
        timeComboBox.setLabel("Select Time Period");
        timeComboBox.setValue(MONTH);
        timeComboBox.setAllowCustomValue(false);
        timeComboBox.setWidth("29%");
        timeComboBox.addValueChangeListener(timePeriod -> updateLayout());
        timeComboBox.setItemLabelGenerator(this::getLabel);

        super.titleLayout.remove(titleLabel);
        super.titleLayout.setWidthFull();
        super.titleLayout.setPadding(false);
        super.titleLayout.add(dropDownMachines, timeComboBox);
    }

    /**
     * For use with the item label generator on the time value combobox.
     *
     * @param s String to check.
     * @return The string unless it's "Day" in which case it's switched to "24 Hours".
     */
    private String getLabel(String s) {
        return s.equals(DAY) ? "24 Hours" : s;
    }


    /**
     * Creates the three charts displayed in column 1, and adds appropriate styling.
     */
    public void configureCharts() {
        setUpEfficiency = new AggregatedDataChartTile("Set-up Efficiency", true);
        setUpEfficiency.setWidthFull();
        setUpEfficiency.setHeight(ONE_THIRD);
        productionEfficiency = new AggregatedDataChartTile("Production Efficiency", true);
        productionEfficiency.setWidthFull();
        productionEfficiency.setHeight(ONE_THIRD);
        utilisation = new AggregatedDataChartTile("Utilisation", true);
        utilisation.setWidthFull();
        utilisation.setHeight(ONE_THIRD);

        VerticalLayout noSpacingContainer = new VerticalLayout(setUpEfficiency, productionEfficiency, utilisation);
        noSpacingContainer.setSizeFull();
        noSpacingContainer.setSpacing(false);
        noSpacingContainer.setPadding(false);
        add(noSpacingContainer);
    }

    /**
     * Updates the three charts in column 1 with the most current data.
     */
    public void updateLayout() {
        String timeComboBoxValue = timeComboBox.getValue();
        //Get the machine selected from the drop down, if "All Machines" then return null.
        Machine machine = machineService.findAllByName(dropDownMachines.getValue().toString()).orElse(null);
        //Get a list of the selected machine or machines if 'All Machines' is selected.
        List<Machine> machines = machine == null ? machineService.findAllByIncludeInSupervisorViewIsTrue() : List.of(machine);
        //Get the start zoned date time from the time period selected
        ZonedDateTime from = getRollingDateForTimePeriod(timeComboBoxValue, machines, shiftService);
        //The finish zoned date time is always to the current time.
        ZonedDateTime to = now();


        //SET-UP EFFICIENCY
        /*
        Get all the orders for the selected machine in the drop down box, if "All Machines"
        is selected then machine = null and so get all the orders between from and to but after the 1st June 2022.
         */
        List<Order> orders = machine == null ? orderService.findAllBetweenFromAndTo(from, to) :
                orderService.findByMachineBetweenFromAndTo(machine, from, to);
        //Todo: June 2023 remove this.
        ZonedDateTime firstOfJune2022 = ZonedDateTime.of(2022, 6, 1, 0, 0, 0, 0, TimeZone.getDefault().toZoneId());
        orders = orders.stream().filter(order -> order.getStartDateTime().isAfter(firstOfJune2022)).collect(Collectors.toList());

        //Filter the orders to remove all the Pms below the threshold for set up eff.
        List<Order> ordersForSetUp = orders.stream().filter(order -> order.getOrderSetupEfficiency() >= NumbersLayoutV2.MIN_THRESHOLD &&
                        order.getOrderSetupEfficiency() <= NumbersLayoutV2.MAX_THRESHOLD)
                .collect(Collectors.toList());

        //Aggregate for the given time period.
        TreeMap<Integer, List<Order>> aggregatedSetUpChartData = aggregateOrders(ordersForSetUp, timeComboBoxValue, machineService);
        //Consolidate the values to just one value.
        TreeMap<Integer, Double> consolidatedSetUpChartData = consolidateEfficiencyValues(aggregatedSetUpChartData, true);
        //Reorder the map to display in the chart.
        LinkedHashMap<Integer, Double> setUpChartData = reOrderForTimePeriod(consolidatedSetUpChartData, timeComboBoxValue, now());
        //Format chart data for the time period
        LinkedHashMap<String, Double> formattedSetUpChartData = formatForTimePeriod(setUpChartData, timeComboBoxValue, machineService);
        //Update the chart with chart data and the average label
        setUpEfficiency.updateLayout(formattedSetUpChartData, consolidateToOneValue(ordersForSetUp, true));
        log.info("Successfully completed updating the set up chart");


        //PRODUCTION EFFICIENCY
        List<PerformanceMetric> prodPms = machine == null ? performanceMetricService.findByTypeBetweenFromAndTo(from, to, THROUGHPUT) :
                performanceMetricService.findByMachineAndTypeBetweenFromAndTo(machine, from, to, THROUGHPUT);

        //Update the chart
        productionEfficiency.updateLayout(getChartData(prodPms, THROUGHPUT),
                getConsolidatedValue(prodPms, THROUGHPUT, machines, timeComboBoxValue));
        log.info("Successfully completed updating the production eff chart");


        //UTILISATION
        List<PerformanceMetric> utilPms = machine == null ? performanceMetricService.findByTypeBetweenFromAndTo(from, to, UPTIME) :
                performanceMetricService.findByMachineAndTypeBetweenFromAndTo(machine, from, to, UPTIME);
        //Update the chart
        utilisation.updateLayout(getChartData(utilPms, UPTIME, machines),
                getConsolidatedValue(utilPms, UPTIME, machines, timeComboBoxValue));
        
        log.info("Successfully completed updating the utilisation chart");
    }

    /**
     * Consolidates the passed in PMS to get the average value for both UPTIME and THROUGHPUT.
     * <p>
     * Todo: *UTILISATION ONLY* This should have the ability to add the current uptime to individual machines before
     * calculating an average, but I think we need to refactor Shift entity first so it has a List<Machine>
     *
     * @param pms  The PMS to consolidate
     * @param type The type of PM. either Uptime type 2 or Throughput type 4.
     * @return A consolidated value of either uptime or throughput.
     */
    public double getConsolidatedValue(List<PerformanceMetric> pms, Integer type, List<Machine> machines, String timePeriod) {
        //Checks if any of the machines are on shift, if none are on shift then we dont want to consider the current uptime.
        boolean isAnyMachinesOnShift = machines.stream().anyMatch(Machine::isOnShift);

        //Considering the current uptime for the week, month, year is negligible and potently problematic.
        if (type.equals(UPTIME) && (timePeriod.equals(SHIFT) || timePeriod.equals(DAY))) {
            //The Pms will be empty if its the first hour of the shift and Shift is selected,
            // so we only want to show the average of the current hour and this does not need to be divided by two.
            if (pms.isEmpty()) {
                return cug.getCurrentUptimeForMachines(machines, now());
            } else if (isAnyMachinesOnShift) {
                //If were on shift then it is safe to presume that the current hour is on shift
                // and we can include it into the calculation for the average
                return consolidatePmsToOneValueWithCurrentHourRunMinutes(pms, cug.getCurrentHourRunMinutes(machines), now().getMinute());
            }
        }
        //If Throughput is passed in as the type OR UPTIME Week, Month, Year is selected OR no machines are on shift.
        return consolidatePmsToOneValue(pms, type);
    }

    /**
     * Overloaded to separate UPTIME and THROUGHPUT.
     *
     * @param pms  pms to consolidate
     * @param type the type of PM.
     * @return Consolidated Value.
     */
    public LinkedHashMap<String, Double> getChartData(List<PerformanceMetric> pms, Integer type) {
        return getChartData(pms, type, Collections.emptyList());
    }

    public LinkedHashMap<String, Double> getChartData(List<PerformanceMetric> pms, Integer type, List<Machine> machines) {
        String timeComboBoxValue = timeComboBox.getValue();
        //Aggregate for the given time period.
        TreeMap<Integer, List<PerformanceMetric>> aggregatedChartData = aggregateMetrics(pms, timeComboBoxValue, machineService);
        //Consolidate the values to just one value.
        TreeMap<Integer, Double> consolidatedChartData = consolidatePerformanceValues(aggregatedChartData, type);
        //If the type is UPTIME and the time period selected is Shift or Day then add the current hour data.
        if (type.equals(UPTIME)) {
            consolidatedChartData = cug.addCurrentToChartData(consolidatedChartData,
                    timeComboBoxValue, machines, now());
        }
        //Reorder the map to display in the chart.
        LinkedHashMap<Integer, Double> chartData = reOrderForTimePeriod(consolidatedChartData, timeComboBoxValue, now());
        //Format chart data for the time period and return the formatted data
        return formatForTimePeriod(chartData, timeComboBoxValue, machineService);

    }
}

