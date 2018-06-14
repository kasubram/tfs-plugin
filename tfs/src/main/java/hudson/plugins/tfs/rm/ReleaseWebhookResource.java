package hudson.plugins.tfs.rm;

/**
 * Implements ReleaseWebhookResource. Model to represent webhook register event.
 * @author kasubram
 */
public class ReleaseWebhookResource {
    private String operationType;
    private String webhookName;
    private String projectName;
    private String payloadUrl;
    private String secret;

    public String getOperationType() {
        return this.operationType;
    }

    public void setOperationType(final String operationType) {
        this.operationType = operationType;
    }

    public String getWebhookName() {
        return this.webhookName;
    }

    public void setWebhookName(final String webhookName) {
        this.webhookName = webhookName;
    }

    public String getProjectName() {
        return this.projectName;
    }

    public void setProjectName(final String jobName) {
        this.projectName = jobName;
    }

    public String getPayloadUrl() {
        return this.payloadUrl;
    }

    public void setPayloadUrl(final String payloadUrl) {
        this.payloadUrl = payloadUrl;
    }

    public String getSecret() {
        return this.secret;
    }

    public void setSecret(final String secret) {
        this.secret = secret;
    }
}
