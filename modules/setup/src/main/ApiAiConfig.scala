package lila.setup

import strategygames.{ Clock, GameFamily, Mode, Speed, P1, P2 }
import strategygames.variant.Variant
import strategygames.format.FEN
import strategygames.chess.variant.{ FromPosition }

import lila.game.{ Game, Player, Pov, Source }
import lila.lobby.PlayerIndex
import lila.user.User

final case class ApiAiConfig(
    variant: Variant,
    fenVariant: Option[Variant],
    clock: Option[Clock.Config],
    daysO: Option[Int],
    playerIndex: PlayerIndex,
    level: Int,
    fen: Option[FEN] = None
) extends Config
    with Positional {

  val strictFen = false

  val days      = ~daysO
  val increment = clock.??(_.increment.roundSeconds)
  val time      = clock.??(_.limit.roundSeconds / 60)
  val timeMode =
    if (clock.isDefined) TimeMode.RealTime
    else if (daysO.isDefined) TimeMode.Correspondence
    else TimeMode.Unlimited

  def game(user: Option[User]) =
    fenGame { chessGame =>
      val perfPicker = lila.game.PerfPicker.mainOrDefault(
        Speed(chessGame.clock.map(_.config)),
        chessGame.situation.board.variant,
        makeDaysPerTurn
      )
      Game
        .make(
          chess = chessGame,
          p1Player = creatorPlayerIndex.fold(
            Player.make(P1, user, perfPicker),
            Player.make(P1, level.some)
          ),
          p2Player = creatorPlayerIndex.fold(
            Player.make(P2, level.some),
            Player.make(P2, user, perfPicker)
          ),
          mode = Mode.Casual,
          source = if (chessGame.board.variant.fromPosition) Source.Position else Source.Ai,
          daysPerTurn = makeDaysPerTurn,
          pgnImport = None
        )
        .sloppy
    } start

  def pov(user: Option[User]) = Pov(game(user), creatorPlayerIndex)

  def autoVariant =
    if (variant.standard && fen.exists(!_.initial)) copy(variant = Variant.wrap(FromPosition))
    else this
}

object ApiAiConfig extends BaseConfig {

  // lazy val clockLimitSeconds: Set[Int] = Set(0, 15, 30, 45, 60, 90) ++ (2 to 180).view.map(60 *).toSet

  def from(
      l: Int,
      v: Option[String],
      cl: Option[Clock.Config],
      d: Option[Int],
      c: Option[String],
      pos: Option[String]
  ) = {
    val variant = Variant.orDefault(~v)
    new ApiAiConfig(
      variant = variant,
      fenVariant = none,
      clock = cl,
      daysO = d,
      playerIndex = PlayerIndex.orDefault(~c),
      level = l,
      fen = pos.map(f => FEN.apply(variant.gameLogic, f))
    ).autoVariant
  }
}
