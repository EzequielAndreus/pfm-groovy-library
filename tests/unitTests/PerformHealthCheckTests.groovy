/* groovylint-disable CompileStatic, JUnitPublicNonTestMethod */
package com.pfm.tests

import spock.lang.Specification
import com.pfm.DeploymentFunctions
import spock.lang.Unroll

/**
 * Unit tests for the {@link DeploymentFunctions#performHealthCheck(String)} method.
 *
 * <p>This test class validates the health check functionality for application instances
 * by testing various scenarios including successful health checks, failed health checks,
 * and input validation.</p>
 *
 * <p>The tests use Spock framework with mocked Jenkins script context to simulate
 * different HTTP response codes from health check endpoints.</p>
 *
 * @author PFM Team
 * @since 1.0
 * @see DeploymentFunctions
 */
class PerformHealthCheckTests extends Specification {

    // Stage/Instance Constants
    private static final String APP_SERVER_01 = 'app-server-01'
    private static final String APP_SERVER_02 = 'app-server-02'
    private static final String PRIVATE_IP = '192.168.1.100'
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
    /* groovylint-disable-next-line JUnitPublicNonTestMethod */
    void setup() {
        scriptMock = GroovyMock(Object)
        deploymentFunctions = new DeploymentFunctions(scriptMock)
    }

    /**
     * Tests the {@link DeploymentFunctions#performHealthCheck(String)} method with various
     * instance names and HTTP status codes.
     *
     * <p>This parameterized test verifies that the health check method correctly interprets
     * HTTP status codes returned from curl commands and returns the appropriate boolean result.</p>
     *
     * <p>The test mocks the Jenkins script's {@code sh} method to simulate curl responses
     * and verifies that the correct curl command is executed with the proper endpoint.</p>
     *
     * @param instanceName the name or identifier of the instance to health check
     * @param httpStatus the HTTP status code that the mocked curl command should return
     * @param expectedResult the expected boolean result from the health check method
     */
    @Unroll('performHealthCheck returns #expectedResult when health endpoint returns #httpStatus')
    void testPerformHealthCheck(String instanceName, Integer httpStatus, Boolean expectedResult) {
        given: 'health check endpoint returns specific status'
        List<String> capturedCommands = []
        scriptMock.sh(_) >> { args ->
            Object actualArgs = (args instanceof List && args.size() == 1) ? args[0] : args
            String command = (actualArgs instanceof Map) ? actualArgs.script : actualArgs.toString()
            capturedCommands << command

            // Always return HTTP status as string since performHealthCheck uses returnStdout: true
            if (command.contains('curl')) {
                return httpStatus.toString()  // curl returns HTTP status code
            }
            return '0'  // Default string return
        }
        scriptMock.echo(_) >> null

        when:
        boolean result = deploymentFunctions.performHealthCheck(instanceName)

        then:
        result == expectedResult
        capturedCommands.any {
            command -> command.contains('curl') && command.contains(instanceName) && command.contains('/health')
        }

        where:
        instanceName   | httpStatus | expectedResult
        APP_SERVER_01  | 200        | true
        APP_SERVER_02  | 503        | false
        PRIVATE_IP     | 200        | true
        DNS_NAME       | 500        | false
    }

    /**
     * Tests input validation for the {@link DeploymentFunctions#performHealthCheck(String)} method.
     *
     * <p>This parameterized test ensures that the health check method properly validates
     * input parameters and throws {@link IllegalArgumentException} for invalid inputs
     * such as null, empty, or whitespace-only instance names.</p>
     *
     * @param instanceName the invalid instance name to test
     * @param scenario descriptive text explaining the validation scenario being tested
     * @throws IllegalArgumentException when the instance name is invalid
     */
    @Unroll('performHealthCheck validates input when #scenario')
    void testPerformHealthCheckValidation(String instanceName, String scenario) {
        when:
        deploymentFunctions.performHealthCheck(instanceName)

        then:
        thrown(IllegalArgumentException)

        where:
        instanceName | scenario
        null         | 'instance name is null'
        ''           | 'instance name is empty'
        '   '        | 'instance name is whitespace'
    }

}
