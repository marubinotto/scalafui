package featureworkspace

import cats.effect.IO
import cats.effect.testkit.TestControl
import cats.effect.unsafe.implicits.global
import marubinotto.fui.*
import org.scalajs.dom.URL
import org.scalatest.funsuite.AsyncFunSuite
import scala.concurrent.duration.*

class WorkspaceSpec extends AsyncFunSuite {
  override implicit def executionContext: scala.concurrent.ExecutionContext =
    org.scalajs.macrotaskexecutor.MacrotaskExecutor

  private val left = Main.Side.Left
  private val right = Main.Side.Right

  private def initial: Main.Model = Main.init(new URL("https://example.test/")).model
  private def workspace(model: Main.Model, side: Main.Side = left): Workspace.Model =
    Main.slotFor(side).model(model.workspaces).get
  private def counter(model: Main.Model, side: Main.Side = left): Counter.Model =
    Workspace.Slots.counter.model(workspace(model, side).children).get
  private def step(model: Main.Model, msg: Main.Msg): Main.Model = Main.program.update(msg, model)._1

  private def workspaceEvent(model: Main.Model, side: Main.Side, msg: Workspace.Msg): Main.Msg =
    Main.Msg.WorkspaceEvent(Main.slotFor(side).event(model.workspaces, msg).get)

  private def sendWorkspace(model: Main.Model, msg: Workspace.Msg, side: Main.Side = left): Main.Model =
    step(model, workspaceEvent(model, side, msg))

  private def sendCounter(model: Main.Model, msg: Counter.Msg, side: Main.Side = left): (Main.Model, Cmd[Main.Msg]) = {
    val child = Workspace.Slots.counter.event(workspace(model, side).children, msg).get
    Main.program.update(workspaceEvent(model, side, Workspace.Msg.ChildEvent(child)), model)
  }

  private def edit(model: Main.Model, text: String, side: Main.Side = left): Main.Model = {
    val child = Workspace.Slots.editor.event(workspace(model, side).children, NoteEditor.Msg.Changed(text)).get
    sendWorkspace(model, Workspace.Msg.ChildEvent(child), side)
  }

  private def emissions(cmd: Cmd[Main.Msg]): IO[List[Main.Msg]] = cmd match {
    case Cmd.One(io, _) => io.map(_.toList)
    case Cmd.Batch(cmds*) =>
      cmds.foldLeft(IO.pure(List.empty[Main.Msg])) { (acc, one) =>
        acc.flatMap(messages => one.io.map(value => messages ++ value.toList))
      }
  }

  // Execute the real 3-second command with virtual time, changing the parent
  // state after one second and then delivering its captured nested address.
  private def finishAfter(
      cmd: Cmd[Main.Msg]
  )(interim: => Main.Model): IO[Main.Model] = TestControl.executeEmbed {
    for {
      fiber <- emissions(cmd).start
      _ <- IO.sleep(1.second)
      changed <- IO(interim)
      messages <- fiber.joinWithNever
    } yield messages.foldLeft(changed)(step)
  }

  test("the same Workspace feature has independent counters and notes in two slots") {
    val counted = sendCounter(initial, Counter.Msg.Increment)._1
    val edited = edit(counted, "Right-hand note", right)
    assert(counter(edited).value == 1)
    assert(counter(edited, right).value == 0)
    assert(workspace(edited).note.isEmpty)
    assert(workspace(edited, right).note == "Right-hand note")
    assert(Workspace.Slots.editor.model(workspace(edited, right).children).contains("Right-hand note"))
  }

  test("editor and preview are exclusive while the counter and parent note survive switches") {
    val counted = sendCounter(initial, Counter.Msg.Decrement)._1
    val edited = edit(counted, "<b>Plain text</b>\nSecond line")
    val preview = sendWorkspace(edited, Workspace.Msg.Preview)
    assert(Workspace.Slots.editor.model(workspace(preview).children).isEmpty)
    assert(Workspace.Slots.preview.model(workspace(preview).children).contains("<b>Plain text</b>\nSecond line"))
    assert(counter(preview).value == -1)
    val restored = sendWorkspace(preview, Workspace.Msg.Edit)
    assert(Workspace.Slots.preview.model(workspace(restored).children).isEmpty)
    assert(Workspace.Slots.editor.model(workspace(restored).children).contains(workspace(restored).note))
    assert(counter(restored).value == -1)
  }

