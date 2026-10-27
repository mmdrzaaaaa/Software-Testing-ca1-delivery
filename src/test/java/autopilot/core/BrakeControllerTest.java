package autopilot.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static autopilot.core.GearBox.Gear.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class BrakeControllerTest {
    @ParameterizedTest
    @CsvSource({
            "0, 0, D, 0", "45, 20, R, 45", "100, 0, N, 100", "120, 0, D, 100",
            "79.99, 40, D, 79.99", "80, 40, D, 80", "80.01, 40, D, 90",
            "100, 29.99, D, 100", "100, 30, D, 100", "100, 30.01, D, 90"
    })
    void apply_validRequest_boundsPressureAndChecksAbsThresholds(double request, double speed,
                                                               GearBox.Gear gear, double expected) {
        BrakeController brakes = new BrakeController();
        assertEquals(expected, brakes.apply(request, speed, gear));
    }

    @Test
    void apply_continuousAbs_alternatesHighAndLowPulses() {
        BrakeController brakes = new BrakeController();

        double[] pulses = {brakes.apply(100.0, 40.0, D), brakes.apply(100.0, 40.0, D),
                brakes.apply(100.0, 40.0, D)};

        assertArrayEquals(new double[]{90.0, 60.0, 90.0}, pulses);
    }

    @ParameterizedTest
    @CsvSource({"80, 40", "100, 30"})
    void apply_afterNonAbsRequest_restartsWithHighPulse(double request, double speed) {
        BrakeController brakes = new BrakeController();
        brakes.apply(100.0, 40.0, D);
        brakes.apply(request, speed, D);

        assertEquals(90.0, brakes.apply(100.0, 40.0, D));
    }

    @ParameterizedTest
    @EnumSource(value = GearBox.Gear.class, names = {"R", "N", "D"})
    void emergencyBrake_nonParkGear_setsFullPressureAndWarning(GearBox.Gear gear) {
        BrakeController brakes = new BrakeController();

        brakes.emergencyBrake(gear);

        assertAll(() -> assertEquals(100.0, brakes.getPressure()),
                () -> assertTrue(brakes.isWarningActive()));
    }

    @Test
    void emergencyBrake_afterAbs_restartsPulseSequence() {
        BrakeController brakes = new BrakeController();
        brakes.apply(100.0, 40.0, D);
        brakes.emergencyBrake(D);

        assertEquals(90.0, brakes.apply(100.0, 40.0, D));
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void apply_invalidRequest_throwsWithoutChangingPressure(double request, double speed) {
        BrakeController brakes = new BrakeController();
        brakes.apply(25.0, 0.0, D);

        assertThrows(IllegalArgumentException.class, () -> brakes.apply(request, speed, D));
        assertEquals(25.0, brakes.getPressure());
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(-0.01, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)
                .flatMap(value -> Stream.of(arguments(value, 0.0), arguments(25.0, value)));
    }

    @Test
    void apply_parkGear_throwsWithoutAdvancingAbs() {
        BrakeController brakes = new BrakeController();
        brakes.apply(100.0, 40.0, D);

        assertThrows(IllegalStateException.class, () -> brakes.apply(100.0, 40.0, P));
        assertEquals(60.0, brakes.apply(100.0, 40.0, D));
    }

    @Test
    void emergencyBrake_parkGear_throwsWithoutActivatingEmergencyState() {
        BrakeController brakes = new BrakeController();
        brakes.apply(25.0, 0.0, D);

        assertThrows(IllegalStateException.class, () -> brakes.emergencyBrake(P));
        assertAll(() -> assertEquals(25.0, brakes.getPressure()),
                () -> assertFalse(brakes.isWarningActive()));
    }

    @Test
    void apply_nullGear_throws() {
        assertThrows(NullPointerException.class, () -> new BrakeController().apply(25.0, 0.0, null));
    }

    @Test
    void emergencyBrake_nullGear_throws() {
        assertThrows(NullPointerException.class, () -> new BrakeController().emergencyBrake(null));
    }
}
