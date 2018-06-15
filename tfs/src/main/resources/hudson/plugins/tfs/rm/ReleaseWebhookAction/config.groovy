package hudson.plugins.tfs.rm.ReleaseWebhookAction;

def f = namespace(lib.FormTagLib);

f.entry(title: _("Release Webhooks"),
            field: "releaseWebhooks") {

        f.repeatableProperty(field: "releaseWebhooks")
    }
