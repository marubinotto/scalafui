package fuidungeon

import org.scalatest.funsuite.AnyFunSuite
import Game.*
import Main.*

class TileAppearanceSpec extends AnyFunSuite {
  private val board = Board(Vector("#######", "#S...E#", "#######"))
  private val position = Pos(2, 1)
  private val base = State(board, board.start, items = Map(position -> Item.Key))
  private val ghost = Flash(Damage(Target.Monster(7), position, true, Some(EnemyKind.Goblin)), 1)

  test("living occupants take precedence over defeated afterimages and items") {
    val player = base.copy(player = position)
    assert(tileAppearance(player, position, Some(ghost)).cssClass == "player")
    val occupied = base.copy(enemies = Vector(Enemy.spawn(8, position, EnemyKind.Ogre)))
    assert(tileAppearance(occupied, position, Some(ghost)) ==
      TileAppearance(EnemyKind.Ogre.icon, "enemy", EnemyKind.Ogre.label))
    assert(tileAppearance(base, position, Some(ghost)).label == "Defeated Goblin")
    assert(tileAppearance(base, position, None).cssClass == "key")
  }

  test("only defeated enemies produce afterimages; items precede terrain") {
    val hit = ghost.copy(damage = ghost.damage.copy(defeated = false))
    assert(tileAppearance(base, position, Some(hit)).cssClass == "key")
    assert(tileAppearance(base.copy(items = Map(position -> Item.Potion)), position, None).cssClass == "potion")
    assert(tileAppearance(base.copy(items = Map(board.exit -> Item.Key)), board.exit, None).cssClass == "key")
    assert(tileAppearance(base, board.exit, None).cssClass == "exit")
    assert(tileAppearance(base, Pos(0, 0), None).cssClass == "wall")
    assert(tileAppearance(base, Pos(3, 1), None).cssClass == "floor")
  }
}
