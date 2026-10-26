package fuidungeon

/** Pixel offsets for a clipped map. Small maps are centered, large maps track the player. */
private[fuidungeon] object Camera {
  val tileSize = 28
  case class Offset(x: Int, y: Int)

  def offset(board: Game.Board, player: Game.Pos, visible: Int): Offset = {
    def axis(size: Int, position: Int): Int =
      if (size < visible) (visible - size) * tileSize / 2
      else -((position - visible / 2).max(0).min(size - visible) * tileSize)
    Offset(axis(board.width, player.x), axis(board.height, player.y))
  }
}
