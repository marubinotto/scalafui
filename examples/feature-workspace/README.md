# Feature Workspace

One `Workspace` feature, registered twice. Each workspace contains its own counter
and a pair of mutually exclusive note views. Everything runs in one Runtime.

```text
App (FeatureProgram)
├─ left: Workspace.feature
│  ├─ counter: Counter
│  └─ editor: NoteEditor ↔ preview: NotePreview
└─ right: Workspace.feature
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

1. Increment the left counter and write a note on the right. The two registrations
   of the same feature have independent state.
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

**Closing or resetting does not cancel running work.** Commands finish, but
FeatureSlots ignores results addressed to an earlier visit. Counter reset uses
`binding.init`, not a value-only reset; closing uses `binding.remove`, preserving
slot generation history. Existing state is never reset to `Slots.empty`.

## Code guide

- `Main.scala`: two slots for the same composed Workspace, independent coexistence,
  open/close/reset controls, and the single browser Runtime.
- `Workspace.scala`: nested slots, an explicit exclusive policy, child output
  handling and composition with `withFeatures` before outer registration.
- `Counter.scala`: immediate updates and a three-second `Cmd`. Only one delayed
  increment per active counter is pending at a time.
- `NoteEditor.scala` / `NotePreview.scala`: output-driven editing and plain-text
  rendering. Slot registrations do not determine DOM placement; `view` does.

All contexts are `Unit`. There is no server, routing or persistent storage.
Refreshing the page starts over. Tests execute the actual delayed commands using
Cats Effect virtual time and deliver their nested addresses after lifecycle changes.
