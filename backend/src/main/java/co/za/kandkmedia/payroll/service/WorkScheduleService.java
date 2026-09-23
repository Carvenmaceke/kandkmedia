package co.za.kandkmedia.payroll.service;

import co.za.kandkmedia.payroll.domain.Company;
import co.za.kandkmedia.payroll.domain.Employee;
import co.za.kandkmedia.payroll.repository.CompanyRepository;
import co.za.kandkmedia.payroll.repository.EmployeeRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Decides which days each employee is in which office, week by week.
 *
 * HR gives every employee a plan:
 * <ul>
 *   <li><b>ROTATING</b> — how many days at each office, e.g. Midrand 2 +
 *   Rosebank 1. The system picks the days, and they change every week.</li>
 *   <li><b>FIXED</b> — exact days and offices (mandatory days, or
 *   "every day" staff). Used as-is every week.</li>
 * </ul>
 *
 * Rules for rotating plans, in priority order:
 * <ol>
 *   <li>Never three weekdays in a row. Only unavoidable at 4 days (the
 *   single valid pattern is Mon, Tue, Thu, Fri) and 5 days (every day).</li>
 *   <li>Stay within each office's desk capacity where at all possible.</li>
 *   <li>Rotate: each week starts from the next valid pattern for that
 *   person, and in split-office plans the office for each day rotates
 *   too, so nobody is stuck on the same days.</li>
 * </ol>
 *
 * Schedules are computed on demand for any week (nothing to "generate" or
 * go stale): fixed plans are placed first, then rotating plans, largest
 * first. Each person's starting point is offset by a stable per-person
 * seed, which also spreads different people across different days.
 */
@Service
@RequiredArgsConstructor
public class WorkScheduleService {

    public static final List<String> OFFICES = List.of("Midrand", "Sandton", "Rosebank");
    public static final String ROTATING = "ROTATING";
    public static final String FIXED = "FIXED";

    static final DayOfWeek[] WEEKDAYS = {
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
    };
    /** A Monday; week numbers for rotation count from here. */
    private static final LocalDate ROTATION_EPOCH = LocalDate.of(2024, 1, 1);

    /** Patterns only used when every preferred one would overfill an office (filled by computePatterns). */
    private static final Map<Integer, List<List<DayOfWeek>>> FALLBACK_PATTERNS = new LinkedHashMap<>();
    /** Preferred day patterns per days-needed count (1-5), in a fixed canonical order. */
    static final Map<Integer, List<List<DayOfWeek>>> PATTERNS = computePatterns();

    private static final ObjectMapper JSON = new ObjectMapper();

    private final EmployeeRepository employeeRepository;
    private final CompanyRepository companyRepository;

    /* ------------------------------------------------------------------ */
    /* Plans                                                              */
    /* ------------------------------------------------------------------ */

