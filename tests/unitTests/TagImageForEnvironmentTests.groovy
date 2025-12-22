package com.pfm.tests

import spock.lang.Specification
import com.pfm.DeploymentFunctions
import spock.lang.Unroll

/**
 * Unit tests for the {@link DeploymentFunctions#tagImageForEnvironment(String, String)} method.
 * 
 * <p>This test class validates the Docker image tagging functionality for different environments.
 * The tests verify that images are properly tagged with environment-specific tags and that
 * both environment and environment-latest tags are created correctly. The tests cover various
 * scenarios including successful tagging operations and input validation.</p>
 * 
 * <p>The tests use Spock framework with mocked Jenkins script context to simulate
 * Docker tagging commands and verify proper tag creation and command execution.</p>
 * 
 * @author PFM Team
 * @since 1.0
 * @see DeploymentFunctions
 */
class TagImageForEnvironmentTests extends Specification {

    // Docker Image Constants
    private static final String DOCKER_IMAGE = 'myrepo/myimage:1.0.0'
    private static final String REGISTRY_IMAGE = 'registry/image:latest'

    // Environment Constants
    private static final String STAGING_ENV = 'staging'
    private static final String PRODUCTION_ENV = 'production'

    // Instance Fields
    private DeploymentFunctions deploymentFunctions
    /* groovylint-disable-next-line FieldTypeRequired */
    private def scriptMock

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
     * Tests the {@link DeploymentFunctions#tagImageForEnvironment(String, String)} method
     * with various Docker images and environment configurations.
     * 
     * <p>This parameterized test verifies that the image tagging method correctly:</p>
     * <ul>
     *   <li>Tags the specified image with the environment name</li>
     *   <li>Creates an additional environment-latest tag</li>
     *   <li>Executes Docker tag commands with proper parameters</li>
     *   <li>Returns true upon successful completion</li>
     * </ul>
     * 
     * <p>The test mocks the Jenkins script's {@code sh} method to simulate successful
     * Docker command execution and verifies that the correct tagging commands are
     * constructed and executed.</p>
     * 
     * @param imageId the Docker image identifier to tag (including repository and tag)
     * @param environment the target environment name for tagging
     */
    @Unroll('tagImageForEnvironment successfully tags #imageId for #environment')
    void testTagImageForEnvironment(String imageId, String environment) {
        given: 'Docker commands will succeed'
        def capturedCommands = []
        scriptMock.sh(_) >> { args ->
            String command = (args instanceof Map) ? args.script : args[0]
            capturedCommands << command
            return 0
        }
        scriptMock.echo(_) >> null

        when:
        boolean result = deploymentFunctions.tagImageForEnvironment(imageId, environment)

        then:
        result == true
        
        // Check that we tag the image for the environment
        capturedCommands.any { it.contains("docker tag") && it.contains(imageId) && it.contains(":${environment}") }
        
        // Check that we also create the environment-latest tag
        capturedCommands.any { it.contains("docker tag") && it.contains(imageId) &&
                            it.contains(":${environment}-latest") }

        where:
        imageId         | environment
        DOCKER_IMAGE    | STAGING_ENV
        REGISTRY_IMAGE  | PRODUCTION_ENV
    }

    /**
     * Tests input validation for the {@link DeploymentFunctions#tagImageForEnvironment(String, String)} method.
     * 
     * <p>This parameterized test ensures that the image tagging method properly validates
     * input parameters and throws {@link IllegalArgumentException} for invalid inputs such as:</p>
     * <ul>
     *   <li>Null or empty image identifiers</li>
     *   <li>Null or empty environment names</li>
     *   <li>Invalid environment names that don't match expected patterns</li>
     * </ul>
     * 
     * <p>The validation ensures that the method fails fast with appropriate error messages
     * when called with invalid arguments, preventing execution of invalid Docker commands.</p>
     * 
     * @param imageId the image identifier to validate (may be invalid)
     * @param environment the environment name to validate (may be invalid)
     * @param scenario descriptive text explaining the validation scenario being tested
     * @throws IllegalArgumentException when any input parameter is invalid
     */
    @Unroll('tagImageForEnvironment validates input when #scenario')
    void testTagImageForEnvironmentValidation(String imageId, String environment, String scenario) {
        when:
        deploymentFunctions.tagImageForEnvironment(imageId, environment)

        then:
        thrown(IllegalArgumentException)

        where:
        imageId      | environment     | scenario
        null         | STAGING_ENV     | 'image ID is null'
        ''           | STAGING_ENV     | 'image ID is empty'
        DOCKER_IMAGE | null            | 'environment is null'
        DOCKER_IMAGE | ''              | 'environment is empty'
        DOCKER_IMAGE | 'invalid-env'   | 'environment is invalid'
    }
}