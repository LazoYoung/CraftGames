package com.github.lazoyoung.craftgames.impl.game

class GameTask(
        private val game: Game,
        internal vararg val phase: GamePhase
) {

    private lateinit var task: Runnable

    /**
     * Schedule a [task] to be executed upon transition to [phase].
     *
     * Task will be removed once it's been executed.
     *
     * @param task Task to register.
     */
    fun schedule(task: () -> Unit) {
        this.task = Runnable(task)
        game.taskList.add(this)
    }

    internal fun execute() {
        task.run()
    }
}