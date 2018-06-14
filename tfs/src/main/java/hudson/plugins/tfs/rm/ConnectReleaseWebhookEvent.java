package hudson.plugins.tfs.rm;

import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.plugins.tfs.TeamPluginGlobalConfig;
import hudson.plugins.tfs.model.AbstractHookEvent;
import hudson.plugins.tfs.model.servicehooks.Event;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.InvalidParameterException;
import java.util.HashMap;
import java.util.List;
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
                 + "    \"webhookName\": \"webhook name\"\n"
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
            throw new UnsupportedOperationException("Webhook operation " + parameters.getOperationType() + " is not supported");
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
        String webhookName = resource.getWebhookName();
        AbstractProject project = validateAndGetJenkinsProject(resource);

        final TeamPluginGlobalConfig config = TeamPluginGlobalConfig.get();
        if (config == null) {
            throw new InternalError("Cannot load TFS global configuration");
        }

        boolean webhookExists = false;
        for (ReleaseWebhook webhook : config.getReleaseWebhookConfigurations()) {
            if (webhook.getPayloadUrl().equalsIgnoreCase(payloadUrl)) {
                webhookName = webhook.getWebhookName();
                webhookExists = true;
                break;
            }
        }

        if (!webhookExists) {
            ReleaseWebhook webhook = new ReleaseWebhook(webhookName, payloadUrl, secret);
            config.getReleaseWebhookConfigurations().add(webhook);
            config.save();
        }

        boolean webhookActionExists = false;
        DescribableList<Publisher, Descriptor<Publisher>> publishersList = project.getPublishersList();
        for (Publisher publisher : publishersList) {
            if (publisher instanceof ReleaseWebhookAction) {
                ReleaseWebhookAction action = (ReleaseWebhookAction) publisher;
                if (action.getWebhookName().equalsIgnoreCase(webhookName)) {
                    webhookActionExists = true;
                    break;
                }
            }
        }

        if (!webhookActionExists) {
            publishersList.add(new ReleaseWebhookAction(webhookName));

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

        String webhookName = resource.getWebhookName();
        AbstractProject project = validateAndGetJenkinsProject(resource);

        boolean webhookActionFound = false;
        DescribableList<Publisher, Descriptor<Publisher>> publishersList = project.getPublishersList();
        for (Publisher publisher : publishersList) {
            if (publisher instanceof ReleaseWebhookAction) {
                ReleaseWebhookAction action = (ReleaseWebhookAction) publisher;
                if (action.getWebhookName().equalsIgnoreCase(webhookName)) {
                    webhookActionFound = true;
                    publishersList.remove(publisher);
                    break;
                }
            }
        }

        if (webhookActionFound) {
            try {
                project.save();
                deleteGlobalWebhookConfigIfRequired(webhookName);
            } catch (IOException ex) {
                throw new InternalError("Cannot save project " + resource.getProjectName(), ex);
            }
        }
    }

    private void deleteGlobalWebhookConfigIfRequired(final String webhookName) {
        final TeamPluginGlobalConfig config = TeamPluginGlobalConfig.get();
        if (config == null) {
            throw new InternalError("Cannot load TFS global configuration");
        }

        // looking up each project
        for (final Item item : Jenkins.getActiveInstance().getAllItems()) {
            if (item instanceof AbstractProject) {
                AbstractProject project = (AbstractProject) item;

                // Get all post build action
                DescribableList<Publisher, Descriptor<Publisher>> publishersList = project.getPublishersList();

                for (Publisher publisher : publishersList) {

                    // looking up each ReleaseWebhookAction
                    if (publisher instanceof ReleaseWebhookAction) {
                        ReleaseWebhookAction action = (ReleaseWebhookAction) publisher;
                        if (action.getWebhookName().equalsIgnoreCase(webhookName)) {
                            // some webhook action already reference this webhook config. Cannot delete
                            return;
                        }
                    }
                }
            }
        }

        // No project's post build action refers to it. Deleting webhook config
        List<ReleaseWebhook> webhooks = config.getReleaseWebhookConfigurations();
        for (ReleaseWebhook webhook : webhooks) {
            if (webhook.getWebhookName().equalsIgnoreCase(webhookName)) {
                webhooks.remove(webhook);

                config.save();
                break;
            }
        }
    }

    private JSONObject listReleaseWebhook(final ReleaseWebhookResource resource) {
        AbstractProject project = validateAndGetJenkinsProject(resource);

        HashMap<String, String> existingWebhooksConfig = new HashMap<String, String>();
        final TeamPluginGlobalConfig config = TeamPluginGlobalConfig.get();
        if (config == null) {
            throw new InternalError("Cannot load TFS global configuration");
        }

        for (ReleaseWebhook webhook : config.getReleaseWebhookConfigurations()) {
            existingWebhooksConfig.put(webhook.getWebhookName(), webhook.getPayloadUrl());
        }

        HashMap<String, Boolean> webhooksMap = new HashMap<String, Boolean>();

        DescribableList<Publisher, Descriptor<Publisher>> publishersList = project.getPublishersList();
        JSONArray webhooks = new JSONArray();
        for (Publisher publisher : publishersList) {
            if (publisher instanceof ReleaseWebhookAction) {
                ReleaseWebhookAction action = (ReleaseWebhookAction) publisher;

                // possibly we have added more than one post build action referring to same webhook.
                // if we have't already added to return list and we can find the webhookName in the global config, then add it to return list
                if (!webhooksMap.containsKey(action.getWebhookName()) && existingWebhooksConfig.containsKey(action.getWebhookName())) {
                    JSONObject webhook = new JSONObject();
                    webhook.put("WebhookName", action.getWebhookName());
                    webhook.put("PayloadUrl", existingWebhooksConfig.get(action.getWebhookName()));

                    webhooks.add(webhook);
                }
            }
        }

        JSONObject result = new JSONObject();
        result.put("ReleaseWebhooks", webhooks);

        return result;
    }
}
