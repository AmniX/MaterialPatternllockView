package com.amnix.materiallockview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Debug;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;

import java.util.ArrayList;
import java.util.List;

public class MaterialLockView extends View {
    public static class Cell implements Parcelable {
        public final int row, column;
        static Cell[][] sCells = new Cell[LOCK_SIZE][LOCK_SIZE];

        static {
            for (int i = 0; i < LOCK_SIZE; i++) {
                for (int j = 0; j < LOCK_SIZE; j++) {
                    sCells[i][j] = new Cell(i, j);
                }
            }
        }

        /**
         * @param row    number or row
         * @param column number of column
         */
        private Cell(int row, int column) {
            checkRange(row, column);
            this.row = row;
            this.column = column;
        }

        /**
         * Gets the ID.It is counted from left to right, top to bottom of the matrix, starting by zero.
         *
         * @return the ID.
         */
        public int getId() {
            return row * LOCK_SIZE + column;
        }// getId()

        /**
         * @param row    The row of the cell.
         * @param column The column of the cell.
         */
        public static synchronized Cell of(int row, int column) {
            checkRange(row, column);
            return sCells[row][column];
        }

        /**
         * Gets a cell from its ID.
         *
         * @param id the cell ID.
         * @return the cell.
         * @author Hai Bison
         * @since v2.7 beta
         */
        public static synchronized Cell of(int id) {
            return of(id / LOCK_SIZE, id % LOCK_SIZE);
        }

        private static void checkRange(int row, int column) {
            if (row < 0 || row > LOCK_SIZE - 1) {
                throw new IllegalArgumentException("row must be in range 0-"
                        + (LOCK_SIZE - 1));
            }
            if (column < 0 || column > LOCK_SIZE - 1) {
                throw new IllegalArgumentException("column must be in range 0-"
                        + (LOCK_SIZE - 1));
            }
        }

        /**
         * @return Row and Column in String.
         */
        @Override
        public String toString() {
            return "(ROW=" + row + ",COL=" + column + ")";
        }

        @Override
        public boolean equals(Object object) {
            if (object instanceof Cell)
                return column == ((Cell) object).column
                        && row == ((Cell) object).row;
            return super.equals(object);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(column);
            dest.writeInt(row);
        }

        public static final Creator<Cell> CREATOR = new Creator<Cell>() {

            public Cell createFromParcel(Parcel in) {
                return new Cell(in);
            }

            public Cell[] newArray(int size) {
                return new Cell[size];
            }
        };

        private Cell(Parcel in) {
            column = in.readInt();
            row = in.readInt();
        }

    }

    /**
     * How to display the current pattern.
     */
    public enum DisplayMode {

        /**
         * The pattern drawn is correct (i.e draw it in a friendly color)
         */
        Correct,

        /**
         * Animate the pattern (for demo, and help).
         */
        Animate,

        /**
         * The pattern is wrong (i.e draw a foreboding color)
         */
        Wrong
    }

    /**
     * The call back abstract class for detecting patterns entered by the user.
     */
    public static abstract class OnPatternListener {

        /**
         * A new pattern has begun.
         */
        public void onPatternStart() {

        }

        /**
         * The pattern was cleared.
         */
        public void onPatternCleared() {

        }

        /**
         * The user extended the pattern currently being drawn by one cell.
         *
         * @param pattern The pattern with newly added cell.
         */
        public void onPatternCellAdded(List<Cell> pattern, String SimplePattern) {

        }

        /**
         * A pattern was detected from the user.
         *
         * @param pattern The pattern.
         */
        public void onPatternDetected(List<Cell> pattern, String SimplePattern) {

        }
    }


    /**
     * @author Aman Tonk
     */
    public static final int LOCK_SIZE = 3;

    /**
     * The size of the pattern's matrix.
     */
    public static final int MATRIX_SIZE = LOCK_SIZE * LOCK_SIZE;

    private static final boolean PROFILE_DRAWING = false;
    private final CellState[][] mCellStates;
    private final int mDotSize;
    private final int mDotSizeActivated;
    private final int mPathWidth;
    private boolean mDrawingProfilingStarted = false;
    private Paint mPaint = new Paint();
    private Paint mPathPaint = new Paint();

    /**
     * How many milliseconds we spend animating each circle of a lock pattern if the animating mode is set. The entire
     * animation should take this constant * the length of the pattern to complete.
     */
    private static final int MILLIS_PER_CIRCLE_ANIMATING = 700;

    /**
     * This can be used to avoid updating the display for very small motions or noisy panels. It didn't seem to have
     * much impact on the devices tested, so currently set to 0.
     */
    private static final float DRAG_THRESHHOLD = 0.0f;

    private OnPatternListener mOnPatternListener;
    private ArrayList<Cell> mPattern = new ArrayList<>(MATRIX_SIZE);

    /**
     * Lookup table for the circles of the pattern we are currently drawing. This will be the cells of the complete
     * pattern unless we are animating, in which case we use this to hold the cells we are drawing for the in progress
     * animation.
     */
    private boolean[][] mPatternDrawLookup = new boolean[LOCK_SIZE][LOCK_SIZE];

