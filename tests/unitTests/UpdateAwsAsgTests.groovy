/* groovylint-disable CompileStatic, JUnitPublicNonTestMethod, MethodName, ThrowException */
package com.pfm.tests

import spock.lang.Specification
import com.pfm.DeploymentFunctions
import spock.lang.Unroll

/**
 * Unit tests for the {@link DeploymentFunctions#updateAwsAsg(String, Integer)} method.
 *
 * <p>This test class validates the AWS Auto Scaling Group (ASG) update functionality which includes
 * querying current and maximum capacity, validating capacity constraints, and updating the desired
 * capacity of ASGs. The tests cover various scenarios including successful updates, input validation,
 * error handling, and logging verification.</p>
 *
 * <p>The tests use Spock framework with mocked Jenkins script context to simulate
 * AWS CLI commands and verify proper ASG capacity management and command execution.</p>
 *
 * @author PFM Team
 * @since 1.0
 * @see DeploymentFunctions
 */
class UpdateAwsAsgTests extends Specification {

    // AWS Constants
    private static final String PRODUCTION_ASG = 'production-asg-web'
    private static final String PROD_ASG = 'prod-asg'

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
     * Tests the {@link DeploymentFunctions#updateAwsAsg(String, Integer)} method
     * with various ASG names and instance count configurations.
     *
     * <p>This parameterized test verifies that the ASG update method correctly:</p>
     * <ul>
     *   <li>Queries the current desired capacity of the ASG</li>
     *   <li>Queries the maximum size limit of the ASG</li>
     *   <li>Calculates the new desired capacity (current + increment)</li>
     *   <li>Updates the ASG with the new desired capacity</li>
     *   <li>Executes AWS CLI commands in the proper sequence</li>
     * </ul>
     *
     * <p>The test mocks AWS CLI responses to simulate a current capacity of 2 instances
     * and a maximum capacity of 10 instances, then verifies that the correct commands
     * are executed to update the ASG capacity.</p>
     *
     * @param asgName the name of the AWS Auto Scaling Group to update
     * @param instanceCount the number of instances to add to the current capacity
     */
    @Unroll('updateAwsAsg successfully updates ASG #asgName to #instanceCount instances')
    void testUpdateAwsAsgSuccess(String asgName, Integer instanceCount) {
        given: 'AWS CLI command will succeed'
        List<String> capturedCommands = []
        scriptMock.sh(_) >> { args ->
            // Handle the ArrayList wrapper
            Object actualArgs = (args instanceof List && args.size() == 1) ? args[0] : args
            String command = (actualArgs instanceof Map) ?
                actualArgs.script : actualArgs.toString()
            capturedCommands << command

            // Handle different return types based on returnStdout parameter
            if (actualArgs instanceof Map && actualArgs.containsKey('returnStdout') &&
                    actualArgs.returnStdout == true) {
                // Return appropriate string values for AWS CLI queries
                if (command.contains('DesiredCapacity')) {
                    return '2'  // Current capacity
                } else if (command.contains('MaxSize')) {
                    return '10'  // Max capacity
                }
                return 'success'  // Default string return
            }
            return 0  // Return status code for regular sh calls
        }
        scriptMock.echo(_) >> null

        when: 'updating AWS ASG'
        deploymentFunctions.updateAwsAsg(asgName, instanceCount)

        then: 'AWS CLI commands are executed correctly'
        capturedCommands.size() == 3  // describe (current), describe (max), set-desired-capacity

        // Check that we query current capacity
        capturedCommands.any {
            command -> command.contains('aws autoscaling describe-auto-scaling-groups') &&
                command.contains('DesiredCapacity') && command.contains(asgName)
        }

        // Check that we query max capacity
        capturedCommands.any {
            command -> command.contains('aws autoscaling describe-auto-scaling-groups') &&
                command.contains('MaxSize') && command.contains(asgName)
        }

        // Check that we set the new desired capacity (current 2 + instanceCount)
        Integer expectedNewCapacity = 2 + instanceCount
        capturedCommands.any {
            command -> command.contains('aws autoscaling set-desired-capacity') &&
                command.contains("--auto-scaling-group-name ${asgName}") &&
                command.contains("--desired-capacity ${expectedNewCapacity}")
        }

        where:
        asgName             | instanceCount
        PROD_ASG            | 3
        PRODUCTION_ASG      | 5
        'test-asg'          | 1
        'staging-asg-web'   | 2
    }

    /**
     * Tests input validation for the {@link DeploymentFunctions#updateAwsAsg(String, Integer)} method.
     *
     * <p>This parameterized test ensures that the ASG update method properly validates
     * input parameters and throws {@link IllegalArgumentException} for invalid inputs such as:</p>
     * <ul>
     *   <li>Null, empty, or whitespace-only ASG names</li>
     *   <li>Null, negative, or zero instance counts</li>
     * </ul>
     *
     * <p>The validation ensures that the method fails fast with appropriate error messages
     * when called with invalid arguments, preventing execution of invalid AWS CLI commands.</p>
     *
     * @param asgName the ASG name to validate (may be invalid)
     * @param instanceCount the instance count to validate (may be invalid)
     * @param scenario descriptive text explaining the validation scenario being tested
     * @throws IllegalArgumentException when any input parameter is invalid
     */
    @Unroll('updateAwsAsg validates input before execution')
    void testUpdateAwsAsgValidation(String asgName, Integer instanceCount, String scenario) {
        when:
        deploymentFunctions.updateAwsAsg(asgName, instanceCount)

        then:
        thrown(IllegalArgumentException)

        where:
        asgName     | instanceCount | scenario
        null        | 2             | 'ASG name is null'
        ''          | 2             | 'ASG name is empty'
        '   '       | 2             | 'ASG name is whitespace'
        PROD_ASG    | null          | 'instance count is null'
        PROD_ASG    | -1            | 'instance count is negative'
        PROD_ASG    | 0             | 'instance count is zero'
    }

    /**
     * Tests error handling behavior when AWS CLI commands fail during ASG updates.
     *
     * <p>This test verifies that when AWS CLI operations fail (such as network issues,
     * permission problems, or invalid ASG names), the method properly propagates the
     * exception without attempting to continue with subsequent operations.</p>
     *
     * <p>The test simulates an AWS CLI failure and ensures that the original exception
     * is thrown to the caller for appropriate error handling at higher levels.</p>
     *
     * @throws Exception when AWS CLI operations fail
     */
    void 'updateAwsAsg handles AWS CLI failure gracefully'() {
        given: 'AWS CLI command fails'
        scriptMock.sh(_) >> { throw new Exception('AWS CLI failed') }
        scriptMock.echo(_) >> null

        when:
        deploymentFunctions.updateAwsAsg(PROD_ASG, 3)

        then:
        Exception exception = thrown(Exception)
        exception.message == 'AWS CLI failed'
    }

    /**
     * Tests logging behavior during successful ASG update operations.
     *
     * <p>This test verifies that the ASG update method produces appropriate log messages
     * during the update process, including:</p>
     * <ul>
     *   <li>Initial notification of the ASG update operation</li>
     *   <li>Success confirmation upon completion</li>
     * </ul>
     *
     * <p>The test captures all log messages produced during execution and verifies
     * that the expected informational messages are generated for monitoring and
     * debugging purposes.</p>
     */
    void 'updateAwsAsg logs appropriate messages'() {
        given: 'successful execution'
        List<String> loggedMessages = []

        // Use the same mock logic as the success test
        scriptMock.sh(_) >> { args ->
            // Handle the ArrayList wrapper
            Object actualArgs = (args instanceof List && args.size() == 1) ? args[0] : args
            String command = (actualArgs instanceof Map) ? actualArgs.script : actualArgs.toString()

            // Handle different return types based on returnStdout parameter
            if (actualArgs instanceof Map && actualArgs.containsKey('returnStdout') &&
                    actualArgs.returnStdout == true) {
                // Return appropriate string values for AWS CLI queries
                if (command.contains('DesiredCapacity')) {
                    return '2'  // Current capacity
                } else if (command.contains('MaxSize')) {
                    return '10'  // Max capacity
                }
                return 'success'  // Default string return
            }
            return 0  // Return status code for regular sh calls
        }

        scriptMock.echo(_) >> { args -> loggedMessages << args[0] }

        when:
        deploymentFunctions.updateAwsAsg(PROD_ASG, 3)

        then:
        loggedMessages.any { String msg -> msg.contains("Updating ASG '${PROD_ASG}'") }
        loggedMessages.any { String msg -> msg.contains('Successfully updated ASG') }
    }

}
