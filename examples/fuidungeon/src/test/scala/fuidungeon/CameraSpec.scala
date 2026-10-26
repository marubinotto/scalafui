package fuidungeon

import org.scalatest.funsuite.AnyFunSuite
import Game.*

class CameraSpec extends AnyFunSuite {
  private def board(width: Int, height: Int): Board = Board(Vector.tabulate(height, width) { (y, x) =>
    if (x == 0 && y == 0) 'S' else if (x == width - 1 && y == height - 1) 'E' else '.'
  }.map(_.mkString))

  test("both viewport sizes track the center and clamp at every edge") {
    val map = board(40, 30)
    for (visible <- Seq(15, 9)) {
      val half = visible / 2
      assert(Camera.offset(map, Pos(20, 15), visible) == Camera.Offset(-(20 - half) * 28, -(15 - half) * 28))
      assert(Camera.offset(map, Pos(0, 0), visible) == Camera.Offset(0, 0))
      assert(Camera.offset(map, Pos(39, 0), visible) == Camera.Offset(-(40 - visible) * 28, 0))
      assert(Camera.offset(map, Pos(0, 29), visible) == Camera.Offset(0, -(30 - visible) * 28))
      assert(Camera.offset(map, Pos(39, 29), visible) == Camera.Offset(-(40 - visible) * 28, -(30 - visible) * 28))
      assert(Camera.offset(map, Pos(half, half), visible) == Camera.Offset(0, 0))
      assert(Camera.offset(map, Pos(half + 1, half + 1), visible) == Camera.Offset(-28, -28))
    }
  }

  test("small dimensions are centered independently and equal dimensions do not move") {
    for (visible <- Seq(15, 9)) {
      assert(Camera.offset(board(6, 4), Pos(1, 1), visible) ==
        Camera.Offset((visible - 6) * 14, (visible - 4) * 14))
      assert(Camera.offset(board(6, 30), Pos(1, 29), visible) ==
        Camera.Offset((visible - 6) * 14, -(30 - visible) * 28))
      assert(Camera.offset(board(40, 4), Pos(39, 1), visible) ==
        Camera.Offset(-(40 - visible) * 28, (visible - 4) * 14))
      assert(Camera.offset(board(visible, visible), Pos(visible - 1, visible - 1), visible) == Camera.Offset(0, 0))
    }
  }
}
