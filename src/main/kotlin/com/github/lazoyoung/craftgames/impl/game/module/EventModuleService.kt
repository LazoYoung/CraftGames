package com.github.lazoyoung.craftgames.impl.game.module

import com.github.lazoyoung.craftgames.api.event.*
import com.github.lazoyoung.craftgames.api.module.EventModule
import java.util.function.Consumer

class EventModuleService : EventModule, Service {

    internal val events = HashMap<Class<out GameEvent>, Consumer<in GameEvent>>()

    override fun start() {}

    override fun terminate() {}

    override fun onGameInit(callback: Consumer<GameInitEvent>) {
        events[GameInitEvent::class.java] = Consumer {
            callback.accept(it as GameInitEvent)
        }
    }

    override fun onGameStart(callback: Consumer<GameStartEvent>) {
        events[GameStartEvent::class.java] = Consumer {
            callback.accept(it as GameStartEvent)
        }
    }

    override fun onGameJoin(callback: Consumer<GameJoinEvent>) {
        events[GameJoinEvent::class.java] = Consumer {
            callback.accept(it as GameJoinEvent)
        }
    }

    override fun afterGameJoin(callback: Consumer<GameJoinPostEvent>) {
        events[GameJoinPostEvent::class.java] = Consumer {
            callback.accept(it as GameJoinPostEvent)
        }
    }

    override fun onGameLeave(callback: Consumer<GameLeaveEvent>) {
        events[GameLeaveEvent::class.java] = Consumer {
            callback.accept(it as GameLeaveEvent)
        }
    }

    override fun onGameTimeout(callback: Consumer<GameTimeoutEvent>) {
        events[GameTimeoutEvent::class.java] = Consumer {
            callback.accept(it as GameTimeoutEvent)
        }
    }

    override fun onGameFinish(callback: Consumer<GameFinishEvent>) {
        events[GameFinishEvent::class.java] = Consumer {
            callback.accept(it as GameFinishEvent)
        }
    }

    override fun onAreaEnter(callback: Consumer<GameAreaEnterEvent>) {
        events[GameAreaEnterEvent::class.java] = Consumer {
            callback.accept(it as GameAreaEnterEvent)
        }
    }

    override fun onAreaExit(callback: Consumer<GameAreaExitEvent>) {
        events[GameAreaExitEvent::class.java] = Consumer {
            callback.accept(it as GameAreaExitEvent)
        }
    }

    override fun onPlayerKill(callback: Consumer<GamePlayerKillEvent>) {
        events[GamePlayerKillEvent::class.java] = Consumer {
            callback.accept(it as GamePlayerKillEvent)
        }
    }

    override fun onPlayerDeath(callback: Consumer<GamePlayerDeathEvent>) {
        events[GamePlayerDeathEvent::class.java] = Consumer {
            callback.accept(it as GamePlayerDeathEvent)
        }
    }

    override fun onPlayerInteract(callback: Consumer<GamePlayerInteractEvent>) {
        events[GamePlayerInteractEvent::class.java] = Consumer {
            callback.accept(it as GamePlayerInteractEvent)
        }
    }

    override fun onEntityDamage(callback: Consumer<GameEntityDamageEvent>) {
        events[GameEntityDamageEvent::class.java] = Consumer {
            callback.accept(it as GameEntityDamageEvent)
        }
    }

}