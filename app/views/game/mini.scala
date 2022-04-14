package views.html.game

import strategygames.format.Forsyth
import strategygames.variant.Variant
import controllers.routes
import play.api.i18n.Lang

import lila.api.Context
import lila.app.templating.Environment._
import lila.app.ui.ScalatagsTemplate._
import lila.game.Pov
import lila.i18n.defaultLang

object mini {

  private val dataLive  = attr("data-live")
  private val dataState = attr("data-state")
  private val dataTime  = attr("data-time")
  val cgWrap            = span(cls := "cg-wrap")(cgWrapContent)

  def extraClasses(variant: Variant) = {
    val gameLogic  = variant.gameLogic.name.toLowerCase()
    val gameFamily = variant.gameFamily.key
    variant match {
      case Variant.Draughts(v) =>
        s"${gameLogic} is${v.boardSize.key}"
      case _ =>
        s"${gameFamily}"
    }
  }

  def apply(
      pov: Pov,
      ownerLink: Boolean = false,
      tv: Boolean = false,
      withLink: Boolean = true
  )(implicit ctx: Context): Tag = {
    val game    = pov.game
    val isLive  = game.isBeingPlayed
    val tag     = if (withLink) a else span
    val extra   = extraClasses(game.variant)
    val variant = game.variant.key
    tag(
      href := withLink.option(gameLink(game, pov.playerIndex, ownerLink, tv)),
      cls := s"mini-game mini-game-${game.id} mini-game--init ${extra} ${variant} variant-${variant} is2d",
      dataLive := isLive.option(game.id),
      renderState(pov)
    )(
      renderPlayer(!pov),
      cgWrap,
      renderPlayer(pov)
    )
  }

  def noCtx(pov: Pov, tv: Boolean = false): Tag = {
    val game    = pov.game
    val isLive  = game.isBeingPlayed
    val extra   = extraClasses(game.variant)
    val variant = game.variant.key
    a(
      href := (if (tv) routes.Tv.index else routes.Round.watcher(pov.gameId, pov.playerIndex.name)),
      cls := s"mini-game mini-game-${game.id} mini-game--init is2d ${isLive ?? "mini-game--live"} ${extra} ${variant} variant-${variant}",
      dataLive := isLive.option(game.id),
      renderState(pov)
    )(
      renderPlayer(!pov)(defaultLang),
      cgWrap,
      renderPlayer(pov)(defaultLang)
    )
  }

  def renderState(pov: Pov) =
    pov.game.variant match {
      case Variant.Chess(_) | Variant.FairySF(_) =>
        dataState := s"${Forsyth.boardAndPlayer(pov.game.variant.gameLogic, pov.game.situation)},${pov.playerIndex.name},${~pov.game.lastMoveKeys}"
      case Variant.Draughts(v) =>
        dataState := s"${Forsyth.boardAndPlayer(
          pov.game.variant.gameLogic,
          pov.game.situation
        )}|${v.boardSize.width}x${v.boardSize.height}|${pov.playerIndex.name}|${~pov.game.lastMoveKeys}"
    }

  private def renderPlayer(pov: Pov)(implicit lang: Lang) =
    span(cls := "mini-game__player")(
      span(cls := "mini-game__user")(
        playerUsername(pov.player, withRating = false),
        span(cls := "rating")(lila.game.Namer ratingString pov.player)
      ),
      if (pov.game.finished) renderResult(pov)
      else pov.game.clock.map { renderClock(_, pov) }
    )

  private def calculateScore(pov: Pov): String =
    pov.game.variant.key match {
      case "flipello" =>
        "(" + pov.game.board.pieces
          .map { case (_, piece) => piece.player.name }
          .filter(p => p == pov.playerIndex.name)
          .size
          .toString() + ")"
      case "threeCheck" | "fiveCheck" =>
        "(" + pov.game.history
          .checkCount(pov.game.opponent(pov.playerIndex).playerIndex)
          .toString() + ")"
      case _ => ""
    }

  private def renderResult(pov: Pov) =
    span(cls := "mini-game__result")(
      pov.game.winnerPlayerIndex.fold("½") { c =>
        if (c == pov.playerIndex) "1" else "0"
      }
        +
          calculateScore(pov)
    )

  private def renderClock(clock: strategygames.Clock, pov: Pov) = {
    val s = clock.remainingTime(pov.playerIndex).roundSeconds
    span(
      cls := s"mini-game__clock mini-game__clock--${pov.playerIndex.name}",
      dataTime := s
    )(
      f"${s / 60}:${s % 60}%02d"
        +
          calculateScore(pov)
    )
  }
}
