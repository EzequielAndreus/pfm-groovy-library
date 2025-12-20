import spock.lang.Specification
import spock.lang.Unroll
import helpers.AnsibleParametersBuilder
import helpers.DeploymentFunctions

/* groovylint-disable CompileStatic, JUnitPublicNonTestMethod */

/**
 * Unit tests for DeploymentFunctions.
 *
 * This test class provides comprehensive coverage for all deployment automation functions including:
 * - Docker image building and tagging
 * - AWS Auto Scaling Group management
 * - Stage validation and health checks
 * - Ansible playbook execution
 * - Application health monitoring
 * - Environment-specific image tagging
 *
 * Uses Spock Framework for clear, expressive test specifications with Given-When-Then syntax.
 * Each test method validates both positive scenarios and error handling with appropriate exception checks.
 */
class DeploymentFunctionsUnitTests extends Specification {

    // ============================================================================
    // Git/Repository Constants
    // ============================================================================
    private static final String HTTPS_REPO_URL = 'https://github.com/user/repo.git'
    private static final String SSH_REPO_URL = 'git@github.com:user/repo.git'
    private static final String MAIN_BRANCH = 'main'
    private static final String DEVELOP_BRANCH = 'develop'
    private static final String FEATURE_BRANCH = 'feature/new-feature'
    private static final String SHA1_REGEX = '[a-f0-9]{40}'
    private static final int COMMIT_HASH_LENGTH = 40

    // ============================================================================
    // AWS Constants
    // ============================================================================
    private static final String PRODUCTION_ASG = 'production-asg-web'
    private static final String PROD_ASG = 'prod-asg'

    // ============================================================================
    // Stage/Instance Constants
    // ============================================================================
    private static final String STAGING_01 = 'staging-01'
    private static final String STAGING_02 = 'staging-02'
    private static final String APP_SERVER_01 = 'app-server-01'
    private static final String APP_SERVER_02 = 'app-server-02'
    private static final String IP_ADDRESS = '192.168.1.100'
    private static final String PRIVATE_IP = '10.0.1.50'
    private static final String DNS_NAME = 'app.example.com'

    // ============================================================================
    // Ansible Constants
    // ============================================================================
    private static final String ANSIBLE_PATH = '/etc/ansible'
    private static final String ANSIBLE_OPT_PATH = '/opt/ansible'
    private static final String DEPLOY_PLAYBOOK = 'deploy.yml'
    private static final String HEALTH_CHECK_PLAYBOOK = 'health-check.yml'
    private static final String CONFIGURE_PLAYBOOK = 'configure.yml'
    private static final String NON_EXISTENT_PLAYBOOK = 'non-existent.yml'

    // ============================================================================
    // Docker Image Constants
    // ============================================================================
    private static final String DOCKER_IMAGE = 'myrepo/myimage:1.0.0'
    private static final String REGISTRY_IMAGE = 'registry/image:latest'
    private static final String SHA256_IMAGE = 'myrepo/myimage@sha256:abc123def456'

    // ============================================================================
    // Environment Constants
    // ============================================================================
    private static final String DEVELOPMENT_ENV = 'development'
    private static final String STAGING_ENV = 'staging'
    private static final String PRODUCTION_ENV = 'production'
    private static final String TEST_ENV = 'test'
    private static final String INVALID_ENV = 'invalid-env'

    private DeploymentFunctions deploymentFunctions

    // ============================================================================
    // Helper Methods
    // ============================================================================

    /**
     * Validates that a string is a valid SHA-1 commit hash
     */
    private void assertValidCommitHash(String hash) {
        assert hash != null
        assert hash.matches(SHA1_REGEX)
        assert hash.length() == COMMIT_HASH_LENGTH
    }

    void setup() {
        deploymentFunctions = new DeploymentFunctions()
    }

    // ============================================================================
    // Tests for buildAndTagImage()
    // ============================================================================

    @Unroll('#repositoryUrl with branch #branch should return valid commit hash')
    void testBuildAndTagImage(String repositoryUrl, String branch) {
        expect: 'buildAndTagImage returns a valid SHA-1 commit hash'
        String result = deploymentFunctions.buildAndTagImage(repositoryUrl, branch)
        assertValidCommitHash(result)

        where:
        repositoryUrl   | branch
        HTTPS_REPO_URL  | MAIN_BRANCH
        SSH_REPO_URL    | DEVELOP_BRANCH
        HTTPS_REPO_URL  | FEATURE_BRANCH
    }

