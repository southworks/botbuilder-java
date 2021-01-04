// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
package com.microsoft.bot.dialogs;

import com.microsoft.bot.builder.NullBotTelemetryClient;
import com.microsoft.bot.builder.Severity;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;

/**
 * A container for a set of Dialogs.
 */
public abstract class DialogContainer extends Dialog
{
    private DialogSet dialogs = new DialogSet();

    /**
     * Initializes a new instance of the {@link DialogContainer{T}} class.
     * @param dialogId The ID to assign to the dialog.
     */
    protected DialogContainer(@Nullable String dialogId) {
        super(dialogId);
    }

    /**
     * Gets the DialogSet.
     * @return The containers Dialog Set.
     */
    public final DialogSet getDialogs() {
        return dialogs;
    }

    /**
     * Sets the DialogSet.
     * @param value The containers Dialog Set.
     */
    public final void setDialogs(DialogSet value) {
        dialogs = value;
    }

    /**
     * Gets the IBotTelemetryClient.
     * @return The IBotTelemetryClient
     */
    @Override
    public IBotTelemetryClient getTelemetryClient() {
        return super.getTelemetryClient();
    }

    /**
     * Sets the IBotTelemetryClient.
     * @param value The new value for IBotTelemetryClient
     */
    @Override
    public void setTelemetryClient(IBotTelemetryClient value) {
        super.setTelemetryClient((value != null) ? value : (IBotTelemetryClient) new NullBotTelemetryClient());
        dialogs.setTelemetryClient(super.getTelemetryClient());
    }

    /**
    * Creates an inner dialog context for the containers active child.
    * @param dc Parents dialog context.
    * @return A new dialog context for the active child.
    */
    public abstract DialogContext createChildContext(DialogContext dc);

    /**
    * Finds a child dialog that was previously added to the container.
    * @param dialogId The ID of the dialog to lookup.
    * @return The Dialog if found; otherwise null.
    */
    public Dialog findDialog(String dialogId) {
        return this.dialogs.Find(dialogId);
    }

    /**
    * Called when an event has been raised, using `DialogContext.emitEvent()`,
    * by either the current dialog or a dialog that the current dialog started.
    * @param dc The dialog context for the current turn of conversation.
    * @param e The event being raised.
    * @return True if the event is handled by the current dialog and bubbling should stop.
    */
    @Override
    public CompletableFuture<Boolean> onDialogEvent(DialogContext dc, DialogEvent e) {
        return super.onDialogEvent(dc, e).thenApply(handled -> {
            // Trace unhandled "versionChanged" events.
            if (!handled && e.Name == DialogEvents.VersionChanged) {
                String traceMessage =
                    String.format("Unhandled dialog event: %s. Active Dialog: %s", e.Name, dc.ActiveDialog.Id);

                dc.Dialogs.TelemetryClient.TrackTrace(traceMessage, Severity.WARNING, null);

                dc.Context.TraceActivityAsync(traceMessage);
            }

            return handled;
        });
    }

    /**
    * GetInternalVersion - Returns internal version identifier for this container.
    * DialogContainers detect changes of all sub-components in the container and map that to an DialogChanged event.
    * Because they do this, DialogContainers "hide" the internal changes and just have the .id. This isolates changes
    * to the container level unless a container doesn't handle it.  To support this DialogContainers define a
    * protected virtual method GetInternalVersion() which computes if this dialog or child dialogs have changed
    * which is then examined via calls to CheckForVersionChangeAsync().
    * @return version which represents the change of the internals of this container.
    */
    protected String getInternalVersion() {
        return this.Dialogs.GetVersion();
    }

    /**
    * CheckForVersionChangeAsync.
    * Checks to see if a containers child dialogs have changed since the current dialog instance was started.
    * This should be called at the start of `beginDialog()`, `continueDialog()`, and `resumeDialog()`.
    * @param dc dialog context.
    * @return version which represents the change of the internals of this container.
    */
    protected CompletableFuture<Void> checkForVersionChangeAsync(DialogContext dc) {
        String current = dc.ActiveDialog.Version;
        dc.ActiveDialog.Version = this.getInternalVersion();

        // Check for change of previously stored hash
        if (current != null && current != dc.ActiveDialog.Version) {
            dc.EmitEvent(DialogEvents.VersionChanged, this.getId(), true, false);
        }
    }
}