    /**
     * the in progress point: - during interaction: where the user's finger is - during animation: the current tip of
     * the animating line
     */
    private float mInProgressX = -1;
    private float mInProgressY = -1;

    private long mAnimatingPeriodStart;

    private DisplayMode mPatternDisplayMode = DisplayMode.Correct;
    private boolean mInputEnabled = true;
    private boolean mInStealthMode = false;
    private boolean mEnableHapticFeedback = true;
    private boolean mPatternInProgress = false;

    private float mHitFactor = 0.6f;

    private float mSquareWidth;
    private float mSquareHeight;

    private final Path mCurrentPath = new Path();
    private final Rect mInvalidate = new Rect();
    private final Rect mTmpInvalidateRect = new Rect();

    private int mRegularColor;
    private int mErrorColor;
    private int mSuccessColor;

    private Interpolator mFastOutSlowInInterpolator;
    private Interpolator mLinearOutSlowInInterpolator;

    public static class CellState {
        public float scale = 1.0f;
        public float translateY = 0.0f;
        public float alpha = 1.0f;
        public float size;
        public float lineEndX = Float.MIN_VALUE;
        public float lineEndY = Float.MIN_VALUE;
        public ValueAnimator lineAnimator;
    }

    public MaterialLockView(Context context) {
        this(context, null);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public MaterialLockView(Context context, AttributeSet attrs) {
        super(context, attrs);

        setClickable(true);
        mPathPaint.setAntiAlias(true);
        mPathPaint.setDither(true);

        TypedArray typedArray = context.obtainStyledAttributes(attrs,R.styleable.MaterialLockView);
        mRegularColor = typedArray.getColor(R.styleable.MaterialLockView_LOCK_COLOR,Color.WHITE);
        mErrorColor = typedArray.getColor(R.styleable.MaterialLockView_WRONG_COLOR,Color.RED);
        mSuccessColor = typedArray.getColor(R.styleable.MaterialLockView_CORRECT_COLOR,Color.GREEN);
        typedArray.recycle();


        mPathPaint.setColor(mRegularColor);
        mPathPaint.setStyle(Paint.Style.STROKE);
        mPathPaint.setStrokeJoin(Paint.Join.ROUND);
        mPathPaint.setStrokeCap(Paint.Cap.ROUND);

        mPathWidth = dpToPx(3);
        mPathPaint.setStrokeWidth(mPathWidth);
        mDotSize = dpToPx(12);
        mDotSizeActivated = dpToPx(28);
        mPaint.setAntiAlias(true);
        mPaint.setDither(true);

        mCellStates = new CellState[LOCK_SIZE][LOCK_SIZE];
        for (int i = 0; i < LOCK_SIZE; i++) {
            for (int j = 0; j < LOCK_SIZE; j++) {
                mCellStates[i][j] = new CellState();
                mCellStates[i][j].size = mDotSize;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && !isInEditMode()) {
            mFastOutSlowInInterpolator = AnimationUtils.loadInterpolator(
                    context, android.R.interpolator.fast_out_slow_in);
            mLinearOutSlowInInterpolator = AnimationUtils.loadInterpolator(
                    context, android.R.interpolator.linear_out_slow_in);
        }
    }

    public CellState[][] getCellStates() {
        return mCellStates;
    }

    /**
     * @return Whether the view is in stealth mode.
     */
    public boolean isInStealthMode() {
        return mInStealthMode;
    }

    /**
     * @return Whether the view has tactile feedback enabled.
     */
    public boolean isTactileFeedbackEnabled() {
        return mEnableHapticFeedback;
    }

    /**
     * Set whether the view is in stealth mode. If {@code true}, there will be no visible feedback as the user enters
     * the pattern.
     *
     * @param inStealthMode Whether in stealth mode.
     */
    public void setInStealthMode(boolean inStealthMode) {
        mInStealthMode = inStealthMode;
    }

    /**
     * Set whether the view will use tactile feedback. If {@code true}, there will be tactile feedback as the user
     * enters the pattern.
     *
     * @param tactileFeedbackEnabled Whether tactile feedback is enabled
     */
    public void setTactileFeedbackEnabled(boolean tactileFeedbackEnabled) {
        mEnableHapticFeedback = tactileFeedbackEnabled;
    }

    /**
     * Set the call back for pattern detection.
     *
     * @param onPatternListener The call back.
     */
    public void setOnPatternListener(OnPatternListener onPatternListener) {
        mOnPatternListener = onPatternListener;
    }

    /**
     * Retrieves current pattern.
     *
     * @return current displaying pattern. <b>Note:</b> This is an independent list with the view's pattern itself.
     */
    @SuppressWarnings("unchecked")
    public List<Cell> getPattern() {
        return (List<Cell>) mPattern.clone();
    }

    /**
     * Set the pattern explicitly (rather than waiting for the user to input a pattern).
     *
     * @param displayMode How to display the pattern.
     * @param pattern     The pattern.
     */
    public void setPattern(DisplayMode displayMode, List<Cell> pattern) {
        mPattern.clear();
        mPattern.addAll(pattern);
        clearPatternDrawLookup();
        for (Cell cell : pattern) {
            mPatternDrawLookup[cell.row][cell.column] = true;
        }

        setDisplayMode(displayMode);
    }

    /**
     * Gets display mode.
     *
     * @return display mode.
     */
    public DisplayMode getDisplayMode() {
        return mPatternDisplayMode;
    }// getDisplayMode()

    /**
     * Set the display mode of the current pattern. This can be useful, for instance, after detecting a pattern to tell
     * this view whether change the in progress result to correct or wrong.
     *
     * @param displayMode The display mode.
     */
    public void setDisplayMode(DisplayMode displayMode) {
        mPatternDisplayMode = displayMode;
        if (displayMode == DisplayMode.Animate) {
            if (mPattern.size() == 0) {
                throw new IllegalStateException(
                        "you must have a pattern to "
                                + "animate if you want to set the display mode to animate");
            }
            mAnimatingPeriodStart = SystemClock.elapsedRealtime();
            final Cell first = mPattern.get(0);
            mInProgressX = getCenterXForColumn(first.column);
            mInProgressY = getCenterYForRow(first.row);
            clearPatternDrawLookup();
        }
        invalidate();
    }

    private String getSimplePattern(List<Cell> pattern) {
        StringBuilder stringBuilder = new StringBuilder();
        for (Cell cell : pattern) {
            stringBuilder.append(getSipmleCellPosition(cell));
        }
        return stringBuilder.toString();
    }

    private String getSipmleCellPosition(Cell cell) {
        if (cell == null)
            return "";
        switch (cell.row) {
            case 0:
                switch (cell.column) {
                    case 0:
                        return "1";
                    case 1:
                        return "2";
                    case 2:
                        return "3";
                }
                break;
            case 1:
                switch (cell.column) {
                    case 0:
                        return "4";
                    case 1:
                        return "5";
                    case 2:
                        return "6";
                }
                break;
            case 2:
                switch (cell.column) {
                    case 0:
                        return "7";
                    case 1:
                        return "8";
                    case 2:
                        return "9";
                }
                break;

        }
        return "";
    }

    private void notifyCellAdded() {
        if (mOnPatternListener != null) {
            mOnPatternListener.onPatternCellAdded(mPattern, getSimplePattern(mPattern));
        }
    }

    private void notifyPatternStarted() {
        if (mOnPatternListener != null) {
            mOnPatternListener.onPatternStart();
        }
    }

    private void notifyPatternDetected() {
        if (mOnPatternListener != null) {
            mOnPatternListener.onPatternDetected(mPattern, getSimplePattern(mPattern));
        }
    }

    private void notifyPatternCleared() {
        if (mOnPatternListener != null) {
            mOnPatternListener.onPatternCleared();
        }
    }

    /**
     * Clear the pattern.
     */
    public void clearPattern() {
        resetPattern();
    }

    /**
     * Reset all pattern state.
     */
    private void resetPattern() {
        mPattern.clear();
        clearPatternDrawLookup();
        mPatternDisplayMode = DisplayMode.Correct;
        invalidate();
    }

    /**
     * Clear the pattern lookup table.
     */
    private void clearPatternDrawLookup() {
        for (int i = 0; i < LOCK_SIZE; i++) {
            for (int j = 0; j < LOCK_SIZE; j++) {
                mPatternDrawLookup[i][j] = false;
            }
        }
    }

    /**
     * Disable input (for instance when displaying a message that will timeout so user doesn't get view into messy
     * state).
     */
    public void disableInput() {
        mInputEnabled = false;
    }

    /**
     * Enable input.
     */
    public void enableInput() {
        mInputEnabled = true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        final int width = w - getPaddingLeft() - getPaddingRight();
        mSquareWidth = width / (float) LOCK_SIZE;

        final int height = h - getPaddingTop() - getPaddingBottom();
        mSquareHeight = height / (float) LOCK_SIZE;
    }

    private int resolveMeasured(int measureSpec, int desired) {
        int result = 0;
        int specSize = MeasureSpec.getSize(measureSpec);
        switch (MeasureSpec.getMode(measureSpec)) {
            case MeasureSpec.UNSPECIFIED:
                result = desired;
                break;
            case MeasureSpec.AT_MOST:
                result = Math.max(specSize, desired);
                break;
            case MeasureSpec.EXACTLY:
            default:
                result = specSize;
        }
        return result;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int minimumWidth = getSuggestedMinimumWidth();
        final int minimumHeight = getSuggestedMinimumHeight();
        int viewWidth = resolveMeasured(widthMeasureSpec, minimumWidth);
        int viewHeight = resolveMeasured(heightMeasureSpec, minimumHeight);
        viewWidth = viewHeight = Math.min(viewWidth, viewHeight);
        setMeasuredDimension(viewWidth, viewHeight);
    }

    /**
     * Determines whether the point x, y will add a new point to the current pattern (in addition to finding the cell,
     * also makes heuristic choices such as filling in gaps based on current pattern).
     *
     * @param x The x coordinate.
     * @param y The y coordinate.
     */
    @TargetApi(Build.VERSION_CODES.ECLAIR)
    private Cell detectAndAddHit(float x, float y) {
        final Cell cell = checkForNewHit(x, y);
        if (cell != null) {

            // check for gaps in existing pattern
            Cell fillInGapCell = null;
            final ArrayList<Cell> pattern = mPattern;
            if (!pattern.isEmpty()) {
                final Cell lastCell = pattern.get(pattern.size() - 1);
                int dRow = cell.row - lastCell.row;
                int dColumn = cell.column - lastCell.column;

                int fillInRow = lastCell.row;
                int fillInColumn = lastCell.column;

                if (Math.abs(dRow) == 2 && Math.abs(dColumn) != 1) {
                    fillInRow = lastCell.row + ((dRow > 0) ? 1 : -1);
                }

                if (Math.abs(dColumn) == 2 && Math.abs(dRow) != 1) {
                    fillInColumn = lastCell.column + ((dColumn > 0) ? 1 : -1);
                }

                fillInGapCell = Cell.of(fillInRow, fillInColumn);
            }

            if (fillInGapCell != null
                    && !mPatternDrawLookup[fillInGapCell.row][fillInGapCell.column]) {
                addCellToPattern(fillInGapCell);
            }
            addCellToPattern(cell);
            if (mEnableHapticFeedback) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR)
                    performHapticFeedback(
                            HapticFeedbackConstants.VIRTUAL_KEY,
                            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                                    | HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            }
            return cell;
        }
        return null;
    }

    private void addCellToPattern(Cell newCell) {
        mPatternDrawLookup[newCell.row][newCell.column] = true;
        mPattern.add(newCell);
        if (!mInStealthMode) {
            startCellActivatedAnimation(newCell);
        }
        notifyCellAdded();
    }

    private void startCellActivatedAnimation(Cell cell) {
        final CellState cellState = mCellStates[cell.row][cell.column];
        startSizeAnimation(mDotSize, mDotSizeActivated, 96,
                mLinearOutSlowInInterpolator, cellState, new Runnable() {

                    @Override
                    public void run() {
                        startSizeAnimation(mDotSizeActivated, mDotSize, 192,
                                mFastOutSlowInInterpolator, cellState, null);
                    }
                });
        startLineEndAnimation(cellState, mInProgressX, mInProgressY,
                getCenterXForColumn(cell.column), getCenterYForRow(cell.row));
    }

    private void startLineEndAnimation(final CellState state,
                                       final float startX, final float startY, final float targetX,
                                       final float targetY) {
        /*
         * Currently this animation looks unclear, we don't really need it...
         */
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
            return;

        ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1);
        valueAnimator
                .addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        float t = (Float) animation.getAnimatedValue();
                        state.lineEndX = (1 - t) * startX + t * targetX;
                        state.lineEndY = (1 - t) * startY + t * targetY;
                        invalidate();
                    }

                });
        valueAnimator.addListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animation) {
                state.lineAnimator = null;
            }

        });
        valueAnimator.setInterpolator(mFastOutSlowInInterpolator);
        valueAnimator.setDuration(100);
        valueAnimator.start();
        state.lineAnimator = valueAnimator;
    }

    private void startSizeAnimation(float start, float end, long duration,
                                    Interpolator interpolator, final CellState state,
                                    final Runnable endRunnable) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            FloatAnimator animator = new FloatAnimator(start, end, duration);
            animator.addEventListener(new FloatAnimator.SimpleEventListener() {

                @Override
                public void onAnimationUpdate(FloatAnimator animator) {
                    state.size = (Float) animator.getAnimatedValue();
                    invalidate();
                }// onAnimationUpdate()

                @Override
                public void onAnimationEnd(FloatAnimator animator) {
                    if (endRunnable != null)
                        endRunnable.run();
                }// onAnimationEnd()

            });
            animator.start();
        }// API < 11
        else {
            ValueAnimator valueAnimator = ValueAnimator.ofFloat(start, end);
            valueAnimator
                    .addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

                        @Override
                        public void onAnimationUpdate(ValueAnimator animation) {
                            state.size = (Float) animation.getAnimatedValue();
                            invalidate();
                        }

                    });
            if (endRunnable != null) {
                valueAnimator.addListener(new AnimatorListenerAdapter() {

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (endRunnable != null)
                            endRunnable.run();
                    }

                });
            }
            valueAnimator.setInterpolator(interpolator);
            valueAnimator.setDuration(duration);
            valueAnimator.start();
        }// API 11+
    }// startSizeAnimation()

    // helper method to find which cell a point maps to
    private Cell checkForNewHit(float x, float y) {

        final int rowHit = getRowHit(y);
        if (rowHit < 0) {
            return null;
        }
        final int columnHit = getColumnHit(x);
        if (columnHit < 0) {
            return null;
        }

        if (mPatternDrawLookup[rowHit][columnHit]) {
            return null;
        }
        return Cell.of(rowHit, columnHit);
    }

    /**
     * Helper method to find the row that y falls into.
     *
     * @param y The y coordinate
     * @return The row that y falls in, or -1 if it falls in no row.
     */
    private int getRowHit(float y) {

        final float squareHeight = mSquareHeight;
        float hitSize = squareHeight * mHitFactor;

        float offset = getPaddingTop() + (squareHeight - hitSize) / 2f;
        for (int i = 0; i < LOCK_SIZE; i++) {

            final float hitTop = offset + squareHeight * i;
            if (y >= hitTop && y <= hitTop + hitSize) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Helper method to find the column x fallis into.
     *
     * @param x The x coordinate.
     * @return The column that x falls in, or -1 if it falls in no column.
     */
    private int getColumnHit(float x) {
        final float squareWidth = mSquareWidth;
        float hitSize = squareWidth * mHitFactor;

        float offset = getPaddingLeft() + (squareWidth - hitSize) / 2f;
        for (int i = 0; i < LOCK_SIZE; i++) {

            final float hitLeft = offset + squareWidth * i;
            if (x >= hitLeft && x <= hitLeft + hitSize) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        if (((AccessibilityManager) getContext().getSystemService(
                Context.ACCESSIBILITY_SERVICE)).isTouchExplorationEnabled()) {
            final int action = event.getAction();
            switch (action) {
                case MotionEvent.ACTION_HOVER_ENTER:
                    event.setAction(MotionEvent.ACTION_DOWN);
                    break;
                case MotionEvent.ACTION_HOVER_MOVE:
                    event.setAction(MotionEvent.ACTION_MOVE);
                    break;
                case MotionEvent.ACTION_HOVER_EXIT:
                    event.setAction(MotionEvent.ACTION_UP);
                    break;
            }
            onTouchEvent(event);
            event.setAction(action);
        }
        return super.onHoverEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mInputEnabled || !isEnabled()) {
            return false;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                handleActionDown(event);
                return true;
            case MotionEvent.ACTION_UP:
                handleActionUp(event);
                return true;
            case MotionEvent.ACTION_MOVE:
                handleActionMove(event);
                return true;
            case MotionEvent.ACTION_CANCEL:
            /*
             * Original source check for mPatternInProgress == true first before
             * calling next three lines. But if we do that, there will be
             * nothing happened when the user taps at empty area and releases
             * the finger. We want the pattern to be reset and the message will
             * be updated after the user did that.
             */
                mPatternInProgress = false;
                resetPattern();
                notifyPatternCleared();

                if (PROFILE_DRAWING) {
                    if (mDrawingProfilingStarted) {
                        Debug.stopMethodTracing();
                        mDrawingProfilingStarted = false;
                    }
                }
                return true;
        }
        return false;
    }

    private void handleActionMove(MotionEvent event) {
        // Handle all recent motion events so we don't skip any cells even when
        // the device
        // is busy...
        final float radius = mPathWidth;
        final int historySize = event.getHistorySize();
        mTmpInvalidateRect.setEmpty();
        boolean invalidateNow = false;
        for (int i = 0; i < historySize + 1; i++) {
            final float x = i < historySize ? event.getHistoricalX(i) : event
                    .getX();
            final float y = i < historySize ? event.getHistoricalY(i) : event
                    .getY();
            Cell hitCell = detectAndAddHit(x, y);
            final int patternSize = mPattern.size();
            if (hitCell != null && patternSize == 1) {
                mPatternInProgress = true;
                notifyPatternStarted();
            }
            // note current x and y for rubber banding of in progress patterns
            final float dx = Math.abs(x - mInProgressX);
            final float dy = Math.abs(y - mInProgressY);
            if (dx > DRAG_THRESHHOLD || dy > DRAG_THRESHHOLD) {
                invalidateNow = true;
            }

            if (mPatternInProgress && patternSize > 0) {
                final ArrayList<Cell> pattern = mPattern;
                final Cell lastCell = pattern.get(patternSize - 1);
                float lastCellCenterX = getCenterXForColumn(lastCell.column);
                float lastCellCenterY = getCenterYForRow(lastCell.row);

                // Adjust for drawn segment from last cell to (x,y). Radius
                // accounts for line width.
                float left = Math.min(lastCellCenterX, x) - radius;
                float right = Math.max(lastCellCenterX, x) + radius;
                float top = Math.min(lastCellCenterY, y) - radius;
                float bottom = Math.max(lastCellCenterY, y) + radius;

                // Invalidate between the pattern's new cell and the pattern's
                // previous cell
                if (hitCell != null) {
                    final float width = mSquareWidth * 0.5f;
                    final float height = mSquareHeight * 0.5f;
                    final float hitCellCenterX = getCenterXForColumn(hitCell.column);
                    final float hitCellCenterY = getCenterYForRow(hitCell.row);

                    left = Math.min(hitCellCenterX - width, left);
                    right = Math.max(hitCellCenterX + width, right);
                    top = Math.min(hitCellCenterY - height, top);
                    bottom = Math.max(hitCellCenterY + height, bottom);
                }

                // Invalidate between the pattern's last cell and the previous
                // location
                mTmpInvalidateRect.union(Math.round(left), Math.round(top),
                        Math.round(right), Math.round(bottom));
            }
        }
        mInProgressX = event.getX();
        mInProgressY = event.getY();

        // To save updates, we only invalidate if the user moved beyond a
        // certain amount.
        if (invalidateNow) {
            mInvalidate.union(mTmpInvalidateRect);
            invalidate(mInvalidate);
            mInvalidate.set(mTmpInvalidateRect);
        }
    }

    private void sendAccessEvent(int resId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            setContentDescription(getContext().getString(resId));
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
            setContentDescription(null);
        } else
            announceForAccessibility(getContext().getString(resId));
    }

    private void handleActionUp(MotionEvent event) {
        // report pattern detected
        if (!mPattern.isEmpty()) {
            mPatternInProgress = false;
            cancelLineAnimations();
            notifyPatternDetected();
            invalidate();
        }
        if (PROFILE_DRAWING) {
            if (mDrawingProfilingStarted) {
                Debug.stopMethodTracing();
                mDrawingProfilingStarted = false;
            }
        }
    }

    private void cancelLineAnimations() {
        for (int i = 0; i < LOCK_SIZE; i++) {
            for (int j = 0; j < LOCK_SIZE; j++) {
                CellState state = mCellStates[i][j];
                if (state.lineAnimator != null) {
                    state.lineAnimator.cancel();
                    state.lineEndX = Float.MIN_VALUE;
                    state.lineEndY = Float.MIN_VALUE;
                }
            }
        }
    }

    private void handleActionDown(MotionEvent event) {
        resetPattern();
        final float x = event.getX();
        final float y = event.getY();
        final Cell hitCell = detectAndAddHit(x, y);
        if (hitCell != null) {
            mPatternInProgress = true;
            mPatternDisplayMode = DisplayMode.Correct;
            notifyPatternStarted();
        } else {
            /*
             * Original source check for mPatternInProgress == true first before
             * calling this block. But if we do that, there will be nothing
             * happened when the user taps at empty area and releases the
             * finger. We want the pattern to be reset and the message will be
             * updated after the user did that.
             */
            mPatternInProgress = false;
            notifyPatternCleared();
        }
        if (hitCell != null) {
            final float startX = getCenterXForColumn(hitCell.column);
            final float startY = getCenterYForRow(hitCell.row);

            final float widthOffset = mSquareWidth / 2f;
            final float heightOffset = mSquareHeight / 2f;

            invalidate((int) (startX - widthOffset),
                    (int) (startY - heightOffset),
                    (int) (startX + widthOffset), (int) (startY + heightOffset));
        }
        mInProgressX = x;
        mInProgressY = y;
        if (PROFILE_DRAWING) {
            if (!mDrawingProfilingStarted) {
                Debug.startMethodTracing("LockPatternDrawing");
                mDrawingProfilingStarted = true;
            }
        }
    }

    private float getCenterXForColumn(int column) {
        return getPaddingLeft() + column * mSquareWidth + mSquareWidth / 2f;
    }

    private float getCenterYForRow(int row) {
        return getPaddingTop() + row * mSquareHeight + mSquareHeight / 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final ArrayList<Cell> pattern = mPattern;
        final int count = pattern.size();
        final boolean[][] drawLookup = mPatternDrawLookup;

        if (mPatternDisplayMode == DisplayMode.Animate) {

            // figure out which circles to draw

            // + 1 so we pause on complete pattern
            final int oneCycle = (count + 1) * MILLIS_PER_CIRCLE_ANIMATING;
            final int spotInCycle = (int) (SystemClock.elapsedRealtime() - mAnimatingPeriodStart)
                    % oneCycle;
            final int numCircles = spotInCycle / MILLIS_PER_CIRCLE_ANIMATING;

            clearPatternDrawLookup();
            for (int i = 0; i < numCircles; i++) {
                final Cell cell = pattern.get(i);
                drawLookup[cell.row][cell.column] = true;
            }

            // figure out in progress portion of ghosting line

            final boolean needToUpdateInProgressPoint = numCircles > 0
                    && numCircles < count;

            if (needToUpdateInProgressPoint) {
                final float percentageOfNextCircle = ((float) (spotInCycle % MILLIS_PER_CIRCLE_ANIMATING))
                        / MILLIS_PER_CIRCLE_ANIMATING;

                final Cell currentCell = pattern.get(numCircles - 1);
                final float centerX = getCenterXForColumn(currentCell.column);
                final float centerY = getCenterYForRow(currentCell.row);

                final Cell nextCell = pattern.get(numCircles);
                final float dx = percentageOfNextCircle
                        * (getCenterXForColumn(nextCell.column) - centerX);
                final float dy = percentageOfNextCircle
                        * (getCenterYForRow(nextCell.row) - centerY);
                mInProgressX = centerX + dx;
                mInProgressY = centerY + dy;
            }
            // TODO: Infinite loop here...
            invalidate();
        }

        final Path currentPath = mCurrentPath;
        currentPath.rewind();

        // draw the circles
        for (int i = 0; i < LOCK_SIZE; i++) {
            float centerY = getCenterYForRow(i);
            for (int j = 0; j < LOCK_SIZE; j++) {
                CellState cellState = mCellStates[i][j];
                float centerX = getCenterXForColumn(j);
                float size = cellState.size * cellState.scale;
                float translationY = cellState.translateY;
                drawCircle(canvas, (int) centerX, (int) centerY + translationY,
                        size, drawLookup[i][j], cellState.alpha);
            }
        }

        // TODO: the path should be created and cached every time we hit-detect
        // a cell
        // only the last segment of the path should be computed here
        // draw the path of the pattern (unless we are in stealth mode)
        final boolean drawPath = !mInStealthMode;

        if (drawPath) {
            mPathPaint.setColor(getCurrentColor(true /* partOfPattern */));

            boolean anyCircles = false;
            float lastX = 0f;
            float lastY = 0f;
            for (int i = 0; i < count; i++) {
                Cell cell = pattern.get(i);

                // only draw the part of the pattern stored in
                // the lookup table (this is only different in the case
                // of animation).
                if (!drawLookup[cell.row][cell.column]) {
                    break;
                }
                anyCircles = true;

                float centerX = getCenterXForColumn(cell.column);
                float centerY = getCenterYForRow(cell.row);
                if (i != 0) {
                    CellState state = mCellStates[cell.row][cell.column];
                    currentPath.rewind();
                    currentPath.moveTo(lastX, lastY);
                    if (state.lineEndX != Float.MIN_VALUE
                            && state.lineEndY != Float.MIN_VALUE) {
                        currentPath.lineTo(state.lineEndX, state.lineEndY);
                    } else {
                        currentPath.lineTo(centerX, centerY);
                    }
                    canvas.drawPath(currentPath, mPathPaint);
                }
                lastX = centerX;
                lastY = centerY;
            }

            // draw last in progress section
            if ((mPatternInProgress || mPatternDisplayMode == DisplayMode.Animate)
                    && anyCircles) {
                currentPath.rewind();
                currentPath.moveTo(lastX, lastY);
                currentPath.lineTo(mInProgressX, mInProgressY);

                mPathPaint.setAlpha((int) (calculateLastSegmentAlpha(
                        mInProgressX, mInProgressY, lastX, lastY) * 255f));
                canvas.drawPath(currentPath, mPathPaint);
            }
        }
    }

    private float calculateLastSegmentAlpha(float x, float y, float lastX,
                                            float lastY) {
        float diffX = x - lastX;
        float diffY = y - lastY;
        float dist = (float) Math.sqrt(diffX * diffX + diffY * diffY);
        float frac = dist / mSquareWidth;
        return Math.min(1f, Math.max(0f, (frac - 0.3f) * 4f));
    }

    private int getCurrentColor(boolean partOfPattern) {
        if (!partOfPattern || mInStealthMode || mPatternInProgress) {
            // unselected circle
            return mRegularColor;
        } else if (mPatternDisplayMode == DisplayMode.Wrong) {
            // the pattern is wrong
            return mErrorColor;
        } else if (mPatternDisplayMode == DisplayMode.Correct
                || mPatternDisplayMode == DisplayMode.Animate) {
            return mSuccessColor;
        } else {
            throw new IllegalStateException("unknown display mode "
                    + mPatternDisplayMode);
        }
    }

    /**
     * @param partOfPattern Whether this circle is part of the pattern.
     */
    private void drawCircle(Canvas canvas, float centerX, float centerY,
                            float size, boolean partOfPattern, float alpha) {
        mPaint.setColor(getCurrentColor(partOfPattern));
        mPaint.setAlpha((int) (alpha * 255));
        canvas.drawCircle(centerX, centerY, size / 2, mPaint);
    }


    /**
     * The parecelable for saving and restoring a lock pattern view.
     */
    private static class SavedState extends BaseSavedState {

        private final String mSerializedPattern;
        private final int mDisplayMode;
        private final boolean mInputEnabled;
        private final boolean mInStealthMode;
        private final boolean mTactileFeedbackEnabled;

        /**
         * Constructor called from {@link MaterialLockView#onSaveInstanceState()}
         */
        private SavedState(Parcelable superState, String serializedPattern,
                           int displayMode, boolean inputEnabled, boolean inStealthMode,
                           boolean tactileFeedbackEnabled) {
            super(superState);
            mSerializedPattern = serializedPattern;
            mDisplayMode = displayMode;
            mInputEnabled = inputEnabled;
            mInStealthMode = inStealthMode;
            mTactileFeedbackEnabled = tactileFeedbackEnabled;
        }

        /**
         * Constructor called from {@link #CREATOR}
         */
        private SavedState(Parcel in) {
            super(in);
            mSerializedPattern = in.readString();
            mDisplayMode = in.readInt();
            mInputEnabled = (Boolean) in.readValue(null);
            mInStealthMode = (Boolean) in.readValue(null);
            mTactileFeedbackEnabled = (Boolean) in.readValue(null);
        }

        public String getSerializedPattern() {
            return mSerializedPattern;
        }

        public int getDisplayMode() {
            return mDisplayMode;
        }

        public boolean isInputEnabled() {
            return mInputEnabled;
        }

        public boolean isInStealthMode() {
            return mInStealthMode;
        }

        public boolean isTactileFeedbackEnabled() {
            return mTactileFeedbackEnabled;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(mSerializedPattern);
            dest.writeInt(mDisplayMode);
            dest.writeValue(mInputEnabled);
            dest.writeValue(mInStealthMode);
            dest.writeValue(mTactileFeedbackEnabled);
        }

        @SuppressWarnings("unused")
        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {

            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    public static class FloatAnimator {

        /**
         * Event listener.
         *
         * @author Hai Bison
         */
        public interface EventListener {

            /**
             * Will be called when animation starts.
             *
             * @param animator the animator.
             */
            void onAnimationStart(@NonNull FloatAnimator animator);

            /**
             * Will be called when new animated value is calculated.
             *
             * @param animator the animator.
             */
            void onAnimationUpdate(@NonNull FloatAnimator animator);

            /**
             * Will be called when animation cancels.
             *
             * @param animator the animator.
             */
            void onAnimationCancel(@NonNull FloatAnimator animator);

            /**
             * Will be called when animation ends.
             *
             * @param animator the animator.
             */
            void onAnimationEnd(@NonNull FloatAnimator animator);

        }// EventListener

        /**
         * Simple event listener.
         *
         * @author Hai Bison
         */
        public static class SimpleEventListener implements EventListener {

            @Override
            public void onAnimationStart(@NonNull FloatAnimator animator) {
            }//onAnimationStart()

            @Override
            public void onAnimationUpdate(@NonNull FloatAnimator animator) {
            }//onAnimationUpdate()

            @Override
            public void onAnimationCancel(@NonNull FloatAnimator animator) {
            }//onAnimationCancel()

            @Override
            public void onAnimationEnd(@NonNull FloatAnimator animator) {
            }//onAnimationEnd()

        }// SimpleEventListener

        /**
         * Animation delay, in milliseconds.
         */
        private static final long ANIMATION_DELAY = 1;

        private final float mStartValue, mEndValue;
        private final long mDuration;
        private float mAnimatedValue;

        private List<EventListener> mEventListeners;
        private Handler mHandler;
        private long mStartTime;

        /**
         * Creates new instance.
         *
         * @param start    start value.
         * @param end      end value.
         * @param duration duration, in milliseconds. This should not be long, as delay value between animation frame is
         *                 just 1 millisecond.
         */
        public FloatAnimator(float start, float end, long duration) {
            mStartValue = start;
            mEndValue = end;
            mDuration = duration;

            mAnimatedValue = mStartValue;
        }// FloatAnimator()

        /**
         * Adds event listener.
         *
         * @param listener the listener.
         */
        public void addEventListener(@Nullable EventListener listener) {
            if (listener == null) return;

            mEventListeners.add(listener);
        }// addEventListener()

        /**
         * Gets animated value.
         *
         * @return animated value.
         */
        public float getAnimatedValue() {
            return mAnimatedValue;
        }// getAnimatedValue()

        /**
         * Starts animating.
         */
        public void start() {
            if (mHandler != null)
                return;

            notifyAnimationStart();

            mStartTime = System.currentTimeMillis();

            mHandler = new Handler();
            mHandler.post(new Runnable() {

                @Override
                public void run() {
                    final Handler handler = mHandler;
                    if (handler == null) return;

                    final long elapsedTime = System.currentTimeMillis() - mStartTime;
                    if (elapsedTime > mDuration) {
                        mHandler = null;
                        notifyAnimationEnd();
                    } else {
                        float fraction = mDuration > 0 ? (float) (elapsedTime) / mDuration : 1f;
                        float delta = mEndValue - mStartValue;
                        mAnimatedValue = mStartValue + delta * fraction;

                        notifyAnimationUpdate();
                        handler.postDelayed(this, ANIMATION_DELAY);
                    }
                }// run()

            });
        }// start()

        /**
         * Cancels animating.
         */
        public void cancel() {
            if (mHandler == null) return;

            mHandler.removeCallbacksAndMessages(null);
            mHandler = null;

            notifyAnimationCancel();
            notifyAnimationEnd();
        }// cancel()

        /**
         * Notifies all listeners that animation starts.
         */
        protected void notifyAnimationStart() {
            final List<EventListener> listeners = mEventListeners;
            if (listeners != null) {
                for (EventListener listener : listeners)
                    listener.onAnimationStart(this);
            }// if
        }// notifyAnimationStart()

        /**
         * Notifies all listeners that animation updates.
         */
        protected void notifyAnimationUpdate() {
            final List<EventListener> listeners = mEventListeners;
            if (listeners != null) {
                for (EventListener listener : listeners)
                    listener.onAnimationUpdate(this);
            }// if
        }// notifyAnimationUpdate()

        /**
         * Notifies all listeners that animation cancels.
         */
        protected void notifyAnimationCancel() {
            final List<EventListener> listeners = mEventListeners;
            if (listeners != null) {
                for (EventListener listener : listeners)
                    listener.onAnimationCancel(this);
            }// if
        }// notifyAnimationCancel()

        /**
         * Notifies all listeners that animation ends.
         */
        protected void notifyAnimationEnd() {
            final List<EventListener> listeners = mEventListeners;
            if (listeners != null) {
                for (EventListener listener : listeners)
                    listener.onAnimationEnd(this);
            }// if
        }// notifyAnimationEnd()

    }

    private int dpToPx(float dpValue) {
        final float scale = getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }


}