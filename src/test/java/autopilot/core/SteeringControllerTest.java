package autopilot.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class SteeringControllerTest {
    private static final double LOW_SPEED = 30.0;

    @ParameterizedTest(name = "{0}")
    @CsvFileSource(resources = "/steering-updates.csv", numLinesToSkip = 1)
    void update_validRequest_clampsTargetAndLimitsFirstStep(String scenario, double request,
                                                          double speed, double target, double firstStep) {
        SteeringController steering = new SteeringController();

        double angle = steering.update(request, speed);

        assertAll(() -> assertEquals(target, steering.getTargetAngleDegrees()),
                () -> assertEquals(firstStep, angle));
    }

    @Test
    void update_repeatedRequest_reachesTargetWithoutOvershoot() {
        SteeringController steering = new SteeringController();

        double[] angles = {steering.update(150.0, LOW_SPEED), steering.update(150.0, LOW_SPEED),
                steering.update(150.0, LOW_SPEED), steering.update(150.0, LOW_SPEED)};

        assertArrayEquals(new double[]{60.0, 120.0, 150.0, 150.0}, angles);
    }

    @Test
    void update_oppositeRequest_changesDirectionByAtMostSixtyDegrees() {
        SteeringController steering = new SteeringController();
        steering.update(60.0, LOW_SPEED);

        assertEquals(0.0, steering.update(-60.0, LOW_SPEED));
    }

    @Test
    void update_enteringHighSpeed_reducesExistingAngleGradually() {
        SteeringController steering = new SteeringController();
        for (int tick = 0; tick < 3; tick++) {
            steering.update(180.0, LOW_SPEED);
        }

        double[] angles = {steering.update(180.0, 121.0), steering.update(180.0, 121.0)};

        assertArrayEquals(new double[]{120.0, 90.0}, angles);
    }

    @ParameterizedTest
    @MethodSource("centeringPaths")
    void selfCenter_offsetAngle_movesToZeroWithoutOvershoot(double initialAngle, double[] expected) {
        SteeringController steering = new SteeringController();
        for (int tick = 0; tick < 3; tick++) {
            steering.update(initialAngle, LOW_SPEED);
        }

        double[] actual = {steering.selfCenter(), steering.selfCenter(), steering.selfCenter()};

        assertArrayEquals(expected, actual);
    }

    static Stream<Arguments> centeringPaths() {
        return Stream.of(arguments(0.0, new double[]{0.0, 0.0, 0.0}),
                arguments(150.0, new double[]{90.0, 30.0, 0.0}),
                arguments(-150.0, new double[]{-90.0, -30.0, 0.0}));
    }

    @ParameterizedTest
    @MethodSource("invalidReadings")
    void update_invalidReading_throwsWithoutChangingAngles(double angle, double speed) {
        SteeringController steering = new SteeringController();
        steering.update(100.0, LOW_SPEED);

        assertThrows(IllegalArgumentException.class, () -> steering.update(angle, speed));
        assertAll(() -> assertEquals(60.0, steering.getCurrentAngleDegrees()),
                () -> assertEquals(100.0, steering.getTargetAngleDegrees()));
    }

    static Stream<Arguments> invalidReadings() {
        return Stream.concat(Stream.of(arguments(0.0, -0.01)),
                Stream.of(Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)
                        .flatMap(value -> Stream.of(arguments(value, LOW_SPEED), arguments(0.0, value))));
    }
}
