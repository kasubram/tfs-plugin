package hudson.plugins.tfs.rm;

import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.plugins.tfs.model.AbstractHookEvent;
import hudson.plugins.tfs.model.servicehooks.Event;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;

/**
 * This HookEvent is for to create/delete Release Webhook.
*/
public class ConnectReleaseWebhookEvent extends AbstractHookEvent {

    private static final String CREATE_EVENTNAME = "rmwebhook-create";
    private static final String REMOVE_ENENTNAME = "rmwebhook-remove";
    private static final String LIST_EVENTNAME = "rmwebhook-list";

    /**
     * Factory to create ConnectReleaseWebhookEvent.
     */
    public static class Factory implements AbstractHookEvent.Factory {
        @Override
        public ConnectReleaseWebhookEvent create() {
            return new ConnectReleaseWebhookEvent();
        }

        @Override
        public String getSampleRequestPayload() {
            return "{\n"
                 + "    \"operation\": create\n"
                 + "    \"jobName\": \"Job name\"\n"
                 + "    \"payloadUrl\": \"https://xplatalm.vsrm.visualstudio.com/_apis/Release/receiveExternalEvent/wenhookId\"\n"
                 + "    \"secret\": \"secret\"\n"
                 + "}";
        }
    }

    @Override
    public JSONObject perform(final ObjectMapper mapper, final Event event, final String message, final String detailedMessage) {
        final Object resource = event.getResource();
        final ReleaseWebhookResource parameters = mapper.convertValue(resource, ReleaseWebhookResource.class);

        if (event.getEventType().equalsIgnoreCase(CREATE_EVENTNAME)) {
            createReleaseWebhook(parameters);
        } else if (event.getEventType().equalsIgnoreCase(REMOVE_ENENTNAME)) {
            deleteReleaseWebhook(parameters);
        } else if (event.getEventType().equalsIgnoreCase(LIST_EVENTNAME)) {
            return listReleaseWebhook(parameters);
        } else {
            throw new UnsupportedOperationException("Webhook operation " + event.getEventType() + " is not supported");
        }

        return JSONObject.fromObject(event);
    }

    private String validateAndGetPayloadUrl(final ReleaseWebhookResource parameters) {
        String payloadUrl = parameters.getPayloadUrl();

        if (StringUtils.isBlank(payloadUrl)) {
            throw new InvalidParameterException("pyaloadUrl is empty");
        }

        final URI uri;
        try {
            uri = new URI(payloadUrl);
        } catch (final URISyntaxException e) {
            throw new InvalidParameterException("Malformed Payload URL " + e.getMessage());
        }

        final String hostName = uri.getHost();
        if (StringUtils.isBlank(hostName)) {
            throw new InvalidParameterException("Malformed Payload URL");
        }

        return StringUtils.stripEnd(StringUtils.trim(payloadUrl), "/");
    }

    private AbstractProject validateAndGetJenkinsProject(final ReleaseWebhookResource resource) {
        if (StringUtils.isEmpty(resource.getProjectName())) {
            throw new InvalidParameterException("Project name is empty");
        }

        for (final Item project : Jenkins.getActiveInstance().getAllItems()) {
            if (project instanceof AbstractProject && project.getName().equalsIgnoreCase(resource.getProjectName())) {
                return (AbstractProject) project;
            }
        }

        throw new InvalidParameterException("Cannot find Jenkins Job with the name " + resource.getProjectName());
    }

    private void createReleaseWebhook(final ReleaseWebhookResource resource) {
        if (resource == null) {
            throw new InvalidParameterException("event parameter is null");
        }

        String payloadUrl = validateAndGetPayloadUrl(resource);
        String secret = resource.getSecret();
        AbstractProject project = validateAndGetJenkinsProject(resource);

        boolean webhookExists = false;
        ReleaseWebhookAction webhookAction = null;
        DescribableList<Publisher, Descriptor<Publisher>> publishersList = project.getPublishersList();
        for (Publisher publisher : publishersList) {
            if (publisher instanceof ReleaseWebhookAction) {
                webhookAction = (ReleaseWebhookAction) publisher;

                for (ReleaseWebhook webhook : webhookAction.getReleaseWebhooks()) {
                    if (webhook.getPayloadUrl().equalsIgnoreCase(payloadUrl)) {
                        webhookExists = true;
                        break;
                    }
                }
            }
        }

        if (!webhookExists) {

            ReleaseWebhook webhook = new ReleaseWebhook(payloadUrl, secret);

            if (webhookAction != null) {
                webhookAction.getReleaseWebhooks().add(webhook);
            } else {
                ArrayList<ReleaseWebhook> webhooks = new ArrayList<ReleaseWebhook>();
                webhooks.add(webhook);
                publishersList.add(new ReleaseWebhookAction(webhooks));
            }

            try {
                project.save();
            } catch (IOException ex) {
                throw new InternalError("cannot update the project " + resource.getProjectName(), ex);
            }
        }
    }

    private void deleteReleaseWebhook(final ReleaseWebhookResource resource) {
        if (resource == null) {
            throw new InvalidParameterException("event parameter is null");
        }

        AbstractProject project = validateAndGetJenkinsProject(resource);
        String payloadUrl = validateAndGetPayloadUrl(resource);

        boolean webhookActionFound = false;
        ReleaseWebhookAction webhookAction = null;

        DescribableList<Publisher, Descriptor<Publisher>> publishersList = project.getPublishersList();
        for (Publisher publisher : publishersList) {
            if (publisher instanceof ReleaseWebhookAction) {
                webhookAction = (ReleaseWebhookAction) publisher;

                for (ReleaseWebhook webhook : webhookAction.getReleaseWebhooks()) {
                    if (webhook.getPayloadUrl().equalsIgnoreCase(payloadUrl)) {
                        webhookActionFound = true;
                        webhookAction.getReleaseWebhooks().remove(webhook);
                        break;
                    }
                }
            }
        }

        if (webhookActionFound) {

            if (webhookAction != null && webhookAction.getReleaseWebhooks().isEmpty()) {
                publishersList.remove(webhookAction);
            }

            try {
                project.save();
            } catch (IOException ex) {
                throw new InternalError("Cannot save project " + resource.getProjectName(), ex);
            }
        }
    }

    private JSONObject listReleaseWebhook(final ReleaseWebhookResource resource) {
        AbstractProject project = validateAndGetJenkinsProject(resource);

        JSONArray webhooks = new JSONArray();
        DescribableList<Publisher, Descriptor<Publisher>> publishersList = project.getPublishersList();
        for (Publisher publisher : publishersList) {
            if (publisher instanceof ReleaseWebhookAction) {
                ReleaseWebhookAction action = (ReleaseWebhookAction) publisher;

                for (ReleaseWebhook releaseWebhook : action.getReleaseWebhooks()) {
                    JSONObject webhook = new JSONObject();
                    webhook.put("PayloadUrl", releaseWebhook.getPayloadUrl());

                    webhooks.add(webhook);
                }
            }
        }

        JSONObject result = new JSONObject();
        result.put("ReleaseWebhooks", webhooks);

        return result;
    }
}
