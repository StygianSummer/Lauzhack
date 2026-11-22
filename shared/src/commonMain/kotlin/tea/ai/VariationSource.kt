package tea.ai

import tea.model.Item

/**
 * Cached, human-reviewed variations of a seed item (plan §11). Variations are generated offline
 * (`:server:generateVariations`, stretch goal), validated, reviewed by a person and bundled as
 * files; the app never calls an AI model to get them.
 */
interface VariationSource {
    /** Variations of [seedId] (an item id, or a twin pair's root), possibly empty. */
    fun variationsOf(seedId: String): List<Item>
}

/**
 * Bundled variations, keyed by seed id. MVP 0 ships none, so the default instance is empty and
 * practice uses only the core items.
 */
class BundledVariations(private val bySeed: Map<String, List<Item>> = emptyMap()) : VariationSource {
    override fun variationsOf(seedId: String): List<Item> = bySeed[seedId].orEmpty()

    companion object {
        val EMPTY = BundledVariations()
    }
}
