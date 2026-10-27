package autopilot.core;

import autopilot.exception.IllegalGearChangeException;
import autopilot.exception.SensorFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static autopilot.core.GearBox.Gear.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class GearBoxTest {
    private double speed;
    private GearBox gearbox;

    @BeforeEach
    void setUp() {
        speed = 0.0;
        gearbox = new GearBox(() -> speed);
    }

    @ParameterizedTest
    @MethodSource("transitions")
    void changeTo_requestedTransition_obeysStateAndSpeedRules(GearBox.Gear from, GearBox.Gear to,
                                                            double currentSpeed, boolean allowed) {
        selectInitialGear(from);
        speed = currentSpeed;

        if (allowed) {
            gearbox.changeTo(to);
            assertEquals(to, gearbox.getCurrentGear());
        } else {
            assertThrows(IllegalGearChangeException.class, () -> gearbox.changeTo(to));
            assertEquals(from, gearbox.getCurrentGear());
        }
    }

    static Stream<Arguments> transitions() {
        return Stream.of(
                arguments(P, R, 0.0, true), arguments(P, N, 0.0, false), arguments(P, D, 0.0, false),
                arguments(R, P, 0.0, false), arguments(R, N, 0.0, true), arguments(R, D, 0.0, true),
                arguments(N, P, 0.0, false), arguments(N, R, 0.0, false), arguments(N, D, 0.0, true),
                arguments(D, P, 0.0, false), arguments(D, R, 0.0, true), arguments(D, N, 0.0, false),
                arguments(P, R, 4.99, true), arguments(P, R, 5.0, false), arguments(P, R, 5.01, false),
                arguments(R, D, 0.01, false), arguments(D, R, 0.01, false),
                arguments(R, N, 50.0, true), arguments(N, D, 50.0, true));
    }

    @ParameterizedTest
    @MethodSource("gears")
    void changeTo_currentGear_doesNotReadSensor(GearBox.Gear gear) {
        selectInitialGear(gear);
        gearbox.setSpeedSensor(() -> { throw new AssertionError("No sensor read expected for a no-op"); });

        gearbox.changeTo(gear);

        assertEquals(gear, gearbox.getCurrentGear());
    }

    static Stream<GearBox.Gear> gears() {
        return Stream.of(GearBox.Gear.values());
    }

    @ParameterizedTest
    @MethodSource("invalidSpeeds")
    void changeTo_invalidSensorSpeed_throwsWithoutChangingGear(double invalidSpeed) {
        speed = invalidSpeed;

        assertThrows(IllegalArgumentException.class, () -> gearbox.changeTo(R));
        assertEquals(P, gearbox.getCurrentGear());
    }

    static Stream<Double> invalidSpeeds() {
        return Stream.of(-0.01, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    @Test
    void changeTo_sensorFailure_propagatesWithoutChangingGear() {
        SensorFailureException failure = new SensorFailureException("Speed sensor unavailable");
        gearbox.setSpeedSensor(() -> { throw failure; });

        assertSame(failure, assertThrows(SensorFailureException.class, () -> gearbox.changeTo(R)));
        assertEquals(P, gearbox.getCurrentGear());
    }

    @Test
    void changeTo_nullTarget_throwsWithoutChangingGear() {
        assertThrows(NullPointerException.class, () -> gearbox.changeTo(null));
        assertEquals(P, gearbox.getCurrentGear());
    }

    private void selectInitialGear(GearBox.Gear gear) {
        if (gear != P) {
            gearbox.changeTo(R);
            if (gear != R) {
                gearbox.changeTo(gear);
            }
        }
    }
}