  test("counter reset preserves the note and preview mode") {
    val counted = sendCounter(initial, Counter.Msg.Increment)._1
    val preview = sendWorkspace(edit(counted, "Keep me"), Workspace.Msg.Preview)
    val reset = sendWorkspace(preview, Workspace.Msg.ResetCounter)
    assert(counter(reset) == Counter.Model())
    assert(workspace(reset).note == "Keep me")
    assert(Workspace.Slots.preview.model(workspace(reset).children).contains("Keep me"))
  }

  test("workspace reset and close/reopen reset descendants without affecting the other workspace") {
    val leftEdited = sendWorkspace(edit(sendCounter(initial, Counter.Msg.Increment)._1, "Left"), Workspace.Msg.Preview)
    val bothEdited = edit(sendCounter(leftEdited, Counter.Msg.Increment, right)._1, "Right", right)
    def checkFresh(model: Main.Model): Unit = {
      assert(counter(model) == Counter.Model())
      assert(workspace(model).note.isEmpty)
      assert(Workspace.Slots.editor.model(workspace(model).children).contains(""))
      assert(Workspace.Slots.preview.model(workspace(model).children).isEmpty)
      assert(counter(model, right).value == 1)
      assert(workspace(model, right).note == "Right")
    }
    checkFresh(step(bothEdited, Main.Msg.Reset(left)))
    val closed = step(bothEdited, Main.Msg.Close(left))
    assert(Main.Slots.left.model(closed.workspaces).isEmpty)
    checkFresh(step(closed, Main.Msg.Open(left)))
    succeed
  }

  test("a delayed increment runs once, rejects duplicate scheduling and retains immediate edits") {
    val (pending, cmd) = sendCounter(initial, Counter.Msg.AddLater)
    assert(counter(pending).pending)
    val (duplicate, duplicateCmd) = sendCounter(pending, Counter.Msg.AddLater)
    assert(duplicateCmd == Cmd.none)
    val immediate = sendCounter(duplicate, Counter.Msg.Increment)._1
    finishAfter(cmd)(immediate).map { result =>
      assert(counter(result) == Counter.Model(value = 2))
      assert(counter(result, right) == Counter.Model())
    }.unsafeToFuture()
  }

  test("delayed results survive note mode switches") {
    val (pending, cmd) = sendCounter(edit(initial, "Still here"), Counter.Msg.AddLater)
    finishAfter(cmd)(sendWorkspace(pending, Workspace.Msg.Preview)).map { result =>
      assert(counter(result) == Counter.Model(value = 1))
      assert(Workspace.Slots.preview.model(workspace(result).children).contains("Still here"))
    }.unsafeToFuture()
  }

  test("counter reset ignores an old result even when the replacement has another pending increment") {
    val (pending, oldCmd) = sendCounter(initial, Counter.Msg.AddLater)
    val reset = sendWorkspace(pending, Workspace.Msg.ResetCounter)
    val (newPending, newCmd) = sendCounter(reset, Counter.Msg.AddLater)
    finishAfter(oldCmd)(newPending).flatMap { ignored =>
      assert(counter(ignored) == Counter.Model(pending = true))
      finishAfter(newCmd)(ignored)
    }.map { result => assert(counter(result) == Counter.Model(value = 1)) }.unsafeToFuture()
  }

  for (operation <- List("reset", "close", "reopen")) {
    test(s"workspace $operation ignores a delayed result addressed to the previous ancestor") {
      val (leftPending, oldCmd) = sendCounter(initial, Counter.Msg.AddLater)
      val (bothPending, rightCmd) = sendCounter(leftPending, Counter.Msg.AddLater, right)
      finishAfter(oldCmd) {
        operation match {
          case "reset" => step(bothPending, Main.Msg.Reset(left))
          case "close" => step(bothPending, Main.Msg.Close(left))
          case _ => step(step(bothPending, Main.Msg.Close(left)), Main.Msg.Open(left))
        }
      }.flatMap { result =>
        if (operation == "close") assert(Main.Slots.left.model(result.workspaces).isEmpty)
        else assert(counter(result) == Counter.Model())
        assert(counter(result, right) == Counter.Model(pending = true))
        finishAfter(rightCmd)(result)
      }.map { result => assert(counter(result, right) == Counter.Model(value = 1)) }.unsafeToFuture()
    }
  }
}