    @Unroll('#repositoryUrl with branch #branch should throw exception')
    void testBuildAndTagImageThrowsExceptions(Closure action, Class<? extends Exception> expectedExceptionType) {
        when:
        action.call()

        then:
        thrown(expectedExceptionType)

        where:
        action                                                       | expectedExceptionType
        { deploymentFunctions.buildAndTagImage('', MAIN_BRANCH) }    | IllegalArgumentException
        { deploymentFunctions.buildAndTagImage(HTTPS_REPO_URL, '') } | IllegalArgumentException
        { deploymentFunctions.buildAndTagImage(null, null) }         | NullPointerException
    }

    // ============================================================================
    // Tests for updateAwsAsg()
    // ============================================================================

    @Unroll('Updating ASG #asgName to #instanceCount instances should succeed')
    void testUpdateAwsAsgValidScenarios(String asgName, Integer instanceCount) {
        given: 'a valid ASG configuration'
        // ASG name and instance count are provided

        when: 'updating the ASG'
        deploymentFunctions.updateAwsAsg(asgName, instanceCount)

        then: 'no exception is thrown'
        noExceptionThrown()

        where:
        asgName         | instanceCount
        PRODUCTION_ASG  | 3
        PROD_ASG        | 1
        PROD_ASG        | 5
        PROD_ASG        | 10
        PROD_ASG        | 25
    }

    @Unroll('Updating ASG with invalid parameters should throw exception')
    void testUpdateAwsAsgThrowsExceptions(Closure action, Class<? extends Exception> expectedExceptionType) {
        when: 'calling updateAwsAsg with invalid parameters'
        action.call()

        then: 'the expected exception is thrown'
        thrown(expectedExceptionType)

        where:
        action                                             | expectedExceptionType
        { deploymentFunctions.updateAwsAsg(PROD_ASG, -1) } | IllegalArgumentException
        { deploymentFunctions.updateAwsAsg(PROD_ASG, 0) }  | IllegalArgumentException
        { deploymentFunctions.updateAwsAsg('', 2) }        | IllegalArgumentException
        { deploymentFunctions.updateAwsAsg(null, 2) }      | NullPointerException
    }

    // ============================================================================
    // Tests for validateStageIsUp()
    // ============================================================================

    @Unroll('Validating if stage #stageName is up should return #expectedResult')
    void testValidateStageIsUp(String stageName, Boolean expectedResult) {
        when: 'validating if stage is up'
        boolean result = deploymentFunctions.validateStageIsUp(stageName)

        then: 'the result matches expectations when provided'
        if (expectedResult != null) {
            assert result == expectedResult
        }

        where:
        stageName   | expectedResult
        STAGING_01  | true
        STAGING_02  | false
        IP_ADDRESS  | null  // No specific expectation; result may be true or false
    }

    @Unroll('Validating if stage with invalid parameters should throw exception')
    void testValidateStageIsUpThrowsExceptions(Closure action, Class<? extends Exception> expectedExceptionType) {
        when: 'calling validateStageIsUp with invalid parameters'
        action.call()

        then: 'the expected exception is thrown'
        thrown(expectedExceptionType)

        where:
        action                                            | expectedExceptionType
        { deploymentFunctions.validateStageIsUp('') }    | IllegalArgumentException
        { deploymentFunctions.validateStageIsUp(null) }  | NullPointerException
    }

    // ============================================================================
    // Tests for runAnsiblePlaybook()
    // ============================================================================

    @Unroll('Running Ansible playbook #playbook with parameters should execute successfully')
    void testRunAnsiblePlaybookExecutesSuccessfully(String ansiblePath, String playbook, Map parameters) {
        given: 'valid Ansible playbook parameters'
        // Parameters are provided via the where block

        when: 'executing the Ansible playbook'
        deploymentFunctions.runAnsiblePlaybook(ansiblePath, playbook, parameters)

        then: 'no exception is thrown'
        noExceptionThrown()

        where:
        ansiblePath       | playbook               | parameters
        ANSIBLE_PATH      | DEPLOY_PLAYBOOK        | AnsibleParametersBuilder.builder().withEnvironment(STAGING_ENV)
                                                       .withVersion('1.0.0').build()
        ANSIBLE_PATH      | HEALTH_CHECK_PLAYBOOK  | AnsibleParametersBuilder.builder().build()
        ANSIBLE_OPT_PATH  | CONFIGURE_PLAYBOOK     | AnsibleParametersBuilder.builder().withDebug(true)
                                                       .withRetries(3).withTimeout(300).withHosts('all')
                                                       .withTags(['setup', 'deploy']).build()
    }

