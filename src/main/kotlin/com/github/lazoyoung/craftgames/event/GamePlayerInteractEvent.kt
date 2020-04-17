package com.github.lazoyoung.craftgames.event

import com.github.lazoyoung.craftgames.game.Game
import com.github.lazoyoung.craftgames.game.player.PlayerData
import org.bukkit.block.Block
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack

class GamePlayerInteractEvent(
        playerData: PlayerData,
        game: Game,
        private val event: PlayerInteractEvent
) : GamePlayerEvent(playerData, game), Cancellable {

    companion object {
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList {
            return this.handlerList
        }
    }

    override fun getHandlers(): HandlerList {
        return getHandlerList()
    }

    /**
     * @param use the action to take with the interacted block
     */
    fun setUseInteractedBlock(use: Result) {
        event.setUseInteractedBlock(use)
    }

    /**
     * @param use the action to take with the item in hand
     */
    fun setUseItemInHand(use: Result) {
        event.setUseItemInHand(use)
    }

    /**
     * This controls the action to take with the block (if any) that was
     * clicked on. This event gets processed for all blocks, but most don't
     * have a default action
     *
     * @return the action to take with the interacted block
     */
    fun useInteractedBlock(): Result {
        return event.useInteractedBlock()
    }

    /**
     * This controls the action to take with the item the player is holding.
     * This includes both blocks and items (such as flint and steel or
     * records). When this is set to default, it will be allowed if no action
     * is taken on the interacted block.
     *
     * @return the action to take with the item in hand
     */
    fun useItemInHand(): Result {
        return event.useItemInHand()
    }

    /**
     * Returns the action type of block interaction.
     *
     * @return Action returns the type of interaction
     */
    fun getBlockAction(): Action {
        return event.action
    }

    /**
     * Returns the clicked block
     *
     * @return Block returns the block clicked with this item.
     */
    fun getClickedBlock(): Block? {
        return event.clickedBlock
    }

    /**
     * Returns the item in hand represented by this event
     *
     * @return ItemStack the item used
     */
    fun getItemInHand(): ItemStack? {
        return event.item
    }

    override fun setCancelled(cancel: Boolean) {
        event.isCancelled = cancel
    }

    @Suppress("DEPRECATION")
    @Deprecated("Alternative: useItemInHand() or useInteractedBlock()")
    override fun isCancelled(): Boolean {
        return event.isCancelled
    }
}