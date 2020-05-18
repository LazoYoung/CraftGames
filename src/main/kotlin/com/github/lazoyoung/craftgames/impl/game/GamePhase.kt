package com.github.lazoyoung.craftgames.impl.game

enum class GamePhase {

    /** Game is being initialized. **/
    INIT,

    /** Map is being generated. **/
    GENERATE,

    /** Game is being edited. **/
    EDIT,

    /** Players are waiting for game to start. **/
    LOBBY,

    /** Game-play is in progress. **/
    PLAYING,

    /** Game has finished. Ceremony is in progress. **/
    FINISH,

    /** Game is being terminated. **/
    TERMINATE

}