package io.woong.snappicker

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * A scrollable picker to allow user to select one value from multiple items.
 *
 * [ValuePickerView] displays multiple items as a linear list, vertical or horizontal, with
 * snap fling behavior. The picker scroll position will stop at specified item, not ambiguous
 * position.
 *
 * This picker works with several components:
 * - [ValuePickerView]: A view class to be placed into view hierarchy.
 * - [ValuePickerAdapter]: An adapter class to handle data set and item view associated with
 *   specified data.
 * - Listeners: Listener classes or interfaces to receive specified events from this picker.
 *
 * To display values into picker view, at least, places a view to layout and adds adapter
 * to the picker view. And set values to the adapter.
 *
 * @see ValuePickerAdapter
 */
public class ValuePickerView : FrameLayout {

    private val recyclerView: RecyclerView
    private val recyclerViewId: Int

    /**
     * Adapter to manage child view and data.
     */
    public var adapter: ValuePickerAdapter<*, *>? = null
        set(value) {
            val currentAdapter = adapter
            if (currentAdapter != null) {
                currentAdapter.pickerViewRef = null
            }
            if (value != null) {
                value.pickerViewRef = this
            }
            recyclerView.adapter = value
            field = value
            requestLayout()
        }

    /**
     * Orientation of this picker. Either [ORIENTATION_VERTICAL] or [ORIENTATION_HORIZONTAL].
     */
    @RecyclerView.Orientation
    public var orientation: Int
        get() = (recyclerView.layoutManager as LinearLayoutManager).orientation
        set(value) {
            if (value != ORIENTATION_HORIZONTAL || value != ORIENTATION_VERTICAL) {
                throw IllegalArgumentException("Orientation value must be one of 0 or 1")
            }
            (recyclerView.layoutManager as LinearLayoutManager).orientation = value
            requestLayout()
        }

    /**
     * Whether this picker has infinity length or not.
     */
    public var isCyclic: Boolean = DEFAULT_CYCLIC_ENABLED
        set(value) {
            field = value
            requestLayout()
        }

    private var onPickerScrollListener: OnPickerScrollListener? = null
    private var onPickerValueSelectedListener: OnPickerValueSelectedListener? = null

    public constructor(context: Context) : this(context, null)

    public constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    public constructor(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int
    ) : this(context, attrs, defStyleAttr, 0)

    public constructor(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int,
        @StyleRes defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.ValuePickerView, defStyleAttr, defStyleRes)
        val orientation = a.getInt(R.styleable.ValuePickerView_android_orientation, ORIENTATION_VERTICAL)
        isCyclic = a.getBoolean(R.styleable.ValuePickerView_isCyclic, true)
        val initialIndex = a.getInt(R.styleable.ValuePickerView_initialIndex, 0)
        a.recycle()

        recyclerView = RecyclerView(context)
        recyclerViewId = ViewCompat.generateViewId()
        recyclerView.id = recyclerViewId
        val pickerLayoutManager = LinearLayoutManager(context, orientation, false)
        recyclerView.layoutManager = pickerLayoutManager
        LinearSnapHelper().attachToRecyclerView(recyclerView)
        attachOnPickerScrollListenerAdapter(recyclerView)
        attachOnPickerValueSelectedListenerAdapter(recyclerView)
        attachCenterRepositionHelper(recyclerView)
        addView(recyclerView)
        postScrollToInitialPosition(initialIndex)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        // Move inner picker recycler view to front.
        if (childCount > 1) {
            val pickerView = getChildAt(0)
            if (pickerView.id == recyclerViewId) {
                removeViewAt(0)
                addView(pickerView)
            }
        }

