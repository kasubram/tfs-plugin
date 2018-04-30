package hudson.plugins.tfs.TeamCollectionConfiguration;

def f = namespace(lib.FormTagLib);
def c = namespace(lib.CredentialsTagLib);

f.entry(title: _("Collection URL"), field: "collectionUrl") {
    f.textbox()
}

f.entry(title: _("Credentials"), field: "credentialsId",
        description: "Depending on the integration features used, the user account or personal access token may need code_read, code_status and/or work_write permissions") {
    c.select()
}

f.optionalBlock(
    title: _("Enable sending job completed event to this collection"), 
    checked: instance?.connectionParameters?.sendJobCompletionEvents,
    inline: true,
    name: "enableSendingJobCompletionEvent") {
    
    f.entry(
        title: _("Secret"), 
        description: _("Secret used to make sure the payload sent to TFS is not tampered"), 
        field: "connectionSignature") {
        f.password()
    }

    f.entry(
        title: _("Server Key"), 
        description: _("Unique name to identity this jenkins server. if this value is not provided host name of the jenkins server will be taken"),
        field: "serverKey") {
        f.textbox()
    }
}

f.block() {
    f.validateButton(
            title: _("Test connection"),
            progress: _("Testing..."),
            method: "testCredentials",
            with: "collectionUrl,credentialsId"
    )
}

f.entry {
    div(align: "right") {
        f.repeatableDeleteButton()
    }
}