    /** An employee's schedule plan. Exactly one of officeDays / fixedDays is used, per mode. */
    public record Plan(String mode, Map<String, Integer> officeDays, Map<DayOfWeek, String> fixedDays) {
        public int total() {
            return FIXED.equals(mode) ? fixedDays.size() : officeDays.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    /** The employee's plan, or null if HR hasn't set one. Older records with only daysPerWeek count as rotating at their home office. */
    public Plan planOf(Employee e) {
        if (FIXED.equals(e.getScheduleMode())) {
            Map<DayOfWeek, String> fixed = new EnumMap<>(DayOfWeek.class);
            readJson(e.getFixedDays(), new TypeReference<Map<String, String>>() {})
                    .forEach((day, office) -> fixed.put(DayOfWeek.valueOf(day), office));
            return fixed.isEmpty() ? null : new Plan(FIXED, Map.of(), fixed);
        }
        if (ROTATING.equals(e.getScheduleMode())) {
            Map<String, Integer> counts = new LinkedHashMap<>(readJson(e.getOfficeDays(), new TypeReference<Map<String, Integer>>() {}));
            counts.values().removeIf(v -> v == null || v <= 0);
            return counts.isEmpty() ? null : new Plan(ROTATING, counts, Map.of());
        }
        if (e.getDaysPerWeek() != null && e.getDaysPerWeek() > 0) {
            return new Plan(ROTATING, Map.of(homeOffice(e), Math.min(5, e.getDaysPerWeek())), Map.of());
        }
        return null;
    }

    /**
     * Applies an HR schedule update. Accepted bodies:
     * {"daysPerWeek": null} clears; {"daysPerWeek": n} = rotating n days at the home office;
     * {"mode":"ROTATING","officeDays":{"Midrand":2,"Rosebank":1}};
     * {"mode":"FIXED","fixedDays":{"MONDAY":"Midrand","FRIDAY":"Rosebank"}}.
     */
    @SuppressWarnings("unchecked")
    public Employee updatePlan(Employee e, Map<String, Object> body) {
        Object mode = body.get("mode");
        if (mode == null) {
            if (!body.containsKey("daysPerWeek") || body.get("daysPerWeek") == null) {
                return employeeRepository.save(clear(e));
            }
            int days = toInt(body.get("daysPerWeek"));
            if (days == 0) return employeeRepository.save(clear(e));
            return employeeRepository.save(setRotating(e, Map.of(homeOffice(e), days)));
        }
        if (ROTATING.equals(mode)) {
            Map<String, Object> raw = (Map<String, Object>) body.getOrDefault("officeDays", Map.of());
            Map<String, Integer> counts = new LinkedHashMap<>();
            raw.forEach((office, n) -> counts.put(office, toInt(n)));
            return employeeRepository.save(setRotating(e, counts));
        }
        if (FIXED.equals(mode)) {
            Map<String, Object> raw = (Map<String, Object>) body.getOrDefault("fixedDays", Map.of());
            Map<DayOfWeek, String> fixed = new EnumMap<>(DayOfWeek.class);
            raw.forEach((day, office) -> {
                if (office == null || office.toString().isBlank()) return;
                DayOfWeek d;
                try { d = DayOfWeek.valueOf(day.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ex) { throw bad("Unknown day: " + day); }
                if (d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY) throw bad("Only weekdays can be scheduled.");
                fixed.put(d, checkOffice(office.toString()));
            });
            if (fixed.isEmpty()) throw bad("Pick at least one day.");
            e.setScheduleMode(FIXED);
            e.setFixedDays(writeJson(fixed.entrySet().stream().collect(Collectors.toMap(x -> x.getKey().name(), Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new))));
            e.setOfficeDays(null);
            e.setDaysPerWeek(fixed.size());
            e.setAssignedWorkDays(null);
            return employeeRepository.save(e);
        }
        throw bad("mode must be ROTATING or FIXED.");
    }

    private Employee setRotating(Employee e, Map<String, Integer> counts) {
        Map<String, Integer> clean = new LinkedHashMap<>();
        counts.forEach((office, n) -> {
            if (n < 0) throw bad("Days can't be negative.");
            if (n > 0) clean.merge(checkOffice(office), n, Integer::sum);
        });
        int total = clean.values().stream().mapToInt(Integer::intValue).sum();
        if (total < 1 || total > 5) throw bad("A rotating plan needs between 1 and 5 office days a week in total.");
        e.setScheduleMode(ROTATING);
        e.setOfficeDays(writeJson(clean));
        e.setFixedDays(null);
        e.setDaysPerWeek(total);
        e.setAssignedWorkDays(null);
        return e;
    }

    private Employee clear(Employee e) {
        e.setScheduleMode(null);
        e.setOfficeDays(null);
        e.setFixedDays(null);
        e.setDaysPerWeek(null);
        e.setAssignedWorkDays(null);
        return e;
    }

    /** Clears every employee's plan. */
    public List<Employee> resetAll() {
        List<Employee> withSchedule = employeeRepository.findAll().stream()
                .filter(e -> e.getScheduleMode() != null || e.getDaysPerWeek() != null
                        || (e.getAssignedWorkDays() != null && !e.getAssignedWorkDays().isBlank()))
                .toList();
        withSchedule.forEach(this::clear);
        return employeeRepository.saveAll(withSchedule);
    }

    /* ------------------------------------------------------------------ */
    /* Weekly schedule                                                    */
    /* ------------------------------------------------------------------ */

    public record WeekSchedule(LocalDate weekStart,
                               Map<String, Integer> capacity,
                               Map<String, Map<DayOfWeek, Integer>> headcount,
                               Map<Long, Map<DayOfWeek, String>> days,
                               List<String> warnings) {}

    /** The schedule for the Mon-Fri week containing the given date. */
    public WeekSchedule weekSchedule(LocalDate anyDay) {
        LocalDate weekStart = anyDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        long weekIndex = ChronoUnit.WEEKS.between(ROTATION_EPOCH, weekStart);
        Map<String, Integer> capacity = capacity();

        List<Employee> scheduled = employeeRepository.findAll().stream()
                .filter(Employee::isActive)
                .filter(e -> planOf(e) != null)
                .sorted(Comparator.comparing(Employee::getEmployeeCode, Comparator.nullsLast(String::compareTo)))
                .toList();

        // First try keeping everyone's rotation; if that overfills an office,
        // re-plan the week balancing desks first (rotation as the tie-break).
        Placement placement = place(scheduled, weekIndex, capacity, false);
        if (placement.overflow(capacity) > 0) {
            Placement balanced = place(scheduled, weekIndex, capacity, true);
            if (balanced.overflow(capacity) < placement.overflow(capacity)) placement = balanced;
        }
        Map<String, Map<DayOfWeek, Integer>> headcount = placement.headcount();
        Map<Long, Map<DayOfWeek, String>> result = placement.days();
        List<String> warnings = new ArrayList<>();

        headcount.forEach((office, byDay) -> byDay.forEach((d, n) -> {
            int cap = capacity.getOrDefault(office, Integer.MAX_VALUE);
            if (n > cap) warnings.add(office + " is over capacity on " + pretty(d) + " (" + n + "/" + cap + ").");
        }));
        return new WeekSchedule(weekStart, capacity, headcount, result, warnings);
    }

    private record Placement(Map<String, Map<DayOfWeek, Integer>> headcount, Map<Long, Map<DayOfWeek, String>> days) {
        int overflow(Map<String, Integer> capacity) {
            int total = 0;
            for (Map.Entry<String, Map<DayOfWeek, Integer>> o : headcount.entrySet()) {
                int cap = capacity.getOrDefault(o.getKey(), Integer.MAX_VALUE);
                for (int n : o.getValue().values()) total += Math.max(0, n - cap);
            }
            return total;
        }
    }

    private Placement place(List<Employee> scheduled, long weekIndex, Map<String, Integer> capacity, boolean balanceFirst) {
        Map<String, Map<DayOfWeek, Integer>> headcount = new LinkedHashMap<>();
        for (String o : OFFICES) headcount.put(o, emptyWeek());
        Map<Long, Map<DayOfWeek, String>> result = new LinkedHashMap<>();

        // Fixed plans take their desks first — they're mandatory.
        for (Employee e : scheduled) {
            Plan plan = planOf(e);
            if (!FIXED.equals(plan.mode())) continue;
            Map<DayOfWeek, String> days = new EnumMap<>(plan.fixedDays());
            days.forEach((d, o) -> headcount.computeIfAbsent(o, k -> emptyWeek()).merge(d, 1, Integer::sum));
            result.put(e.getId(), days);
        }

        // Then rotating plans, biggest first (they have the fewest options).
        List<Employee> rotating = scheduled.stream()
                .filter(e -> ROTATING.equals(planOf(e).mode()))
                .sorted(Comparator.comparingInt((Employee e) -> -planOf(e).total())
                        .thenComparing(Employee::getEmployeeCode, Comparator.nullsLast(String::compareTo)))
                .toList();
        for (Employee e : rotating) {
            Map<DayOfWeek, String> days = placeRotating(e, planOf(e), weekIndex, headcount, capacity, balanceFirst);
            days.forEach((d, o) -> headcount.computeIfAbsent(o, k -> emptyWeek()).merge(d, 1, Integer::sum));
            result.put(e.getId(), days);
        }
        return new Placement(headcount, result);
    }

    /**
     * Picks this week's days for one rotating plan: tries valid patterns
     * starting from this person's rotation point, and for each pattern
     * every rotation of which office gets which day. Normally takes the
     * first combination that fits capacity; with balanceFirst it takes the
     * one that leaves the fullest day emptiest, rotation order breaking ties.
     */
    Map<DayOfWeek, String> placeRotating(Employee e, Plan plan, long weekIndex,
                                         Map<String, Map<DayOfWeek, Integer>> headcount, Map<String, Integer> capacity,
                                         boolean balanceFirst) {
        int n = Math.min(5, plan.total());
        List<List<DayOfWeek>> patterns = PATTERNS.get(n);
        List<String> slots = new ArrayList<>();
        plan.officeDays().entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, Integer> x) -> officeOrder(x.getKey())))
                .forEach(x -> { for (int i = 0; i < x.getValue(); i++) slots.add(x.getKey()); });
        while (slots.size() > n) slots.remove(slots.size() - 1);

