package io.github.inputbiscuit.dotboard.core.render

import io.github.inputbiscuit.dotboard.core.data.DepartureException

/** How each failure reads on the board itself. */
fun DepartureException.toBoardMessage(): BoardContent.Message = when (this) {
    is DepartureException.MissingApiKey -> BoardContent.Message("No API key", "Add one in Dotboard settings")
    is DepartureException.InvalidApiKey -> BoardContent.Message("API key rejected", "Check it in Dotboard settings")
    is DepartureException.Network -> BoardContent.Message("No connection")
    is DepartureException.Http -> BoardContent.Message("Service unavailable", "Please try again later")
    is DepartureException.BadResponse -> BoardContent.Message("Service unavailable", "Please try again later")
}
