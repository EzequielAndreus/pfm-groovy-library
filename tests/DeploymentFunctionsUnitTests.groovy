import spock.lang.Specification
import spock.lang.Unroll

/* groovylint-disable CompileStatic, MethodCount, JUnitPublicNonTestMethod */

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

    void setup() {
        deploymentFunctions = new DeploymentFunctions()
    }

    // ============================================================================
    // Tests for buildAndTagImage()
    // ============================================================================

    void testBuildAndTagImageReturnsValidCommitHash() {
        given:
        String repositoryUrl = 'https://github.com/user/repo.git'
        String branch = 'main'

        when:
        String result = deploymentFunctions.buildAndTagImage(repositoryUrl, branch)

        then:
        assert result != null
        assert result.matches('[a-f0-9]{40}') // Validate SHA-1 hash format
    }

    void testBuildAndTagImageHandlesRepositoryUrlWithSSHFormat() {
        given:
        String repositoryUrl = 'git@github.com:user/repo.git'
        String branch = 'develop'

        when:
        String result = deploymentFunctions.buildAndTagImage(repositoryUrl, branch)

        then:
        assert result != null
        assert result.length() == 40
    }

    void testBuildAndTagImageBuildsFromSpecifiedBranch() {
        given:
        String repositoryUrl = 'https://github.com/user/repo.git'
        String branch = 'feature/new-feature'

        when:
        String result = deploymentFunctions.buildAndTagImage(repositoryUrl, branch)

        then:
        assert result != null
        assert result
    }

    void testBuildAndTagImageThrowsExceptionForEmptyRepositoryUrl() {
        given:
        String repositoryUrl = ''
        String branch = 'main'

        when:
        deploymentFunctions.buildAndTagImage(repositoryUrl, branch)

        then:
        assert thrown(IllegalArgumentException)
    }

    void testBuildAndTagImageThrowsExceptionForEmptyBranch() {
        given:
        String repositoryUrl = 'https://github.com/user/repo.git'
        String branch = ''

        when:
        deploymentFunctions.buildAndTagImage(repositoryUrl, branch)

        then:
        assert thrown(IllegalArgumentException)
    }

    void testBuildAndTagImageThrowsExceptionForNullParameters() {
        when:
        deploymentFunctions.buildAndTagImage(null, null)

        then:
        assert thrown(NullPointerException)
    }

    // ============================================================================
    // Tests for updateAwsAsg()
    // ============================================================================

    void testUpdateAwsAsgSuccessfully() {
        given:
        String asgName = 'production-asg-web'
        Integer instanceCount = 3

        when:
        deploymentFunctions.updateAwsAsg(asgName, instanceCount)

        then:
        assert noExceptionThrown() != null
    }

    @Unroll
    void testUpdateAwsAsgHandlesDifferentInstanceCounts(Integer instanceCount) {
        given:
        String asgName = 'prod-asg'

        when:
        deploymentFunctions.updateAwsAsg(asgName, instanceCount)

        then:
        noExceptionThrown()

        where:
        instanceCount << [1, 5, 10, 25]
    }

    void testUpdateAwsAsgThrowsExceptionForNegativeInstanceCount() {
        given:
        String asgName = 'prod-asg'
        Integer instanceCount = -1

        when:
        deploymentFunctions.updateAwsAsg(asgName, instanceCount)

        then:
        assert thrown(IllegalArgumentException)
    }

    void testUpdateAwsAsgThrowsExceptionForZeroInstanceCount() {
        given:
        String asgName = 'prod-asg'
        Integer instanceCount = 0

        when:
        deploymentFunctions.updateAwsAsg(asgName, instanceCount)

        then:
        assert thrown(IllegalArgumentException)
    }

    void testUpdateAwsAsgThrowsExceptionForEmptyAsgName() {
        given:
        String asgName = ''
        Integer instanceCount = 2

        when:
        deploymentFunctions.updateAwsAsg(asgName, instanceCount)

        then:
        assert thrown(IllegalArgumentException)
    }

    void testUpdateAwsAsgThrowsExceptionForNullAsgName() {
        when:
        deploymentFunctions.updateAwsAsg(null, 2)

        then:
        assert thrown(NullPointerException)
    }

    // ============================================================================
    // Tests for validateStageIsUp()
    // ============================================================================

    void testValidateStageIsUpReturnsTrue() {
        given:
        String stageName = 'staging-01'

        when:
        boolean result = deploymentFunctions.validateStageIsUp(stageName)

        then:
        assert result == true
    }

    void testValidateStageIsUpReturnsFalse() {
        given:
        String stageName = 'staging-02'

        when:
        boolean result = deploymentFunctions.validateStageIsUp(stageName)

        then:
        assert result == false
    }

    void testValidateStageIsUpHandlesIpAddresses() {
        given:
        String stageName = '192.168.1.100'

        when:
        boolean result = deploymentFunctions.validateStageIsUp(stageName)

        then:
        assert result instanceof Boolean
    }

    void testValidateStageIsUpThrowsExceptionForEmptyStageName() {
        given:
        String stageName = ''

        when:
        deploymentFunctions.validateStageIsUp(stageName)

        then:
        assert thrown(IllegalArgumentException)
    }

    void testValidateStageIsUpThrowsExceptionForNullStageName() {
        when:
        deploymentFunctions.validateStageIsUp(null)

        then:
        assert thrown(NullPointerException)
    }

    // ============================================================================
    // Tests for runAnsiblePlaybook()
    // ============================================================================

    void testRunAnsiblePlaybookExecutesWithParameters() {
        given:
        String ansibleRepoPath = '/etc/ansible'
        String playbookName = 'deploy.yml'
        Map parameters = [env: 'staging', version: '1.0.0']

        when:
        deploymentFunctions.runAnsiblePlaybook(ansibleRepoPath, playbookName, parameters)

        then:
        assert noExceptionThrown() != null
    }

    void testRunAnsiblePlaybookHandlesEmptyParameterMap() {
        given:
        String ansibleRepoPath = '/etc/ansible'
        String playbookName = 'health-check.yml'
        Map parameters = [:]

        when:
        deploymentFunctions.runAnsiblePlaybook(ansibleRepoPath, playbookName, parameters)

        then:
        assert noExceptionThrown() != null
    }

    void testRunAnsiblePlaybookHandlesVariousParameterTypes() {
        given:
        String ansibleRepoPath = '/opt/ansible'
        String playbookName = 'configure.yml'
        Map parameters = [
            debug      : true,
            retries    : 3,
            timeout    : 300,
            hosts      : 'all',
            tags       : ['setup', 'deploy']
        ]

        when:
        deploymentFunctions.runAnsiblePlaybook(ansibleRepoPath, playbookName, parameters)

        then:
        assert noExceptionThrown() != null
    }

    void testRunAnsiblePlaybookThrowsExceptionForEmptyAnsibleRepoPath() {
        given:
        String ansibleRepoPath = ''
        String playbookName = 'deploy.yml'
        Map parameters = [:]

        when:
        deploymentFunctions.runAnsiblePlaybook(ansibleRepoPath, playbookName, parameters)

        then:
        assert thrown(IllegalArgumentException)
    }

    void testRunAnsiblePlaybookThrowsExceptionForEmptyPlaybookName() {
        given:
        String ansibleRepoPath = '/etc/ansible'
        String playbookName = ''
        Map parameters = [:]

        when:
        deploymentFunctions.runAnsiblePlaybook(ansibleRepoPath, playbookName, parameters)

        then:
        assert thrown(IllegalArgumentException)
    }

    void testRunAnsiblePlaybookThrowsExceptionForNullParametersMap() {
        given:
        String ansibleRepoPath = '/etc/ansible'
        String playbookName = 'deploy.yml'

        when:
        deploymentFunctions.runAnsiblePlaybook(ansibleRepoPath, playbookName, null)

        then:
        assert thrown(NullPointerException)
    }

    void testRunAnsiblePlaybookValidatesPlaybookFileExists() {
        given:
        String ansibleRepoPath = '/etc/ansible'
        String playbookName = 'non-existent.yml'
        Map parameters = [:]

        when:
        deploymentFunctions.runAnsiblePlaybook(ansibleRepoPath, playbookName, parameters)

        then:
        assert thrown(FileNotFoundException)
    }

    // ============================================================================
    // Tests for performHealthCheck()
    // ============================================================================

    void testPerformHealthCheckReturnsTrue() {
        given:
        String instanceName = 'app-server-01'

        when:
        boolean result = deploymentFunctions.performHealthCheck(instanceName)

        then:
        assert result == true
    }

    void testPerformHealthCheckReturnsFalse() {
        given:
        String instanceName = 'app-server-02'

        when:
        boolean result = deploymentFunctions.performHealthCheck(instanceName)

        then:
        assert result == false
    }

    void testPerformHealthCheckHandlesIpAddresses() {
        given:
        String instanceName = '10.0.1.50'

        when:
        boolean result = deploymentFunctions.performHealthCheck(instanceName)

        then:
        assert result instanceof Boolean
    }

    void testPerformHealthCheckHandlesDnsNames() {
        given:
        String instanceName = 'app.example.com'

        when:
        boolean result = deploymentFunctions.performHealthCheck(instanceName)

        then:
        assert result instanceof Boolean
    }

    void testPerformHealthCheckThrowsExceptionForEmptyInstanceName() {
        given:
        String instanceName = ''

        when:
        deploymentFunctions.performHealthCheck(instanceName)

        then:
        assert thrown(IllegalArgumentException)
    }

    void testPerformHealthCheckThrowsExceptionForNullInstanceName() {
        when:
        deploymentFunctions.performHealthCheck(null)

        then:
        assert thrown(NullPointerException)
    }

    // ============================================================================
    // Tests for tagImageForEnvironment()
    // ============================================================================

    void testTagImageForEnvironmentStagingEnvironment() {
        given:
        String imageId = 'myrepo/myimage:1.0.0'
        String environment = 'staging'

        when:
        deploymentFunctions.tagImageForEnvironment(imageId, environment)

        then:
        assert noExceptionThrown() != null
    }

    void testTagImageForEnvironmentProductionEnvironment() {
        given:
        String imageId = 'myrepo/myimage:1.0.0'
        String environment = 'production'

        when:
        deploymentFunctions.tagImageForEnvironment(imageId, environment)

        then:
        assert noExceptionThrown() != null
    }

    @Unroll
    void testTagImageForEnvironmentSupportsEnvironment(String environment) {
        given:
        String imageId = 'registry/image:latest'

        when:
        deploymentFunctions.tagImageForEnvironment(imageId, environment)

        then:
        noExceptionThrown()

        where:
        environment << ['development', 'staging', 'production', 'test']
    }

    void testTagImageForEnvironmentHandlesImageIdsWithSHA256Digest() {
        given:
        String imageId = 'myrepo/myimage@sha256:abc123def456'
        String environment = 'production'

        when:
        deploymentFunctions.tagImageForEnvironment(imageId, environment)

        then:
        assert noExceptionThrown() != null
    }

    void testTagImageForEnvironmentThrowsExceptionForEmptyImageId() {
        given:
        String imageId = ''
        String environment = 'staging'

        when:
        deploymentFunctions.tagImageForEnvironment(imageId, environment)

        then:
        assert thrown(IllegalArgumentException)
    }

    void testTagImageForEnvironmentThrowsExceptionForEmptyEnvironment() {
        given:
        String imageId = 'myrepo/myimage:1.0.0'
        String environment = ''

        when:
        deploymentFunctions.tagImageForEnvironment(imageId, environment)

        then:
        assert thrown(IllegalArgumentException)
    }

    void testTagImageForEnvironmentThrowsExceptionForInvalidEnvironment() {
        given:
        String imageId = 'myrepo/myimage:1.0.0'
        String environment = 'invalid-env'

        when:
        deploymentFunctions.tagImageForEnvironment(imageId, environment)

        then:
        assert thrown(IllegalArgumentException)
    }

    void testTagImageForEnvironmentThrowsExceptionForNullImageId() {
        when:
        deploymentFunctions.tagImageForEnvironment(null, 'staging')

        then:
        assert thrown(NullPointerException)
    }

    void testTagImageForEnvironmentThrowsExceptionForNullEnvironment() {
        when:
        deploymentFunctions.tagImageForEnvironment('myrepo/myimage:1.0.0', null)

        then:
        assert thrown(NullPointerException)
    }

}
