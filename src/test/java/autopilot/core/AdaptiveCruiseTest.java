package autopilot.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static autopilot.core.AdaptiveCruise.Mode.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class AdaptiveCruiseTest {
    private static final double TARGET_SPEED = 100.0;
    private static final double TOLERANCE = 1e-9;

    @ParameterizedTest
    @MethodSource("safeDistances")
    void safeDistanceFor_validSpeed_returnsHalfTheSpeed(double speed, double distance) {
        assertEquals(distance, new AdaptiveCruise(TARGET_SPEED).safeDistanceFor(speed), TOLERANCE);
    }

    static Stream<Arguments> safeDistances() {
        return Stream.of(arguments(0.0, 0.0), arguments(1.0, 0.5), arguments(80.0, 40.0));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
    void safeDistanceFor_invalidSpeed_throws(double speed) {
        AdaptiveCruise cruise = new AdaptiveCruise(TARGET_SPEED);
        assertThrows(IllegalArgumentException.class, () -> cruise.safeDistanceFor(speed));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("freshDecisions")
    void update_freshCruise_returnsExpectedDecision(String scenario, double speed, double distance,
                                                  AdaptiveCruise.Mode mode, double amount) {
        AdaptiveCruise.Decision decision = new AdaptiveCruise(TARGET_SPEED).update(speed, distance);
        assertAll(() -> assertEquals(mode, decision.mode()),
                () -> assertEquals(amount, decision.amount(), TOLERANCE));
    }

    static Stream<Arguments> freshDecisions() {
        return Stream.of(
                arguments("below emergency boundary", 80.0, 19.99, EMERGENCY_BRAKE, 100.0),
                arguments("at emergency boundary", 80.0, 20.0, EMERGENCY_BRAKE, 100.0),
                arguments("above emergency boundary", 80.0, 20.01, BRAKE, 49.975),
                arguments("below safe distance", 80.0, 39.99, BRAKE, 0.025),
                arguments("at safe distance", 80.0, 40.0, HOLD, 0.0),
                arguments("above safe distance", 80.0, 40.01, ACCELERATE, 5.0),
                arguments("exact acceleration cap", 95.0, 100.0, ACCELERATE, 5.0),
                arguments("near target speed", 98.0, 100.0, ACCELERATE, 2.0),
                arguments("at target speed", 100.0, 100.0, HOLD, 0.0),
                arguments("above target speed", 101.0, 100.0, HOLD, 0.0),
                arguments("stationary with no clearance", 0.0, 0.0, EMERGENCY_BRAKE, 100.0),
                arguments("stationary with clearance", 0.0, 1.0, ACCELERATE, 5.0));
    }

    @ParameterizedTest
    @MethodSource("brakingDecisions")
    void update_alreadyBraking_obeysReleaseThreshold(double distance, AdaptiveCruise.Mode mode,
                                                    double amount) {
        AdaptiveCruise cruise = new AdaptiveCruise(TARGET_SPEED);
        cruise.update(80.0, 30.0);

        AdaptiveCruise.Decision decision = cruise.update(80.0, distance);

        assertAll(() -> assertEquals(mode, decision.mode()),
                () -> assertEquals(amount, decision.amount(), TOLERANCE));
    }

    static Stream<Arguments> brakingDecisions() {
        return Stream.of(arguments(20.0, EMERGENCY_BRAKE, 100.0),
                arguments(35.0, BRAKE, 12.5), arguments(40.0, BRAKE, 10.0),
                arguments(47.99, BRAKE, 10.0), arguments(48.0, ACCELERATE, 5.0),
                arguments(48.01, ACCELERATE, 5.0));
    }

    @Test
    void update_afterEmergencyBrake_keepsBrakingUntilRelease() {
        AdaptiveCruise cruise = new AdaptiveCruise(TARGET_SPEED);
        cruise.update(80.0, 20.0);

        assertEquals(new AdaptiveCruise.Decision(BRAKE, 10.0), cruise.update(80.0, 45.0));
    }

    @Test
    void update_afterRelease_clearsBrakingHistory() {
        AdaptiveCruise cruise = new AdaptiveCruise(TARGET_SPEED);
        cruise.update(80.0, 30.0);
        cruise.update(80.0, 48.0);

        assertEquals(new AdaptiveCruise.Decision(ACCELERATE, 5.0), cruise.update(80.0, 45.0));
    }

    @ParameterizedTest
    @MethodSource("invalidReadings")
    void update_invalidReading_throwsWithoutChangingBrakingState(double speed, double distance) {
        AdaptiveCruise cruise = new AdaptiveCruise(TARGET_SPEED);
        cruise.update(80.0, 30.0);

        assertThrows(IllegalArgumentException.class, () -> cruise.update(speed, distance));
        assertEquals(new AdaptiveCruise.Decision(BRAKE, 10.0), cruise.update(80.0, 45.0));
    }

    static Stream<Arguments> invalidReadings() {
        return Stream.of(-0.01, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)
                .flatMap(value -> Stream.of(arguments(value, 100.0), arguments(80.0, value)));
    }
}
