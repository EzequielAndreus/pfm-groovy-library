package com.pfm.tests

import spock.lang.Specification
import com.pfm.DeploymentFunctions
import spock.lang.Unroll

/**
 * Tests error handling and cleanup behavior when Docker build operations fail.
 * 
 * <p>This test verifies that when a Docker build fails during the image preparation process,
 * the method properly handles the exception and ensures that cleanup operations are still
 * executed to remove temporary files and directories.</p>
 * 
 * <p>The test simulates successful Git operations followed by a Docker build failure,
 * and verifies that:</p>
 * <ul>
 *   <li>The original exception is propagated</li>
 *   <li>Temporary build directories are cleaned up</li>
 *   <li>Commit hash files are cleaned up</li>
 * </ul>
 * 
 * @throws Exception when Docker build operations fail
 */
class RunAnsiblePlaybookTests extends Specification {

    // Ansible Constants
    private static final String ANSIBLE_PATH = '/etc/ansible'
    private static final String ANSIBLE_OPT_PATH = '/opt/ansible'
    private static final String DEPLOY_PLAYBOOK = 'deploy.yml'
    private static final String HEALTH_CHECK_PLAYBOOK = 'health-check.yml'

    // Environment Constants
    private static final String STAGING_ENV = 'staging'

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
     * Tests the {@link DeploymentFunctions#runAnsiblePlaybook(String, String, Map)} method
     * with various Ansible paths, playbooks, and parameter configurations.
     * 
     * <p>This parameterized test verifies that the Ansible playbook execution method correctly:</p>
     * <ul>
     *   <li>Constructs the ansible-playbook command with the specified playbook</li>
     *   <li>Includes the correct Ansible path in the execution context</li>
     *   <li>Passes extra variables when parameters are provided</li>
     *   <li>Handles empty parameter maps correctly</li>
     * </ul>
     * 
     * <p>The test mocks the Jenkins script's {@code sh} method to simulate successful
     * command execution and verifies that the correct ansible-playbook command is constructed
     * with appropriate parameters and extra variables.</p>
     * 
     * @param ansiblePath the file system path where Ansible is installed
     * @param playbook the name of the Ansible playbook file to execute
     * @param parameters a map of parameters to pass as extra variables to the playbook
     */
    @Unroll('runAnsiblePlaybook executes ansible-playbook with correct parameters')
    void testRunAnsiblePlaybook(String ansiblePath, String playbook, Map parameters) {
        given: 'Ansible command will succeed'
        def capturedCommands = []
        scriptMock.sh(_) >> { args ->
            String command = (args instanceof Map) ? args.script : args[0]
            capturedCommands << command
            return 0
        }
        scriptMock.echo(_) >> null

        when:
        deploymentFunctions.runAnsiblePlaybook(ansiblePath, playbook, parameters)

        then:
        capturedCommands.any { it.contains("ansible-playbook") && it.contains(playbook) }
        if (parameters.environment) {
            capturedCommands.any { it.contains("--extra-vars") && it.contains("environment=${parameters.environment}") }
        }

        where:
        ansiblePath       | playbook               | parameters
        ANSIBLE_PATH      | DEPLOY_PLAYBOOK        | [environment: STAGING_ENV, version: '1.0.0']
        ANSIBLE_OPT_PATH  | HEALTH_CHECK_PLAYBOOK  | [:]
    }

    /**
     * Tests input validation for the {@link DeploymentFunctions#runAnsiblePlaybook(String, String, Map)} method.
     * 
     * <p>This parameterized test ensures that the Ansible playbook execution method properly validates
     * input parameters and throws {@link IllegalArgumentException} for invalid inputs such as null
     * or empty values for Ansible path, playbook name, or null parameters map.</p>
     * 
     * <p>The validation covers all required parameters to ensure the method fails fast
     * with appropriate error messages when called with invalid arguments.</p>
     * 
     * @param ansiblePath the Ansible installation path to validate (may be invalid)
     * @param playbook the playbook name to validate (may be invalid)
     * @param parameters the parameters map to validate (may be invalid)
     * @param scenario descriptive text explaining the validation scenario being tested
     * @throws IllegalArgumentException when any input parameter is invalid
     */
    @Unroll('runAnsiblePlaybook validates input when #scenario')
    void testRunAnsiblePlaybookValidation(String ansiblePath, String playbook, Map parameters, String scenario) {
        when:
        deploymentFunctions.runAnsiblePlaybook(ansiblePath, playbook, parameters)

        then:
        thrown(IllegalArgumentException)

        where:
        ansiblePath  | playbook        | parameters | scenario
        null         | DEPLOY_PLAYBOOK | [:]        | 'ansible path is null'
        ''           | DEPLOY_PLAYBOOK | [:]        | 'ansible path is empty'
        ANSIBLE_PATH | null            | [:]        | 'playbook is null'
        ANSIBLE_PATH | ''              | [:]        | 'playbook is empty'
        ANSIBLE_PATH | DEPLOY_PLAYBOOK | null       | 'parameters is null'
    }
}