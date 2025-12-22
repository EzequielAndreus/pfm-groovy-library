/* groovylint-disable CatchException */
package com.pfm

import groovy.json.JsonOutput
import java.text.SimpleDateFormat

/* groovylint-disable CompileStatic */

/**
 * Provides deployment-related utility functions for CI/CD pipelines.
 * Includes operations for Docker image management, AWS Auto Scaling Group updates,
 * instance validation, Ansible playbook execution, and health checks.
 */
class DeploymentFunctions {

    /* groovylint-disable-next-line FieldTypeRequired, NoDef */
    def script

    /* groovylint-disable-next-line , MethodParameterTypeRequired, NoDef */
    DeploymentFunctions(script = null) {
        this.script = script
    }

    /**
    * Builds and tags a new Docker image from a specific branch.
    * Clones the repository, builds the image, tags it with the commit hash, and publishes it.
    *
    * @param repositoryUrl The URL of the repository to clone (required)
    * @param branch The branch name to build from (required)
    * @return The commit hash (shortened)
    * @throws IllegalArgumentException if parameters are null or empty
    */
    String prepareDockerImage(String repositoryUrl, String branch, String imageName) {
        // Input validation
        if (!repositoryUrl?.trim()) {
            throw new IllegalArgumentException('Repository URL cannot be null or empty')
        }
        if (!branch?.trim()) {
            throw new IllegalArgumentException('Branch cannot be null or empty')
        }
        if (!imageName?.trim()) {
            throw new IllegalArgumentException('Image name cannot be null or empty')
        }

        String workDir = "/tmp/build-${System.currentTimeMillis()}"

        try {
            // Clone the repository
            script.sh "git clone ${repositoryUrl} ${workDir}"

            // Checkout the specified branch
            script.sh "cd ${workDir} && git checkout ${branch}"

            // Get the short commit hash
            script.sh "cd ${workDir} && git rev-parse --short HEAD > /tmp/commit-hash.txt"

            // Read the commit hash from file
            String commitHash = script.readFile('/tmp/commit-hash.txt').trim()

            // Build the Docker image
            String dateTag = new SimpleDateFormat('yyyyMMdd', Locale.US).format(new Date())
            script.sh "cd ${workDir} && docker build -t ${imageName}:${commitHash} -t ${imageName}:${dateTag} ."

            // publish image in Docker Hub
            script.sh "docker push ${imageName}:${commitHash}"

            script.echo "Successfully built, tagged, and published image as ${imageName}:${commitHash}"
            return commitHash
        } catch (Exception e) {
            // Shell command failures (git, docker commands with non-zero exit codes)
            script.echo "Build failed: ${e.message}"
            throw e
        } catch (IOException e) {
            // File I/O operations (readFile failures, file system issues)
            script.echo "I/O error during build: ${e.message}"
            throw e
        }
        finally {
            // Clean up the working directory and temp file
            script.sh "rm -rf ${workDir}"
            script.sh 'rm -f /tmp/commit-hash.txt'
        }
    }

