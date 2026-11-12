package tech.vauldex.dekita.presentation.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import tech.vauldex.dekita.data.AuthRepository
import tech.vauldex.dekita.data.InboxRepository
import tech.vauldex.dekita.data.LoadState
import tech.vauldex.dekita.data.OrderRepository
import tech.vauldex.dekita.data.StoreRepository
import tech.vauldex.dekita.data.dataOrNull
import tech.vauldex.dekita.domain.FavoriteItem
import tech.vauldex.dekita.domain.InboxKind
import tech.vauldex.dekita.domain.Order
import tech.vauldex.dekita.domain.OrderStatus
import tech.vauldex.dekita.domain.RATING_TAGS
import tech.vauldex.dekita.domain.Session
import tech.vauldex.dekita.domain.canBeRated
import tech.vauldex.dekita.presentation.STOP_TIMEOUT_MILLIS
import tech.vauldex.dekita.presentation.ratePromptBody
import tech.vauldex.dekita.presentation.ratePromptTitle

/** One ordered item on the Rate screen, with its 1-tap Favorite toggle. */
data class RateItem(val item: FavoriteItem, val isFavorite: Boolean)

data class RateOrderUiState(
    val isLoading: Boolean = true,
    /** The completed order. Null when there is none with this id, it isn't completed, or while signed out. */
    val order: Order? = null,
    val items: List<RateItem> = emptyList(),
    /** 0 until the customer picks 1 to 5. */
    val stars: Int = 0,
    val tags: Set<String> = emptySet(),
    val tagOptions: List<String> = RATING_TAGS,
) {
    val alreadyRated: Boolean get() = order?.rating != null

    val canSubmit: Boolean get() = order != null && !alreadyRated && stars in 1..5

    val heading: String get() = order?.let { "Order #${it.number} · ${it.storeName}" }.orEmpty()

    /** For TalkBack and VoiceOver on the star row, for example "3 of 5 stars". */
    val starsDescription: String get() = if (stars == 0) "No stars selected" else "$stars of 5 stars"
}

/**
 * Rate & Add to Favorites (RCPT-01), shown after [Confirm] or the next time an auto-completed order is
 * opened. A rating is recorded once. Favorites are saved on each tap. Leaving without submitting is a
 * skip, which leaves an in-app inbox message to come back to. No push notification is sent.
 */
class RateOrderViewModel(
    private val orderId: String,
    private val orderRepository: OrderRepository,
    private val storeRepository: StoreRepository,
    private val inboxRepository: InboxRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private class Draft(val stars: Int = 0, val tags: Set<String> = emptySet())

    private val draft = MutableStateFlow(Draft())

    val state: StateFlow<RateOrderUiState> =
        combine(orderRepository.orders, storeRepository.favoriteItems, authRepository.session, draft, ::toUiState)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                toUiState(orderRepository.orders.value, storeRepository.favoriteItems.value, authRepository.session.value, draft.value),
            )

    /** 1 to 5. Ignored once the order is rated. */
    fun setStars(stars: Int) {
        if (stars in 1..5 && !currentState().alreadyRated) draft.update { Draft(stars, it.tags) }
    }

    fun toggleTag(tag: String) {
        if (tag !in RATING_TAGS || currentState().alreadyRated) return
        draft.update { Draft(it.stars, if (tag in it.tags) it.tags - tag else it.tags + tag) }
    }

    /** Saves or removes the item from favorites at once, even if the customer then skips the rating. */
    fun toggleFavorite(itemId: String) {
        val item = currentState().items.firstOrNull { it.item.itemId == itemId } ?: return
        storeRepository.toggleFavoriteItem(item.item)
    }

    /** Records the rating. Returns true the first time only, so a double tap records one rating. */
    fun submit(): Boolean {
        val current = currentState()
        if (!current.canSubmit) return false
        return orderRepository.rate(orderId, current.stars, current.tags)
    }

    /**
     * Leaving without a rating: Skip, Close, or Back. Leaves one inbox message asking to rate the order
     * later. Does nothing once the order is rated.
     */
    fun skip() {
        val order = currentState().order?.takeIf { it.canBeRated } ?: return
        inboxRepository.add(
            id = ratePromptId(order.id),
            title = ratePromptTitle(order.number),
            body = ratePromptBody(order.storeName),
            kind = InboxKind.RATE_ORDER,
            orderId = order.id,
        )
    }

    private fun currentState(): RateOrderUiState =
        toUiState(orderRepository.orders.value, storeRepository.favoriteItems.value, authRepository.session.value, draft.value)

    private fun toUiState(
        orders: LoadState<List<Order>>,
        favorites: List<FavoriteItem>,
        session: Session?,
        draft: Draft,
    ): RateOrderUiState {
        val order = orders.dataOrNull()
            ?.firstOrNull { it.id == orderId && it.status == OrderStatus.COMPLETED }
            ?.takeIf { session != null }
            ?: return RateOrderUiState(isLoading = orders is LoadState.Loading)
        val items = order.lines
            .distinctBy { it.item.id }
            .map { line -> FavoriteItem(order.storeId, line.item.id, line.item.name, line.item.basePrice) }
            .map { item -> RateItem(item, favorites.any { it.storeId == item.storeId && it.itemId == item.itemId }) }
        // A rated order shows its saved rating, not the draft.
        val rating = order.rating
        return RateOrderUiState(
            isLoading = false,
            order = order,
            items = items,
            stars = rating?.stars ?: draft.stars,
            tags = rating?.tags ?: draft.tags,
        )
    }
}

/** The inbox id of an order's rate prompt, so skipping twice leaves one message. */
internal fun ratePromptId(orderId: String): String = "msg-rate-$orderId"
