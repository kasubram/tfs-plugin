package hudson.plugins.tfs.model;

import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Queue;
import hudson.model.queue.ScheduleResult;
import hudson.plugins.tfs.CommitParameterAction;
import hudson.plugins.tfs.PullRequestParameterAction;
import hudson.plugins.tfs.TeamBuildEndpoint;
import jenkins.model.Jenkins;
import jenkins.util.TimeDuration;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BuildCommand extends AbstractCommand {

    private static final Action[] EMPTY_ACTION_ARRAY = new Action[0];
    protected static final String TEAM_BUILD_PREFIX = "_team-build_";
    protected static final int TEAM_BUILD_PREFIX_LENGTH = TEAM_BUILD_PREFIX.length();
    private static final String BUILD_SOURCE_BRANCH = "Build.SourceBranch";
    private static final String BUILD_REPOSITORY_PROVIDER = "Build.Repository.Provider";
    private static final String REFS_PULL_SLASH = "refs/pull/";
    private static final int REFS_PULL_SLASH_LENGTH = REFS_PULL_SLASH.length();
    private static final String BUILD_REPOSITORY_URI = "Build.Repository.Uri";
    private static final String SYSTEM_TEAM_PROJECT = "System.TeamProject";
    private static final String BUILD_SOURCE_VERSION = "Build.SourceVersion";
    private static final String BUILD_REQUESTED_FOR = "Build.RequestedFor";
    private static final String SYSTEM_TEAM_FOUNDATION_COLLECTION_URI = "System.TeamFoundationCollectionUri";

    public static class Factory implements AbstractCommand.Factory {
        @Override
        public AbstractCommand create() {
            return new BuildCommand();
        }

        @Override
        public String getSampleRequestPayload() {
            return "{\n" +
                    "    \"team-parameters\":\n" +
                    "    {\n" +
                    "        \"collectionUri\":\"https://fabrikam-fiber-inc.visualstudio.com\",\n" +
                    "        \"repoUri\":\"https://fabrikam-fiber-inc.visualstudio.com/Personal/_git/olivida.tfs-plugin\",\n" +
                    "        \"projectId\":\"Personal\",\n" +
                    "        \"repoId\":\"olivida.tfs-plugin\",\n" +
                    "        \"commit\":\"6a23fc7afec31f0a14bade6544bed4f16492e6d2\",\n" +
                    "        \"pushedBy\":\"olivida\"\n" +
                    "    }\n" +
                    "}";
        }
    }

    protected JSONObject innerPerform(final AbstractProject project, final TimeDuration delay, final List<Action> extraActions) {
        final JSONObject result = new JSONObject();

        final Jenkins jenkins = Jenkins.getInstance();
        final Queue queue = jenkins.getQueue();
        final Cause cause = new Cause.UserIdCause();
        final CauseAction causeAction = new CauseAction(cause);
        final List<Action> actions = new ArrayList<Action>();
        actions.add(causeAction);

        actions.addAll(extraActions);

        final Action[] actionArray = actions.toArray(EMPTY_ACTION_ARRAY);
        final ScheduleResult scheduleResult = queue.schedule2(project, delay.getTime(), actionArray);
        final Queue.Item item = scheduleResult.getItem();
        if (item != null) {
            result.put("created", jenkins.getRootUrl() + item.getUrl());
        }
        return result;
    }

    @Override
    public JSONObject perform(final AbstractProject project, final JSONObject requestPayload, final TimeDuration delay) {

        final List<Action> actions = new ArrayList<Action>();
        if (requestPayload.containsKey(TeamBuildEndpoint.TEAM_PARAMETERS)) {
            final JSONObject eventArgsJson = requestPayload.getJSONObject(TeamBuildEndpoint.TEAM_PARAMETERS);
            final CommitParameterAction action;
            // TODO: improve the payload detection!
            if (eventArgsJson.containsKey("pullRequestId")) {
                final PullRequestMergeCommitCreatedEventArgs args = PullRequestMergeCommitCreatedEventArgs.fromJsonObject(eventArgsJson);
                action = new PullRequestParameterAction(args);
            }
            else {
                final GitCodePushedEventArgs args = GitCodePushedEventArgs.fromJsonObject(eventArgsJson);
                action = new CommitParameterAction(args);
            }
            actions.add(action);
        }
        // TODO: detect if a job is parameterized and react appropriately

        return innerPerform(project, delay, actions);
    }

    @Override
    public JSONObject perform(final AbstractProject project, final StaplerRequest request, final TimeDuration delay) {

        final List<Action> actions = new ArrayList<Action>();

        final HashMap<String, String> teamParameters = new HashMap<String, String>();

        final Map<String, String[]> parameters = request.getParameterMap();
        for (final Map.Entry<String, String[]> entry : parameters.entrySet()) {
            final String paramName = entry.getKey();
            if (paramName.startsWith(TEAM_BUILD_PREFIX)) {
                final String teamParamName = paramName.substring(TEAM_BUILD_PREFIX_LENGTH);
                final String[] valueArray = entry.getValue();
                if (valueArray == null || valueArray.length != 1) {
                    throw new IllegalArgumentException(String.format("Expected exactly 1 value for parameter '%s'.", teamParamName));
                }
                teamParameters.put(teamParamName, valueArray[0]);
            }
            else {
                // TODO: implement when we add support for parameterized builds
            }
        }

        if (teamParameters.containsKey(BUILD_REPOSITORY_PROVIDER) && "TfGit".equalsIgnoreCase(teamParameters.get(BUILD_REPOSITORY_PROVIDER))) {
            final String collectionUriString = teamParameters.get(SYSTEM_TEAM_FOUNDATION_COLLECTION_URI);
            final URI collectionUri = URI.create(collectionUriString);
            final String repoUriString = teamParameters.get(BUILD_REPOSITORY_URI);
            final URI repoUri = URI.create(repoUriString);
            final String projectId = teamParameters.get(SYSTEM_TEAM_PROJECT);
            final String commit = teamParameters.get(BUILD_SOURCE_VERSION);
            final String pushedBy = teamParameters.get(BUILD_REQUESTED_FOR);
            final Integer pullRequestId = determinePullRequestId(teamParameters);
            final CommitParameterAction action;
            if (pullRequestId != null) {
                final PullRequestMergeCommitCreatedEventArgs args = new PullRequestMergeCommitCreatedEventArgs();
                args.collectionUri = collectionUri;
                args.repoUri = repoUri;
                args.projectId = projectId;
                args.commit = commit;
                args.pushedBy = pushedBy;
                args.pullRequestId = pullRequestId;
                args.iterationId = -1 /* TODO: the pull request iteration ID is missing! */;
                action = new PullRequestParameterAction(args);
            }
            else {
                final GitCodePushedEventArgs args = new GitCodePushedEventArgs();
                args.collectionUri = collectionUri;
                args.repoUri = repoUri;
                args.projectId = projectId;
                args.commit = commit;
                args.pushedBy = pushedBy;
                action = new CommitParameterAction(args);
            }
            actions.add(action);
        }

        return innerPerform(project, delay, actions);
    }

    static Integer determinePullRequestId(final HashMap<String, String> teamParameters) {
        Integer pullRequestId = null;
        if (teamParameters.containsKey(BUILD_SOURCE_BRANCH)) {
            final String sourceBranch = teamParameters.get(BUILD_SOURCE_BRANCH);
            if (sourceBranch.startsWith(REFS_PULL_SLASH)) {
                final String idSlashMerge = sourceBranch.substring(REFS_PULL_SLASH_LENGTH);
                final int nextSlash = idSlashMerge.indexOf('/');
                if (nextSlash > 0) {
                    final String pullRequestIdString = idSlashMerge.substring(0, nextSlash);
                    pullRequestId = Integer.valueOf(pullRequestIdString, 10);
                }
            }
        }
        return pullRequestId;
    }
}
