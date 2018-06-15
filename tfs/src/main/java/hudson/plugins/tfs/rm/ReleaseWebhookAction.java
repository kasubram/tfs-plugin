package hudson.plugins.tfs.rm;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.plugins.tfs.JenkinsEventNotifier;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import java.io.IOException;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Implements ReleaseWebhook Post build action.
 * @author kasubram
 */
public class ReleaseWebhookAction extends Notifier implements Serializable {

    private static final Logger logger = Logger.getLogger(ReleaseWebhookAction.class.getName());
    private final List<ReleaseWebhook> releaseWebhooks = new ArrayList<ReleaseWebhook>();
    private final String apiVersion = "5.0-preview";

    @DataBoundConstructor
    public ReleaseWebhookAction(final List<ReleaseWebhook> releaseWebhooks) {
        this.releaseWebhooks.addAll(releaseWebhooks);
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    public List<ReleaseWebhook> getReleaseWebhooks() {
        return this.releaseWebhooks;
    }

    @Override
    public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) throws InterruptedException, IOException {
        logger.entering("ReleaseWebhookAction", "Perform");

        JSONObject json = new JSONObject();
        final String payload = JenkinsEventNotifier.getApiJson(build.getUrl());
        if (payload != null) {
            json = JSONObject.fromObject(payload);
        }

        json.put("name", build.getProject().getName());
        json.put("startedBy", getStartedBy(build));

        try {
            for (ReleaseWebhook webhook : releaseWebhooks) {
                sendJobCompletedEvent(json, webhook);
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            logger.log(Level.SEVERE, null, ex);
        }

        return true;
    }

    private void sendJobCompletedEvent(final JSONObject json, final ReleaseWebhook webhook) throws IOException, NoSuchAlgorithmException, InvalidKeyException {

        HttpClient client = HttpClientBuilder.create().build();
        final HttpPost request = new HttpPost(webhook.getPayloadUrl());
        final String payload = json.toString();

        request.addHeader("Content-Type", "application/json");
        request.addHeader("Accept", "application/json; api-version=" + apiVersion);

        if (!StringUtils.isEmpty(webhook.getSecret())) {
            String signature = JenkinsEventNotifier.getPayloadSignature(webhook.getSecret(), payload);
            request.addHeader("X-Jenkins-Signature", signature);
        }

        request.setEntity(new StringEntity(payload));
        final HttpResponse response = client.execute(request);
        final int statusCode = response.getStatusLine().getStatusCode();

        if (statusCode == HttpURLConnection.HTTP_OK) {
            logger.log(Level.INFO, "sent event payload successfully");
        } else {
            HttpEntity entity = response.getEntity();
            String content = EntityUtils.toString(entity);
            logger.log(Level.WARNING, "Cannot send the event to webhook. Content:" + content);
        }
    }

    /**
     * Implementation of DescriptorImpl.
     */
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @Override
        public boolean isApplicable(final Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "TFS/Team Services Release Webhook";
        }
    }

    private String getStartedBy(final AbstractBuild build) {
        final Cause.UserIdCause cause = (Cause.UserIdCause) build.getCause(Cause.UserIdCause.class);
        String startedBy = "";
        if (cause != null && cause.getUserId() != null) {
            startedBy = cause.getUserId();
        }

        return startedBy;
    }
}
