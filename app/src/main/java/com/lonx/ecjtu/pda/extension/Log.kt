package com.lonx.ecjtu.pda.extension

import slimber.log.e

inline fun <T> T.log(makeMessage: (T) -> String = { it.toString() }): T =
    also { e { makeMessage(this) } }