        long seed = Math.floorMod(Objects.hashCode(e.getEmployeeCode() != null ? e.getEmployeeCode() : e.getId()), 9973);
        int start = (int) Math.floorMod(seed + weekIndex, patterns.size());
        int officeShift = (int) Math.floorMod(seed + weekIndex, n);

        Map<DayOfWeek, String> best = null;
        long bestScore = Long.MAX_VALUE;
        List<List<DayOfWeek>> ordered = new ArrayList<>();
        for (int i = 0; i < patterns.size(); i++) ordered.add(patterns.get((start + i) % patterns.size()));
        List<List<DayOfWeek>> fallback = FALLBACK_PATTERNS.getOrDefault(n, List.of());
        for (int i = 0; i < fallback.size(); i++) ordered.add(fallback.get((int) Math.floorMod(seed + weekIndex + i, fallback.size())));
        for (int i = 0; i < ordered.size(); i++) {
            List<DayOfWeek> pattern = ordered.get(i);
            for (int j = 0; j < n; j++) {
                int shift = (officeShift + j) % n;
                Map<DayOfWeek, String> candidate = new EnumMap<>(DayOfWeek.class);
                for (int k = 0; k < n; k++) candidate.put(pattern.get(k), slots.get((k + shift) % n));
                int overflow = 0, maxLoad = 0;
                for (Map.Entry<DayOfWeek, String> c : candidate.entrySet()) {
                    int after = headcount.getOrDefault(c.getValue(), Map.of()).getOrDefault(c.getKey(), 0) + 1;
                    overflow += Math.max(0, after - capacity.getOrDefault(c.getValue(), Integer.MAX_VALUE));
                    maxLoad = Math.max(maxLoad, after);
                }
                if (!balanceFirst && overflow == 0) return candidate; // first fit in rotation order
                int order = i * n + j; // distance from this person's rotation point
                long score = balanceFirst
                        ? ((long) overflow * 1_000_000L) + ((long) maxLoad * 1_000L) + order
                        : ((long) overflow * 1_000_000L) + order;
                if (score < bestScore) { bestScore = score; best = candidate; }
            }
        }
        return best;
    }

