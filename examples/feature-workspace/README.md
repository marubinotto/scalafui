# Feature Workspace

One `Workspace` feature, registered once with `collectionSlot[Int]`. Add and
remove instances dynamically; each has its own counter and mutually exclusive
note views. Everything runs in one Runtime.

```text
App (FeatureProgram)
└─ workspaces: collectionSlot[Int](Workspace.feature)
   ├─ 1: Workspace
   ├─ 2: Workspace
   └─ … additional workspaces
      ├─ counter: Counter
      └─ editor: NoteEditor ↔ preview: NotePreview
```

## Run

From this directory, install dependencies with Yarn Classic and start Vite:

```sh
yarn
yarn dev
```

In a second terminal, from the repository root:

```sh
sbt '~exampleFeatureWorkspace/fastLinkJS'
```

Use the Node.js versions listed in the root README. To build for production,
run `yarn build` from this directory; the Vite plugin runs Scala.js full
optimization through sbt automatically. To run the example's tests:

```sh
sbt 'exampleFeatureWorkspace/test'
```

## Things to try

1. Start with two workspaces, then use **Add workspace** to create more.
   Count and write different notes in each; all instances keep independent state.
2. Switch a note from Edit to Preview and back. These two slots are exclusive;
   the counter coexists with either. The editor emits `TextChanged` outputs,
   and Workspace retains the text and supplies it to the next view as input.
   Preview displays plain text, including line breaks, without interpreting HTML.
3. Start a delayed increment, then reset the counter. Its value stays at zero
   when the old result arrives. The note and its current mode are preserved.
4. Start a delayed increment, then reset the whole workspace or close and reopen
   it before three seconds pass. The new workspace has a fresh counter, blank
   note and Edit mode. The old result cannot reach its descendants. The other
   workspace is unaffected.

5. Start auto +1 on several counters. Each adds one every second using the same
   local subscription ID, `tick`. Stop one: the others continue. Switching note
   views also leaves auto increment running.
6. Reset a counter or close/reset its workspace while auto increment is on.
   Its subscription stops; reopening starts with auto increment off. Start it
   again to create a fresh subscription, without affecting the other workspace.

7. Close a workspace and choose **Reopen**. Its card keeps the same ID but the
   feature starts a fresh visit, with a blank note and a zero counter.
8. Close a workspace and choose **Remove** to remove its card too. Add another:
   IDs increase rather than being reused. Other workspaces keep their state.
   Remove all cards and add a new one to start again.

**Closing or resetting stops subscriptions but does not cancel running commands.** Commands finish, but
FeatureSlots ignores results addressed to an earlier visit. Counter reset uses
`binding.init`, not a value-only reset; closing uses `binding.remove`, preserving
generation history. Existing state is never reset to `Slots.empty`.

## Code guide

- `Main.scala`: one collection slot for the composed Workspace, add/close/reopen/
  reset/remove controls, and the single browser Runtime. The parent retains card
  IDs and display order separately from the active collection instances.
- `Workspace.scala`: nested slots, an explicit exclusive policy, child output
  handling and composition with `withFeatures` before outer registration.
- `Counter.scala`: immediate updates, a three-second `Cmd`, and an optional
  one-second `Sub.every` declared by the Feature. Only one delayed increment per
  active counter is pending at a time. Feature composition scopes `tick` through
  both Counter and Workspace instances; Main needs no subscription routing.
- `NoteEditor.scala` / `NotePreview.scala`: output-driven editing and plain-text
  rendering. Slot registrations do not determine DOM placement; `view` does.

All contexts are `Unit`. There is no server, routing or persistent storage.
Refreshing the page starts over. Tests execute the actual delayed commands using
Cats Effect virtual time and deliver their nested addresses after lifecycle changes.
