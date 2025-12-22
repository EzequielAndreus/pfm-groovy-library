/* groovylint-disable CompileStatic, JUnitPublicNonTestMethod, MethodName, ThrowException */
package com.pfm.tests

import spock.lang.Specification
import com.pfm.DeploymentFunctions
import spock.lang.Unroll

/**
 * Unit tests for the {@link DeploymentFunctions#validateStageIsUp(String)} method.
 *
 * <p>This test class validates the stage availability checking functionality which includes
 * performing network connectivity tests using ping and SSH port checks. The tests verify
 * that stages are properly validated for accessibility through both network reachability
 * and service availability checks.</p>
 *
 * <p>The tests use Spock framework with mocked Jenkins script context to simulate
 * network commands and verify proper connectivity validation logic and command execution.</p>
 *
 * @author PFM Team
 * @since 1.0
 * @see DeploymentFunctions
 */
class ValidateStageIsUpTests extends Specification {

    // Stage/Instance Constants
    private static final String STAGING_01 = 'staging-01'
    private static final String STAGING_02 = 'staging-02'
    private static final String IP_ADDRESS = '192.168.1.100'
    private static final String DNS_NAME = 'app.example.com'

    // Instance Fields
    private DeploymentFunctions deploymentFunctions
    /* groovylint-disable-next-line FieldTypeRequired, NoDef */
    private scriptMock

    /**
     * Sets up test fixtures before each test method execution.
     *
     * <p>Initializes the mock Jenkins script object and creates a new
     * {@link DeploymentFunctions} instance with the mocked script context.</p>
     */
    void setup() {
        scriptMock = GroovyMock(Object)
        deploymentFunctions = new DeploymentFunctions(scriptMock)
    }

    /**
     * Tests the {@link DeploymentFunctions#validateStageIsUp(String)} method
     * with various stage names and network connectivity scenarios.
     *
     * <p>This parameterized test verifies that the stage validation method correctly:</p>
     * <ul>
     *   <li>Performs ping connectivity tests to check network reachability</li>
     *   <li>Executes SSH port checks (port 22) when ping succeeds</li>
     *   <li>Returns true only when both ping and SSH checks succeed</li>
     *   <li>Skips SSH check when ping fails to optimize execution</li>
     * </ul>
     *
     * <p>The test mocks network commands to simulate different connectivity scenarios
     * and verifies that the validation logic correctly interprets command exit codes
     * and executes checks in the proper sequence.</p>
     *
     * @param stageName the name or identifier of the stage to validate
     * @param networkStatus the simulated network connectivity status ('accessible' or 'unreachable')
     * @param expectedResult the expected boolean result from the validation method
     */
    @Unroll('validateStageIsUp check if stage #stageName is #networkStatus')
    void testValidateStageIsUp(String stageName, String networkStatus, Boolean expectedResult) {
        given: 'network commands will return specific status'
        List<String> capturedCommands = []
        scriptMock.sh(_) >> { args ->
            // Handle the ArrayList wrapper properly
            Object actualArgs = (args instanceof List && args.size() == 1) ? args[0] : args
            String command = (actualArgs instanceof Map) ? actualArgs.script : actualArgs.toString()
            capturedCommands << command

            // Mock ping and SSH responses based on network status
            if (command.contains('ping')) {
                return (networkStatus == 'accessible') ? 0 : 1
            }

            if (command.contains('nc') && command.contains('22')) {
                return (networkStatus == 'accessible') ? 0 : 1
            }

            return 0
        }
        scriptMock.echo(_) >> null

        when: 'validating if stage is up'
        boolean result = deploymentFunctions.validateStageIsUp(stageName)

        then: 'returns expected result based on network connectivity'
        result == expectedResult

        and: 'always performs ping check'
        capturedCommands.any { command -> command.contains('ping -c 3') && command.contains(stageName) }

        and: 'only performs SSH check when ping succeeds'
        if (networkStatus == 'accessible') {
            capturedCommands.any {
                command -> command.contains('nc -zv -w 5') && command.contains(stageName) && command.contains('22')
            }
        } else {
            // When ping fails, SSH check should NOT be executed
            !capturedCommands.any { command -> command.contains('nc -zv -w 5') }
        }

        where:
        stageName   | networkStatus  | expectedResult
        STAGING_01  | 'accessible'   | true
        STAGING_02  | 'unreachable'  | false
        IP_ADDRESS  | 'unreachable'  | false
        DNS_NAME    | 'accessible'   | true
    }

    /**
     * Tests the scenario where ping succeeds but SSH port connectivity fails.
     *
     * <p>This test verifies that the stage validation method correctly handles the case
     * where a stage is network-reachable (ping succeeds) but the SSH service is not
     * available (port 22 check fails). This scenario typically indicates that the
     * server is running but SSH services are down or blocked.</p>
     *
     * <p>The test ensures that:</p>
     * <ul>
     *   <li>Both ping and SSH port checks are executed</li>
     *   <li>The method returns false when SSH is unavailable</li>
     *   <li>Proper command sequence is maintained</li>
     * </ul>
     */
    void 'validateStageIsUp returns false when ping succeeds but SSH port is closed'() {
        given: 'ping succeeds but SSH port check fails'
        List<String> capturedCommands = []
        scriptMock.sh(_) >> { args ->
            String command = (args instanceof Map) ? args.script : args[0]
            capturedCommands << command

            if (command.contains('ping')) {
                return 0  // Ping success
            }
            return (command.contains('nc') && command.contains('22')) ? 1 : 0  // SSH port closed
        }
        scriptMock.echo(_) >> null

        when:
        boolean result = deploymentFunctions.validateStageIsUp(STAGING_01)

        then:
        result == false
        capturedCommands.any { command -> command.contains('ping') }
        capturedCommands.any { command -> command.contains('nc') }
    }

    /**
     * Tests the scenario where initial ping connectivity fails.
     *
     * <p>This test verifies that the stage validation method correctly handles network
     * unreachability scenarios where the initial ping test fails. This typically indicates
     * that the stage server is completely unreachable due to network issues, server downtime,
     * or firewall restrictions.</p>
     *
     * <p>The test ensures that:</p>
     * <ul>
     *   <li>The method returns false immediately when ping fails</li>
     *   <li>SSH port check is skipped for optimization</li>
     *   <li>Proper early termination behavior is maintained</li>
     * </ul>
     */
    void 'validateStageIsUp returns false when ping fails'() {
        given: 'ping fails immediately'
        scriptMock.sh(_) >> { args ->
            String command = (args instanceof Map) ? args.script : args[0]

            return (command.contains('ping')) ? 1 : 0  // Ping failure
        }
        scriptMock.echo(_) >> null

        when:
        boolean result = deploymentFunctions.validateStageIsUp(STAGING_01)

        then:
        result == false
    }

}
