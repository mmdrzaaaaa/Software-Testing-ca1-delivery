package autopilot.core;

import autopilot.exception.SensorFailureException;
import autopilot.sensor.LaneSensor;
import autopilot.sensor.ObstacleSensor;
import autopilot.sensor.SpeedSensor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class AutoDriverTest {
    private static final double CRUISING_SPEED = 80.0;
    private static final double CLEAR_DISTANCE = 100.0;
    private static final double CENTERED_LANE = 0.0;
    private static final double LANE_OFFSET = 1.0;
    private static final double CORRECTION_ANGLE = 45.0;
    private static final double SAFE_MODE_PRESSURE = 30.0;
    private static final String SENSOR_FAILURE = "Sensor unavailable";

    @Test
    void tick_clearRoadAtTargetSpeed_holdsWithoutBrakingOrSafeMode() {
        AutoDriver driver = new AutoDriver(() -> CRUISING_SPEED, () -> CLEAR_DISTANCE,
                () -> CENTERED_LANE, CRUISING_SPEED);

        AutoDriver.DriveResult result = driver.tick(GearBox.Gear.D);

        assertEquals(new AutoDriver.DriveResult(0.0, 0.0, AdaptiveCruise.Mode.HOLD, false, false), result);
    }

    @ParameterizedTest
    @MethodSource("laneCorrections")
    void tick_laneDeviation_steersInOppositeDirection(double offset, double correction) {
        AutoDriver driver = new AutoDriver(() -> CRUISING_SPEED, () -> CLEAR_DISTANCE,
                () -> offset, CRUISING_SPEED);

        assertEquals(correction, driver.tick(GearBox.Gear.D).steeringAngleDegrees());
    }

    static Stream<Arguments> laneCorrections() {
        return Stream.of(arguments(LANE_OFFSET, -CORRECTION_ANGLE),
                arguments(-LANE_OFFSET, CORRECTION_ANGLE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failedSensors")
    void tick_sensorFailure_returnsSafeModeResult(String scenario, SpeedSensor speed,
                                                 ObstacleSensor obstacle) {
        LaneSensor lane = () -> CENTERED_LANE;
        AutoDriver driver = new AutoDriver(speed, obstacle, lane, CRUISING_SPEED);

        AutoDriver.DriveResult result = driver.tick(GearBox.Gear.D);

        assertEquals(new AutoDriver.DriveResult(0.0, SAFE_MODE_PRESSURE,
                AdaptiveCruise.Mode.BRAKE, false, true), result);
    }

    static Stream<Arguments> failedSensors() {
        SpeedSensor workingSpeed = () -> CRUISING_SPEED;
        ObstacleSensor workingObstacle = () -> CLEAR_DISTANCE;
        SpeedSensor failedSpeed = () -> { throw new SensorFailureException(SENSOR_FAILURE); };
        ObstacleSensor failedObstacle = () -> { throw new SensorFailureException(SENSOR_FAILURE); };
        return Stream.of(arguments("speed sensor failure", failedSpeed, workingObstacle),
                arguments("obstacle sensor failure", workingSpeed, failedObstacle));
    }
}
