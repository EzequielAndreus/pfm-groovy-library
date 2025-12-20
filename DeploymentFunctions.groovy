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

    String workDir = "/tmp/build-${System.currentTimeMillis()}"

    try {
        // Clone the repository
        sh "git clone ${repositoryUrl} ${workDir}"

        // Checkout the specified branch
        sh "cd ${workDir} && git checkout ${branch}"

        // Get the short commit hash
        String commitHash = sh(
            script: "cd ${workDir} && git rev-parse --short HEAD",
            returnStdout: true
        ).trim()

        // Build the Docker image
        sh "cd ${workDir} && docker build -t ${imageName}:${commitHash} ."

        // publish image in Docker Hub
        sh "docker push ${imageName}:${commitHash}"

        echo "Successfully built, tagged, and published image as ${imageName}:${commitHash}"
        return commitHash
    } finally {
        // Clean up the working directory
        sh "rm -rf ${workDir}"
    }
}