    /** Stores this week's days on each employee (legacy field some screens still read) and returns them. */
    public List<Employee> autoAssignAll() {
        WeekSchedule week = weekSchedule(LocalDate.now());
        List<Employee> all = employeeRepository.findAll();
        for (Employee e : all) {
            Map<DayOfWeek, String> days = week.days().get(e.getId());
            e.setAssignedWorkDays(days == null ? null : days.keySet().stream().map(Enum::name).collect(Collectors.joining(",")));
        }
        return employeeRepository.saveAll(all);
    }

    /** One employee's days (day -> office) for the week containing the given date. */
    public Map<DayOfWeek, String> daysFor(Employee e, LocalDate anyDay) {
        return weekSchedule(anyDay).days().getOrDefault(e.getId(), Map.of());
    }

    /** Headcount per weekday for one office, this week. */
    public Map<DayOfWeek, Integer> headcountForOffice(String office) {
        return weekSchedule(LocalDate.now()).headcount().getOrDefault(office, emptyWeek());
    }

    /* ------------------------------------------------------------------ */
    /* Capacity & company                                                 */
    /* ------------------------------------------------------------------ */

    public Map<String, Integer> capacity() {
        Company c = company();
        Map<String, Integer> cap = new LinkedHashMap<>();
        cap.put("Midrand", c.getMidrandCapacity());
        cap.put("Sandton", c.getSandtonCapacity());
        cap.put("Rosebank", c.getRosebankCapacity() != null ? c.getRosebankCapacity() : 10);
        return cap;
    }

    public Map<String, Integer> updateCapacity(Map<String, Integer> body) {
        Company c = company();
        body.forEach((office, n) -> {
            if (n == null) return;
            if (n < 0) throw bad("Capacity can't be negative.");
            switch (checkOffice(office)) {
                case "Midrand" -> c.setMidrandCapacity(n);
                case "Sandton" -> c.setSandtonCapacity(n);
                case "Rosebank" -> c.setRosebankCapacity(n);
                default -> { }
            }
        });
        saveCompany(c);
        return capacity();
    }