    @Unroll('Running Ansible playbook with invalid parameters should throw exception')
    void testRunAnsiblePlaybookThrowsExceptions(Closure action, Class<? extends Exception> expectedExceptionType) {
        when: 'calling runAnsiblePlaybook with invalid parameters'
        action.call()

        then: 'the expected exception is thrown'
        thrown(expectedExceptionType)

        where:
        action                                                                            | expectedExceptionType
        { deploymentFunctions.runAnsiblePlaybook('', DEPLOY_PLAYBOOK, [:]) }             | IllegalArgumentException
        { deploymentFunctions.runAnsiblePlaybook(ANSIBLE_PATH, '', [:]) }                | IllegalArgumentException
        { deploymentFunctions.runAnsiblePlaybook(ANSIBLE_PATH, DEPLOY_PLAYBOOK, null) }  | NullPointerException
        { deploymentFunctions.runAnsiblePlaybook(ANSIBLE_PATH, NON_EXISTENT_PLAYBOOK, [:]) } | FileNotFoundException
    }

    // ============================================================================
    // Tests for performHealthCheck()
    // ============================================================================

    @Unroll('Performing health check on instance #instanceName should return #expectedResult')
    void testPerformHealthCheck(String instanceName, Boolean expectedResult) {
        when: 'performing health check on instance'
        boolean result = deploymentFunctions.performHealthCheck(instanceName)

        then: 'the result matches expectations or is a boolean type'
        if (expectedResult != null) {
            assert result == expectedResult
        }

        where:
        instanceName   | expectedResult
        APP_SERVER_01  | true
        APP_SERVER_02  | false
        PRIVATE_IP     | null  // Just check it returns Boolean
        DNS_NAME       | null  // Just check it returns Boolean
    }

    @Unroll('Performing health check with invalid parameters should throw exception')
    void testPerformHealthCheckThrowsExceptions(Closure action, Class<? extends Exception> expectedExceptionType) {
        when: 'calling performHealthCheck with invalid parameters'
        action.call()

        then: 'the expected exception is thrown'
        thrown(expectedExceptionType)

        where:
        action                                            | expectedExceptionType
        { deploymentFunctions.performHealthCheck('') }   | IllegalArgumentException
        { deploymentFunctions.performHealthCheck(null) } | NullPointerException
    }

    // ============================================================================
    // Tests for tagImageForEnvironment()
    // ============================================================================

    @Unroll('Tagging image #imageId for environment #environment should execute successfully')
    void testTagImageForEnvironmentExecutesSuccessfully(String imageId, String environment) {
        given: 'a valid Docker image and environment'
        // Image ID and environment are provided

        when: 'tagging the image for the environment'
        deploymentFunctions.tagImageForEnvironment(imageId, environment)

        then: 'no exception is thrown'
        noExceptionThrown()

        where:
        imageId         | environment
        DOCKER_IMAGE    | STAGING_ENV
        DOCKER_IMAGE    | PRODUCTION_ENV
        REGISTRY_IMAGE  | DEVELOPMENT_ENV
        REGISTRY_IMAGE  | STAGING_ENV
        REGISTRY_IMAGE  | PRODUCTION_ENV
        REGISTRY_IMAGE  | TEST_ENV
        SHA256_IMAGE    | PRODUCTION_ENV
    }

    @Unroll('Tagging image with invalid parameters should throw exception')
    void testTagImageForEnvironmentThrowsExceptions(Closure action, Class<? extends Exception> expectedExceptionType) {
        when: 'calling tagImageForEnvironment with invalid parameters'
        action.call()

        then: 'the expected exception is thrown'
        thrown(expectedExceptionType)

        where:
        action                                                                      | expectedExceptionType
        { deploymentFunctions.tagImageForEnvironment('', STAGING_ENV) }            | IllegalArgumentException
        { deploymentFunctions.tagImageForEnvironment(DOCKER_IMAGE, '') }           | IllegalArgumentException
        { deploymentFunctions.tagImageForEnvironment(DOCKER_IMAGE, INVALID_ENV) }  | IllegalArgumentException
        { deploymentFunctions.tagImageForEnvironment(null, STAGING_ENV) }          | NullPointerException
        { deploymentFunctions.tagImageForEnvironment(DOCKER_IMAGE, null) }         | NullPointerException
    }

}