    /**
    * Updates an AWS Auto Scaling Group by increasing its desired capacity.
    * Increases the current desired capacity by the specified number of instances
    * without modifying the group's minimum or maximum capacity settings.
    *
    * Note: Requires AWS CLI to be configured with appropriate credentials (IAM role or profile).
    *
    * @param asgName The name of the Auto Scaling Group (required)
    * @param instanceCount The number of instances to add to the current desired capacity (must be > 0)
    * @return true if update is successful, false otherwise
    * @throws IllegalArgumentException if parameters are invalid
    */
    boolean updateAwsAsg(String asgName, Integer instanceCount) {
        // Input validation
        if (!asgName?.trim()) {
            throw new IllegalArgumentException('ASG name cannot be null or empty')
        }
        if (instanceCount == null || instanceCount <= 0) {
            throw new IllegalArgumentException('Instance count must be greater than 0')
        }

        try {
            // Get current desired capacity
            Integer currentCapacity = script.sh(
                script: 'aws autoscaling describe-auto-scaling-groups ' +
                        "--auto-scaling-group-names ${asgName} " +
                        "--query 'AutoScalingGroups[0].DesiredCapacity' " +
                        '--output text',
                returnStdout: true
            ).trim().toInteger()

            // Calculate new desired capacity
            Integer newCapacity = currentCapacity + instanceCount

            // Get the maximum capacity of the ASG
            Integer maxCapacity = script.sh(
                script: "aws autoscaling describe-auto-scaling-groups --auto-scaling-group-names ${asgName} " +
                        "--query 'AutoScalingGroups[0].MaxSize' --output text",
                returnStdout: true
            ).trim().toInteger()

            if (newCapacity > maxCapacity) {
                throw new IllegalArgumentException("New capacity ${newCapacity} exceeds maximum ${maxCapacity}")
            }

            script.echo "Updating ASG '${asgName}' from ${currentCapacity} to ${newCapacity} instances"

            // Update the Auto Scaling Group
            script.sh "aws autoscaling set-desired-capacity " +
                "--auto-scaling-group-name ${asgName} " +
                "--desired-capacity ${newCapacity}"

            script.echo "Successfully updated ASG '${asgName}' desired capacity to ${newCapacity}"
            return true
        } catch (IOException | InterruptedException e) {
            error "Failed to update ASG '${asgName}': ${e.message}"
        }
    }

    /**
    * Validates that a stage is accessible and ready to receive traffic.
    * Performs ping checks and verifies SSH port accessibility.
    *
    * @param instanceAddress IP address or hostname of the instance to check (required)
    * @return true if stage is accessible, false otherwise
    * @throws IllegalArgumentException if instanceAddress is null or empty
    */
    boolean validateStageIsUp(String instanceAddress) {
        // Input validation
        if (!instanceAddress?.trim()) {
            throw new IllegalArgumentException('Instance address cannot be null or empty')
        }

        try {
            script.echo "Validating stage at ${instanceAddress}"

            // Perform ping check (send 3 pings)
            Integer pingResult = script.sh(
                script: "ping -c 3 ${instanceAddress}",
                returnStatus: true
            )

            if (pingResult) {
                script.echo "Ping check failed for ${instanceAddress}"
                return false
            }

            script.echo "Ping check passed for ${instanceAddress}"

            // Check SSH port (22) accessibility
            Integer sshResult = script.sh(
                script: "nc -zv -w 5 ${instanceAddress} 22",
                returnStatus: true
            )

            if (sshResult) {
                script.echo "SSH port check failed for ${instanceAddress}"
                return false
            }

            script.echo "SSH port check passed for ${instanceAddress}"
            script.echo "Stage ${instanceAddress} is up and accessible"
            return true
        } catch (IOException | InterruptedException e) {
            script.echo "Error validating stage: ${e.message}"
            return false
        }
    }

    /**
    * Executes an Ansible playbook with specified parameters.
    *
    * Note: Requires Ansible to be installed and properly configured on the executing system.
    *
    * @param ansibleRepoPath The path to the Ansible repository (required)
    * @param playbookName The name of the playbook to run (required)
    * @param parameters A map of parameters to pass to the playbook (required, can be empty)
    * @throws IllegalArgumentException if parameters are invalid
    * @throws FileNotFoundException if playbook file does not exist
    */
    void runAnsiblePlaybook(String ansibleRepoPath, String playbookName, Map parameters) {
        // Input validation
        if (!ansibleRepoPath?.trim()) {
            throw new IllegalArgumentException('Ansible repository path cannot be null or empty')
        }
        if (!playbookName?.trim()) {
            throw new IllegalArgumentException('Playbook name cannot be null or empty')
        }
        if (parameters == null) {
            throw new IllegalArgumentException('Parameters map cannot be null (use empty map instead)')
        }

        try {
            script.echo "Running Ansible playbook: ${playbookName}"

            // Check if playbook file exists
            String playbookPath = "${ansibleRepoPath}/${playbookName}"
            Integer fileCheckResult = script.sh(
                script: "test -f ${playbookPath}",
                returnStatus: true
            )

            if (fileCheckResult) {
                throw new FileNotFoundException("Playbook file not found: ${playbookPath}")
            }

            // Build extra vars from parameters map using JSON format for better handling
            String extraVars = ''
            if (parameters) {
                // Convert map to JSON string for safer passing
                String jsonParams = JsonOutput.toJson(parameters)
                extraVars = "--extra-vars '${jsonParams}'"
            }

            // Build and execute the ansible-playbook command
            String command = "ansible-playbook ${playbookPath} ${extraVars}"

            script.echo "Executing: ${command}"
            script.sh command

            script.echo "Successfully executed playbook ${playbookName}"
        } catch (IOException | InterruptedException e) {
            error "Failed to run Ansible playbook '${playbookName}': ${e.message}"
        }
    }