        // Set padding to internal RecyclerView and set clipToPadding to false for positioning
        // selected item view to center.
        // If clipToPadding is false, the padding are applied but children views are not clipped.
        // So, the first and last item can be placed on the center of RecyclerView, and padding
        // areas can be transparent and scrollable.
        val adapter = this.adapter
        if (adapter != null) {
            val innerPadding: Int
            if (orientation == ORIENTATION_VERTICAL) {
                innerPadding = (measuredHeight / 2) - (adapter.getMaxItemSize(context) / 2)
                recyclerView.setPadding(0, innerPadding, 0, innerPadding)
            } else {
                innerPadding = (measuredWidth / 2) - (adapter.getMaxItemSize(context) / 2)
                recyclerView.setPadding(innerPadding, 0, innerPadding, 0)
            }
            if (recyclerView.clipToPadding) {
                recyclerView.clipToPadding = false
            }
        }
    }

    private fun attachOnPickerScrollListenerAdapter(recyclerView: RecyclerView) {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                onPickerScrollListener?.onScrollStateChanged(this@ValuePickerView, newState)
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                onPickerScrollListener?.onScrolled(this@ValuePickerView, dx, dy)
            }
        })
    }

    private fun attachOnPickerValueSelectedListenerAdapter(recyclerView: RecyclerView) {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private var prevIndex = RecyclerView.NO_POSITION

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val pickerAdapter = this@ValuePickerView.adapter ?: return
                val position = findCenterItemPosition(recyclerView, pickerAdapter)
                if (position != RecyclerView.NO_POSITION) {
                    val index = position % pickerAdapter.getValueCount()
                    if (index != prevIndex) {
                        prevIndex = index
                        onPickerValueSelectedListener?.onValueSelected(this@ValuePickerView, index)
                    }
                }
            }

            private fun findCenterItemPosition(
                recyclerView: RecyclerView,
                pickerAdapter: ValuePickerAdapter<*, *>
            ): Int {
                val scrollOffset = if (orientation == ORIENTATION_HORIZONTAL) {
                    recyclerView.computeHorizontalScrollOffset().toFloat()
                } else {
                    recyclerView.computeVerticalScrollOffset().toFloat()
                }
                val itemSize = pickerAdapter.getMaxItemSize(context)
                val centerScrollOffset = scrollOffset + (itemSize / 2)
                return centerScrollOffset.toInt() / itemSize
            }
        })
    }

    private fun attachCenterRepositionHelper(recyclerView: RecyclerView) {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == SCROLL_STATE_IDLE) {
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val currentPosition = layoutManager.findFirstCompletelyVisibleItemPosition()
                    scrollToAdapterCenterPosition(currentPosition)
                }
            }
        })
    }

    private fun postScrollToInitialPosition(initialIndex: Int) = post {
        if (isCyclic) {
            scrollToAdapterCenterPosition(initialIndex)
        } else {
            scrollToPosition(initialIndex)
        }
    }

    private fun scrollToAdapterCenterPosition(currentPosition: Int) {
        if (!isCyclic) return
        if (currentPosition == RecyclerView.NO_POSITION) return
        val pickerAdapter = this.adapter ?: return

        val valueCount = pickerAdapter.getValueCount()
        val currentIndex = currentPosition % valueCount
        val mod = Int.MAX_VALUE % valueCount
        val targetPosition = (valueCount * (mod / 2)) + currentIndex
        scrollToPosition(targetPosition)
    }

    /**
     * Move this picker's scroll position to the given position without animation.
     *
     * @param position The destination index.
     */
    public fun scrollToPosition(position: Int) {
        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
        layoutManager.scrollToPosition(position)
    }

    /**
     * Sets a [OnPickerScrollListener] to receive scroll event.
     */
    public fun setOnPickerScrollListener(onPickerScrollListener: OnPickerScrollListener?) {
        this.onPickerScrollListener = onPickerScrollListener
    }

    /**
     * Sets a [OnPickerValueSelectedListener] to receive value selected event.
     */
    public fun setOnPickerValueSelectedListener(onPickerValueSelectedListener: OnPickerValueSelectedListener?) {
        this.onPickerValueSelectedListener = onPickerValueSelectedListener
    }

    public companion object {
        public const val ORIENTATION_HORIZONTAL: Int = RecyclerView.HORIZONTAL
        public const val ORIENTATION_VERTICAL: Int = RecyclerView.VERTICAL

        /**
         * Scroll state constant means this picker is not currently scrolling.
         */
        public const val SCROLL_STATE_IDLE: Int = RecyclerView.SCROLL_STATE_IDLE

        /**
         * Scroll state constant means this picker is currently dragged.
         */
        public const val SCROLL_STATE_DRAGGING: Int = RecyclerView.SCROLL_STATE_DRAGGING

        /**
         * Scroll state constant means this picker is currently animating to a final
         * position after drag finished.
         */
        public const val SCROLL_STATE_SETTLING: Int = RecyclerView.SCROLL_STATE_SETTLING

        public const val DEFAULT_ORIENTATION: Int = ORIENTATION_VERTICAL
        public const val DEFAULT_CYCLIC_ENABLED: Boolean = false
    }
}