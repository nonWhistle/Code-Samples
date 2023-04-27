package aggregatorinterface;

import uk.co.dhl.smas.backend.machine.Machine;
import uk.co.dhl.smas.backend.machine.MachineService;

import java.time.DayOfWeek;
import java.time.Month;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.*;

import static uk.co.dhl.smas.ui.view.dashboard.supervisor.OEELayout.*;

public interface Aggregator {

    double DEFAULT = 0.0;

    /**
     * Takes a Map and returns a linked map with sorted values ready to fill a chart.
     *
     * @param sortedList The map of values to reorder
     * @param timePeriod The time period to order by.
     * @return A Linked HashMap of values in the correct order specified by the time period.
     */
    default LinkedHashMap<Integer, Double> reOrderForTimePeriod(TreeMap<Integer, Double> sortedList,
                                                                String timePeriod, ZonedDateTime now) {
        SortedMap<Integer, Double> firstHalf;
        SortedMap<Integer, Double> secondHalf;

        if (sortedList.isEmpty()) {
            System.out.println("reOrderForTimePeriod in the Aggregator interface was passed insufficient data");
            return new LinkedHashMap<>();
        }  else {
            switch (timePeriod) {
                case SHIFT:
                    firstHalf = sortedList;
                    secondHalf = new TreeMap<>();
                    break;
                case WEEK:
                    firstHalf = sortedList.subMap(now.getDayOfWeek().getValue(), false, sortedList.lastKey(), true);
                    secondHalf = sortedList.subMap(sortedList.firstKey(), true, now.getDayOfWeek().getValue(), true);
                    break;
                case MONTH:
                    if (now.getDayOfMonth() > sortedList.lastKey()) {
                        firstHalf = sortedList.subMap(sortedList.lastKey(), false, sortedList.lastKey(), true);
                    } else {
                        firstHalf = sortedList.subMap(now.getDayOfMonth(), false, sortedList.lastKey(), true);
                    }
                    secondHalf = sortedList.subMap(sortedList.firstKey(), true, now.getDayOfMonth(), true);
                    break;
                case YEAR:
                    firstHalf = sortedList.subMap(now.getMonthValue(), false, sortedList.lastKey(), true);
                    secondHalf = sortedList.subMap(sortedList.firstKey(), true, now.getMonthValue(), true);
                    break;
                default:
                    // DEFAULT is the same as passing in DAY.
                    firstHalf = sortedList.subMap(now.getHour(), false, sortedList.lastKey(), true);
                    secondHalf = sortedList.subMap(sortedList.firstKey(), true, now.getHour(), true);
            }
        }

        LinkedHashMap<Integer, Double> reOrdered = new LinkedHashMap<>();
        reOrdered.putAll(firstHalf);
        reOrdered.putAll(secondHalf);

        return reOrdered;
    }

    /**
     * Converts the keys in the map to the appropriate format for hour, week day, day of month and month.
     *
     * @param chartData  The map to format the keys
     * @param timePeriod Determines how to keys are to be formatted.
     * @return The formatted keys with their values.
     */
    default LinkedHashMap<String, Double> formatForTimePeriod(LinkedHashMap<Integer, Double> chartData, String timePeriod,
                                                              MachineService machineService) {
        LinkedHashMap<String, Double> formattedChartData = new LinkedHashMap<>();

        switch (timePeriod) {
            case SHIFT:
                chartData.forEach((k, v) -> {
                    Machine machine = machineService.findById(k.longValue()).orElse(null);
                    if (machine != null) {
                        formattedChartData.put(machine.getName(), v);
                    } else {
                        formattedChartData.put("id: " + k, v);
                    }
                });
                break;
            case WEEK:
                chartData.forEach((k, v) -> {
                    DayOfWeek dayOfWeek = DayOfWeek.of(k);
                    formattedChartData.put(dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.UK), v);
                });
                break;
            case MONTH:
                chartData.forEach((k, v) -> {
                    String key = String.valueOf(k);
                    boolean is11Or13Or14 = key.equals("11") || key.equals("12") || key.equals("13");
                    char lastChar = key.charAt(key.length() - 1);
                    switch (lastChar) {
                        case '1':
                            if (is11Or13Or14) {
                                key = key + "th";
                            } else {
                                key = key + "st";
                            }
                            break;
                        case '2':
                            if (is11Or13Or14) {
                                key = key + "th";
                            } else {
                                key = key + "nd";
                            }
                            break;
                        case '3':
                            if (is11Or13Or14) {
                                key = key + "th";
                            } else {
                                key = key + "rd";
                            }
                            break;
                        default:
                            key = key + "th";
                    }
                    formattedChartData.put(key, v);
                });
                break;
            case YEAR:
                chartData.forEach((k, v) -> {
                    Month month = Month.of(k);
                    formattedChartData.put(month.getDisplayName(TextStyle.SHORT, Locale.UK), v);
                });
                break;
            default:
                // DEFAULT is the same as passing in DAY.
                chartData.forEach((k, v) -> {
                    String key = String.valueOf(k);
                    if (key.length() == 1) {
                        key = ("0" + key + ":00");
                    } else {
                        key = (key + ":00");
                    }
                    formattedChartData.put(key, v);
                });
                break;
        }
        return formattedChartData;
    }

    /**
     * Populates an empty map with keys so the x-axis is filled even when no data was created for that particular
     * day/ hour/ month.
     *
     * @param timePeriod The time period determines how many keys.
     * @param <T>        The class type for the values.
     * @return The empty map.
     */
    default <T> TreeMap<Integer, List<T>> getEmptyMapForTimePeriod(String timePeriod,
                                                                   MachineService machineService) {
        TreeMap<Integer, List<T>> emptyMap = new TreeMap<>();
        switch (timePeriod) {
            case SHIFT:
                machineService.findAllByIncludeInSupervisorViewIsTrue().stream()
                        .map(Machine::getId)
                        .forEach(id -> emptyMap.put(id.intValue(), Collections.emptyList()));
                break;
            case WEEK:
                for (int i = 1; i <= 7; i++) {
                    emptyMap.put(i, Collections.emptyList());
                }
                break;
            case MONTH:
                for (int i = 1; i <= 28; i++) {
                    emptyMap.put(i, Collections.emptyList());
                }
                break;
            case YEAR:
                for (int i = 1; i <= 12; i++) {
                    emptyMap.put(i, Collections.emptyList());
                }
                break;
            default:
                // DEFAULT is the same as passing in DAY.
                for (int i = 0; i <= 23; i++) {
                    emptyMap.put(i, Collections.emptyList());
                }
                break;
        }
        return emptyMap;
    }
}
