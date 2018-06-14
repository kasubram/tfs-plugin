package hudson.plugins.tfs.rm;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import java.net.URI;
import java.net.URISyntaxException;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.logging.Logger;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.QueryParameter;

/**
 *
 * @author kasubram
 */
public class ReleaseWebhook extends AbstractDescribableImpl<ReleaseWebhook> {
    private static final Logger LOGGER = Logger.getLogger(ReleaseWebhook.class.getName());

    private final String webhookName;
    private final String payloadUrl;
    private final String secret;

    @DataBoundConstructor
    public ReleaseWebhook(final String webhookName, final String payloadUrl, final String secret) {
        this.webhookName = webhookName;
        this.payloadUrl = payloadUrl;
        this.secret = secret;
    }

    public ReleaseWebhook(final String webhookName, final String payloadUrl) {
        this.webhookName = webhookName;
        this.payloadUrl = payloadUrl;
        this.secret = StringUtils.EMPTY;
    }

    public String getWebhookName() {
        return this.webhookName;
    }

    public String getPayloadUrl() {
        return this.payloadUrl;
    }

    public String getSecret() {
        return this.secret;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * DescriptorImpl.
     */
    @Extension
    public static class DescriptorImpl extends Descriptor<ReleaseWebhook> {

        @Override
        public String getDisplayName() {
            return "Release Webhook";
        }

        /**
         * Validates Payload URL.
         * @param value
         * @return
         */
        @SuppressWarnings("unused")
        public FormValidation doCheckPayloadUrl(@QueryParameter final String value) {
            if (StringUtils.isBlank(value)) {
                return FormValidation.warning("Please provide a value");
            }

            final URI uri;
            try {
                uri = new URI(value);
            } catch (final URISyntaxException e) {
                return FormValidation.error("Malformed Payload URL (%s)", e.getMessage());
            }

            final String hostName = uri.getHost();
            if (StringUtils.isBlank(hostName)) {
                return FormValidation.error("Please provide a host name");
            }

            return FormValidation.ok();
        }

        /**
         * Validates Webhook Name.
         * @param value
         * @return
         */
        @SuppressWarnings("unused")
        public FormValidation doCheckWebhookName(@QueryParameter final String value) {
            if (StringUtils.isBlank(value)) {
                return FormValidation.warning("Please provide a value");
            }

            return FormValidation.ok();
        }
    }
}