    /**
    * Performs a health check on the application running in a specified instance.
    * Tries multiple common health endpoints until one responds with HTTP 200.
    *
    * @param instanceAddress IP address or hostname of the instance to check (required)
    * @param healthEndpoints List of endpoint paths to check (optional, defaults to ['/health', '/healthz', '/'])
    * @param protocol Protocol to use for the health check (optional, defaults to 'http')
    * @param connectTimeout Connection timeout in seconds (optional, defaults to 5)
    * @param maxTimeout Maximum request timeout in seconds (optional, defaults to 10)
    * @return true if health check passes, false otherwise
    * @throws IllegalArgumentException if instanceAddress is null or empty
    */
    boolean performHealthCheck(String instanceAddress,
                            List<String> healthEndpoints = ['/health', '/healthz', '/'],
                            String protocol = 'http',
                            int connectTimeout = 5,
                            int maxTimeout = 10) {
        // Input validation
        if (!instanceAddress?.trim()) {
            throw new IllegalArgumentException('Instance address cannot be null or empty')
        }

        try {
            script.echo "Performing health check on ${instanceAddress}"

            // Try to reach the health endpoint

            for (endpoint in healthEndpoints) {
                String url = "${protocol}://${instanceAddress}${endpoint}"
                String result = script.sh(
                    script: "curl -f -s -o /dev/null -w '%{http_code}' " +
                            "--connect-timeout ${connectTimeout} " +
                            "--max-time ${maxTimeout} ${url}",
                    returnStdout: true
                ).trim()

                if (result == '200') {
                    script.echo "Health check passed for ${instanceAddress}${endpoint} - HTTP ${result}"
                    return true
                }
            }

            script.echo "Health check failed for ${instanceAddress} - no healthy endpoints found"
            return false
        } catch (IOException | InterruptedException e) {
            script.echo "Error performing health check: ${e.message}"
            return false
        }
    }

    /**
    * Tags a Docker image for a specific environment.
    *
    * Note: Requires Docker to be installed and running on the executing system.
    *
    * @param imageId The ID or name of the image to tag (required)
    * @param environment The target environment: 'development', 'staging', 'production', or 'test' (required)
    * @return true if tagging is successful, false otherwise
    * @throws IllegalArgumentException if parameters are invalid
    */
    boolean tagImageForEnvironment(String imageId, String environment) {
        // Input validation
        if (!imageId?.trim()) {
            throw new IllegalArgumentException('Image ID cannot be null or empty')
        }
        if (!environment?.trim()) {
            throw new IllegalArgumentException('Environment cannot be null or empty')
        }

        // Validate environment parameter
        List<String> validEnvironments = ['development', 'staging', 'production', 'test']
        if (!validEnvironments.contains(environment)) {
            String validEnvList = validEnvironments.join(', ')
            throw new IllegalArgumentException("Invalid environment: ${environment}. Must be one of: ${validEnvList}")
        }

        // Extract base image name from imageId
        String imageName = imageId.contains(':') ? imageId.split(':')[0] : imageId
        imageName = imageName.contains('@') ? imageName.split('@')[0] : imageName

        try {
            script.echo "Tagging image ${imageId} for ${environment} environment"

            // Tag the image for the specified environment
            script.sh "docker tag ${imageId} ${imageName}:${environment}"
            script.sh "docker tag ${imageId} ${imageName}:${environment}-latest"

            script.echo "Successfully tagged image ${imageId} as ${imageName}:${environment} " +
                "and ${imageName}:${environment}-latest"
            return true
        } catch (IOException | InterruptedException e) {
            script.echo "Failed to tag image: ${e.message}"
            return false
        }
    }

}
