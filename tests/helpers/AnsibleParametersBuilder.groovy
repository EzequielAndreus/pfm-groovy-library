/* groovylint-disable CompileStatic */
package helpers

/**
 * Builder for Ansible playbook parameters.
 * 
 * Provides a fluent API for constructing parameter maps for Ansible playbook execution.
 * Supports common parameters like environment, version, debug settings, retries, timeout,
 * hosts, and tags.
 */
class AnsibleParametersBuilder {

    private Map parameters = [:]

    /**
     * Creates a new builder instance
     * @return a new AnsibleParametersBuilder
     */
    static AnsibleParametersBuilder builder() {
        return new AnsibleParametersBuilder()
    }

    /**
     * Sets the environment parameter
     * @param env the environment name (e.g., 'staging', 'production')
     * @return this builder for method chaining
     */
    AnsibleParametersBuilder withEnvironment(String env) {
        parameters.env = env
        return this
    }

    /**
     * Sets the version parameter
     * @param version the application version
     * @return this builder for method chaining
     */
    AnsibleParametersBuilder withVersion(String version) {
        parameters.version = version
        return this
    }

    /**
     * Sets the debug parameter
     * @param debug whether to enable debug mode
     * @return this builder for method chaining
     */
    AnsibleParametersBuilder withDebug(boolean debug) {
        parameters.debug = debug
        return this
    }

    /**
     * Sets the retries parameter
     * @param retries number of retry attempts
     * @return this builder for method chaining
     */
    AnsibleParametersBuilder withRetries(int retries) {
        parameters.retries = retries
        return this
    }

    /**
     * Sets the timeout parameter
     * @param timeout timeout in seconds
     * @return this builder for method chaining
     */
    AnsibleParametersBuilder withTimeout(int timeout) {
        parameters.timeout = timeout
        return this
    }

    /**
     * Sets the hosts parameter
     * @param hosts target hosts pattern
     * @return this builder for method chaining
     */
    AnsibleParametersBuilder withHosts(String hosts) {
        parameters.hosts = hosts
        return this
    }

    /**
     * Sets the tags parameter
     * @param tags list of tags to execute
     * @return this builder for method chaining
     */
    AnsibleParametersBuilder withTags(List<String> tags) {
        parameters.tags = tags
        return this
    }

    /**
     * Builds and returns the parameter map
     * @return the constructed parameter map
     */
    Map build() {
        return parameters.clone()
    }

}
