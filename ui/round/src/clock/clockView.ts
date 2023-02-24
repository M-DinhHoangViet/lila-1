import * as button from '../view/button';
import * as game from 'game';
import RoundController from '../ctrl';
import { bind, justIcon } from '../util';
import { ClockElements, ClockController, Millis } from './clockCtrl';
import { h, Hooks } from 'snabbdom';
import { Position } from '../interfaces';

export function renderClock(ctrl: RoundController, player: game.Player, position: Position) {
  const clock = ctrl.clock!,
    millis = clock.millisOf(player.playerIndex),
    isPlayer = ctrl.data.player.playerIndex === player.playerIndex,
    isRunning = player.playerIndex === clock.times.activePlayerIndex;
  const update = (el: HTMLElement) => {
    const els = clock.elements[player.playerIndex],
      millis = clock.millisOf(player.playerIndex),
      isRunning = player.playerIndex === clock.times.activePlayerIndex;
    els.time = el;
    els.clock = el.parentElement!;
    el.innerHTML = formatClockTime(millis, clock.showTenths(millis), isRunning, clock.opts.nvui);
  };
  const timeHook: Hooks = {
    insert: vnode => update(vnode.elm as HTMLElement),
    postpatch: (_, vnode) => update(vnode.elm as HTMLElement),
  };
  return h(
    'div.rclock.rclock-' + position,
    {
      class: {
        outoftime: millis <= 0,
        running: isRunning,
        emerg: millis < clock.emergMs,
      },
    },
    clock.opts.nvui
      ? [
          h('div.time', {
            attrs: { role: 'timer' },
            hook: timeHook,
          }),
        ]
      : [
          clock.showBar && game.bothPlayersHavePlayed(ctrl.data) ? showBar(ctrl, player.playerIndex) : undefined,
          h('div.time', {
            class: {
              hour: millis > 3600 * 1000,
            },
            hook: timeHook,
          }),
          renderBerserk(ctrl, player.playerIndex, position),
          isPlayer ? goBerserk(ctrl) : button.moretime(ctrl),
          tourRank(ctrl, player.playerIndex, position),
        ]
  );
}

const pad2 = (num: number): string => (num < 10 ? '0' : '') + num;
const sepHigh = '<sep>:</sep>';
const sepLow = '<sep class="low">:</sep>';

function formatClockTime(time: Millis, showTenths: boolean, isRunning: boolean, nvui: boolean) {
  const date = new Date(time);
  if (nvui)
    return (
      (time >= 3600000 ? Math.floor(time / 3600000) + 'H:' : '') +
      date.getUTCMinutes() +
      'M:' +
      date.getUTCSeconds() +
      'S'
    );
  const millis = date.getUTCMilliseconds(),
    sep = isRunning && millis < 500 ? sepLow : sepHigh,
    baseStr = pad2(date.getUTCMinutes()) + sep + pad2(date.getUTCSeconds());
  if (time >= 3600000) {
    const hours = pad2(Math.floor(time / 3600000));
    return hours + sepHigh + baseStr;
  } else if (showTenths) {
    let tenthsStr = Math.floor(millis / 100).toString();
    if (!isRunning && time < 1000) {
      tenthsStr += '<huns>' + (Math.floor(millis / 10) % 10) + '</huns>';
    }

    return baseStr + '<tenths><sep>.</sep>' + tenthsStr + '</tenths>';
  } else {
    return baseStr;
  }
}

function showBar(ctrl: RoundController, playerIndex: PlayerIndex) {
  const clock = ctrl.clock!;
  const update = (el: HTMLElement) => {
    if (el.animate !== undefined) {
      let anim = clock.elements[playerIndex].barAnim;
      if (anim === undefined || !anim.effect || (anim.effect as KeyframeEffect).target !== el) {
        anim = el.animate([{ transform: 'scale(1)' }, { transform: 'scale(0, 1)' }], {
          duration: clock.barTime,
          fill: 'both',
        });
        clock.elements[playerIndex].barAnim = anim;
      }
      const remaining = clock.millisOf(playerIndex);
      anim.currentTime = clock.barTime - remaining;
      if (playerIndex === clock.times.activePlayerIndex) {
        // Calling play after animations finishes restarts anim
        if (remaining > 0) anim.play();
      } else anim.pause();
    } else {
      clock.elements[playerIndex].bar = el;
      el.style.transform = 'scale(' + clock.timeRatio(clock.millisOf(playerIndex)) + ',1)';
    }
  };
  return h('div.bar', {
    class: { berserk: !!ctrl.goneBerserk[playerIndex] },
    hook: {
      insert: vnode => update(vnode.elm as HTMLElement),
      postpatch: (_, vnode) => update(vnode.elm as HTMLElement),
    },
  });
}

export function updateElements(clock: ClockController, els: ClockElements, millis: Millis) {
  if (els.time) els.time.innerHTML = formatClockTime(millis, clock.showTenths(millis), true, clock.opts.nvui);
  if (els.bar) els.bar.style.transform = 'scale(' + clock.timeRatio(millis) + ',1)';
  if (els.clock) {
    const cl = els.clock.classList;
    if (millis < clock.emergMs) cl.add('emerg');
    else if (cl.contains('emerg')) cl.remove('emerg');
  }
}

function showBerserk(ctrl: RoundController, playerIndex: PlayerIndex): boolean {
  return !!ctrl.goneBerserk[playerIndex] && ctrl.data.game.turns <= 1 && game.playable(ctrl.data);
}

function renderBerserk(ctrl: RoundController, playerIndex: PlayerIndex, position: Position) {
  return showBerserk(ctrl, playerIndex) ? h('div.berserked.' + position, justIcon('`')) : null;
}

function goBerserk(ctrl: RoundController) {
  if (!game.berserkableBy(ctrl.data)) return;
  if (ctrl.goneBerserk[ctrl.data.player.playerIndex]) return;
  return h('button.fbt.go-berserk', {
    attrs: {
      title: 'GO BERSERK! Half the time, no increment, bonus point',
      'data-icon': '`',
    },
    hook: bind('click', ctrl.goBerserk),
  });
}

function tourRank(ctrl: RoundController, playerIndex: PlayerIndex, position: Position) {
  const d = ctrl.data,
    ranks = d.tournament?.ranks || d.swiss?.ranks;
  return ranks && !showBerserk(ctrl, playerIndex)
    ? h(
        'div.tour-rank.' + position,
        {
          attrs: { title: 'Current tournament rank' },
        },
        '#' + ranks[playerIndex]
      )
    : null;
}