    public Company company() {
        return companyRepository.findAll().stream().findFirst()
                .orElseGet(() -> companyRepository.save(Company.builder().build()));
    }

    public Company saveCompany(Company company) {
        return companyRepository.save(company);
    }

    /* ------------------------------------------------------------------ */
    /* Helpers                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * Valid patterns never have 3 days in a row. Among those, "spaced"
     * patterns (no two days back to back) are preferred whenever there are
     * at least two of them to rotate between — true for 1 and 2 days. At 3
     * days the only spaced pattern is Mon/Wed/Fri, so all no-3-in-a-row
     * patterns rotate instead. Preferred patterns come first; the rest are
     * only used when capacity forces it.
     */
    private static Map<Integer, List<List<DayOfWeek>>> computePatterns() {
        Map<Integer, List<List<DayOfWeek>>> byCount = new LinkedHashMap<>();
        for (int k = 1; k <= WEEKDAYS.length; k++) {
            List<List<DayOfWeek>> all = new ArrayList<>();
            combine(0, new ArrayList<>(), k, all);
            List<List<DayOfWeek>> valid = all.stream().filter(c -> !hasThreeInARow(c)).toList();
            List<List<DayOfWeek>> usable = valid.isEmpty() ? all : valid;
            List<List<DayOfWeek>> spaced = usable.stream().filter(c -> adjacentPairs(c) == 0).toList();
            List<List<DayOfWeek>> preferred = spaced.size() >= 2 ? spaced : usable;
            byCount.put(k, preferred);
            FALLBACK_PATTERNS.put(k, usable.stream().filter(c -> !preferred.contains(c)).toList());
        }
        return byCount;
    }

    static int adjacentPairs(List<DayOfWeek> days) {
        int n = 0;
        for (int i = 0; i + 1 < WEEKDAYS.length; i++) {
            if (days.contains(WEEKDAYS[i]) && days.contains(WEEKDAYS[i + 1])) n++;
        }
        return n;
    }

    private static void combine(int start, List<DayOfWeek> current, int k, List<List<DayOfWeek>> out) {
        if (current.size() == k) { out.add(List.copyOf(current)); return; }
        for (int i = start; i < WEEKDAYS.length; i++) {
            current.add(WEEKDAYS[i]);
            combine(i + 1, current, k, out);
            current.remove(current.size() - 1);
        }
    }

    static boolean hasThreeInARow(Collection<DayOfWeek> days) {
        Set<DayOfWeek> set = days.isEmpty() ? EnumSet.noneOf(DayOfWeek.class) : EnumSet.copyOf(days);
        for (int i = 0; i + 2 < WEEKDAYS.length; i++) {
            if (set.contains(WEEKDAYS[i]) && set.contains(WEEKDAYS[i + 1]) && set.contains(WEEKDAYS[i + 2])) return true;
        }
        return false;
    }

    private static Map<DayOfWeek, Integer> emptyWeek() {
        Map<DayOfWeek, Integer> m = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek d : WEEKDAYS) m.put(d, 0);
        return m;
    }

    private String homeOffice(Employee e) {
        return e.getOffice() != null && OFFICES.contains(e.getOffice()) ? e.getOffice() : OFFICES.get(0);
    }

    private static int officeOrder(String office) {
        int i = OFFICES.indexOf(office);
        return i < 0 ? OFFICES.size() : i;
    }

    private static String checkOffice(String office) {
        for (String o : OFFICES) if (o.equalsIgnoreCase(office.trim())) return o;
        throw bad("Unknown office: " + office + ". Use one of " + String.join(", ", OFFICES) + ".");
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o).trim()); } catch (NumberFormatException ex) { throw bad("Not a number: " + o); }
    }

    private static String pretty(DayOfWeek d) {
        return d.name().charAt(0) + d.name().substring(1).toLowerCase(Locale.ROOT);
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static <T> T readJson(String json, TypeReference<T> type) {
        try {
            return json == null || json.isBlank() ? JSON.readValue("{}", type) : JSON.readValue(json, type);
        } catch (Exception ex) {
            try { return JSON.readValue("{}", type); } catch (Exception impossible) { throw new IllegalStateException(impossible); }
        }
    }

    private static String writeJson(Object value) {
        try { return JSON.writeValueAsString(value); } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
