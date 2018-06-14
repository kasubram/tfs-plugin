package hudson.plugins.tfs.rm.ReleaseWebhookAction;

def f = namespace(lib.FormTagLib);

f.entry(title: _("Release Webhook"), field: "webhookName") {
    f.select();
}