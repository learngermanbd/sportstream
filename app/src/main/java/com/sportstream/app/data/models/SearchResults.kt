package com.sportstream.app.data.models


/**
 * Phase 5 · Step 5.6 — Search hit aggregate.
 *
 * SearchViewModel reduces a [com.sportstream.app.ui.viewmodels.MainViewModel]
 * MainSnapshot to this aggregate by case-insensitive substring matching the
 * typed query against each shape's primary + secondary text fields:
 *  - Event     → title + teamA.name + teamB.name + category
 *  - Highlight → title
 *
 * Channels are intentionally NOT part of the search surface: they live in
 * a separate [com.sportstream.app.ui.viewmodels.CategoriesSnapshot] (held
 * by the Categories tab's fragment-scoped ViewModel). Channel browsing is
 * driven by the chip filter in the Categories tab; search keeps its focus
 * on text-rich categories (Events + Highlights) where the substring
 * matching the user types yields high-quality homing. A future polish
 * pass can re-add channels by hoisting CategoriesViewModel up to
 * activity-scope and `.combine()` its channels into this aggregate.
 *
 * Passed to the adapter as two discrete lists so each rendering branch
 * can own its own section header + row layout (mirrors the per-row layout
 * patterns established in Phase 3 · Step 3.3/3.5).
 */
data class SearchResults(
    val events: List<Event>,
    val highlights: List<Highlight>
) {
    val isEmpty: Boolean
        get() = events.isEmpty() && highlights.isEmpty()

    val hasAny: Boolean
        get() = !isEmpty

    companion object {
        /** Empty aggregate for the Idle state. */
        val EMPTY = SearchResults(emptyList(), emptyList())

        /**
         * Reduce a snapshot's full lists down to the matching subset for
         * [query]. Prefix-aware ordering (matches-at-front) is intentionally
         * NOT here — adapters sort within section so a fresh user query
         * doesn't surprise the position of items already in view.
         */
        fun filter(
            events: List<Event>,
            highlights: List<Highlight>,
            query: String
        ): SearchResults {
            val q = query.trim()
            if (q.isEmpty()) return EMPTY
            val qLower = q.lowercase()
            return SearchResults(
                events = events.filter { event ->
                    event.title.lowercase().contains(qLower) ||
                        event.teamA.name.lowercase().contains(qLower) ||
                        event.teamB.name.lowercase().contains(qLower) ||
                        event.category.lowercase().contains(qLower)
                },
                highlights = highlights.filter { it.title.lowercase().contains(qLower) }
            )
        }
    }
}

