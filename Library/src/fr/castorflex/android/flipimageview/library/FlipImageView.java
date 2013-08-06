package fr.castorflex.android.flipimageview.library;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.Transformation;
import android.widget.ImageView;
import fr.castorflex.android.flipimageview.R;

/**
 * Created with IntelliJ IDEA. User: castorflex Date: 30/12/12 Time: 16:25
 */
public class FlipImageView extends ImageView implements View.OnClickListener,
		Animation.AnimationListener {

	private static final int FLAG_ROTATION_X = 1 << 0;

	private static final int FLAG_ROTATION_Y = 1 << 1;

	private static final int FLAG_ROTATION_Z = 1 << 2;

	private static final Interpolator fDefaultInterpolator = new DecelerateInterpolator();

	private static int sDefaultDuration;

	private static int sDefaultRotations;

	private static boolean sDefaultAnimated;

	private static boolean sDefaultFlipped;

	private static boolean sDefaultIsRotationReversed;

	public interface OnFlipListener {

		public void onClick(FlipImageView view);

		public void onFlipStart(FlipImageView view);

		public void onFlipEnd(FlipImageView view);
	}

	private OnFlipListener mListener;

	private boolean mIsFlipped;

	private boolean mIsDefaultAnimated;

	private Drawable mDrawable;

	private Drawable mFlippedDrawable;

	private FlipAnimator mAnimation;

	private boolean mIsRotationXEnabled;

	private boolean mIsRotationYEnabled;

	private boolean mIsRotationZEnabled;

	private boolean mIsFlipping;

	private boolean mIsRotationReversed;

	public FlipImageView(Context context) {
		super(context);
		init(context, null, 0);
	}

	public FlipImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context, attrs, 0);
	}

	public FlipImageView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context, attrs, defStyle);
	}

	private void init(Context context, AttributeSet attrs, int defStyle) {
		sDefaultDuration = context.getResources().getInteger(
				R.integer.default_fiv_duration);
		sDefaultRotations = context.getResources().getInteger(
				R.integer.default_fiv_rotations);
		sDefaultAnimated = context.getResources().getBoolean(
				R.bool.default_fiv_isAnimated);
		sDefaultFlipped = context.getResources().getBoolean(
				R.bool.default_fiv_isFlipped);
		sDefaultIsRotationReversed = context.getResources().getBoolean(
				R.bool.default_fiv_isRotationReversed);

		TypedArray a = context.obtainStyledAttributes(attrs,
				R.styleable.FlipImageView, defStyle, 0);
		mIsDefaultAnimated = a.getBoolean(R.styleable.FlipImageView_isAnimated,
				sDefaultAnimated);
		mIsFlipped = a.getBoolean(R.styleable.FlipImageView_isFlipped,
				sDefaultFlipped);
		mFlippedDrawable = a
				.getDrawable(R.styleable.FlipImageView_flipDrawable);
		int duration = a.getInt(R.styleable.FlipImageView_flipDuration,
				sDefaultDuration);
		int interpolatorResId = a.getResourceId(
				R.styleable.FlipImageView_flipInterpolator, 0);
		Interpolator interpolator = interpolatorResId > 0 ? AnimationUtils
				.loadInterpolator(context, interpolatorResId)
				: fDefaultInterpolator;
		int rotations = a.getInteger(R.styleable.FlipImageView_flipRotations,
				sDefaultRotations);
		mIsRotationXEnabled = (rotations & FLAG_ROTATION_X) != 0;
		mIsRotationYEnabled = (rotations & FLAG_ROTATION_Y) != 0;
		mIsRotationZEnabled = (rotations & FLAG_ROTATION_Z) != 0;

		mDrawable = getDrawable();
		mIsRotationReversed = a.getBoolean(
				R.styleable.FlipImageView_reverseRotation,
				sDefaultIsRotationReversed);

		mAnimation = new FlipAnimator();
		mAnimation.setAnimationListener(this);
		mAnimation.setInterpolator(interpolator);
		mAnimation.setDuration(duration);

		setOnClickListener(this);

		setImageDrawable(mIsFlipped ? mFlippedDrawable : mDrawable);
		mIsFlipping = false;

		a.recycle();
	}

	public void setFlippedDrawable(Drawable flippedDrawable) {
		mFlippedDrawable = flippedDrawable;
		if (mIsFlipped)
			setImageDrawable(mFlippedDrawable);

	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		// Get canvas width and height
		int w = MeasureSpec.getSize(widthMeasureSpec);
		int h = MeasureSpec.getSize(heightMeasureSpec);

		w = Math.min(w, h);
		h = w;

		setMeasuredDimension(w, h);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		BitmapDrawable drawable = (BitmapDrawable) getDrawable();

		if (drawable == null) {
			return;
		}

		if (getWidth() == 0 || getHeight() == 0) {
			return;
		}

		Bitmap fullSizeBitmap = drawable.getBitmap();

		int scaledWidth = getMeasuredWidth();
		int scaledHeight = getMeasuredHeight();

		Bitmap mScaledBitmap;
		if (scaledWidth == fullSizeBitmap.getWidth()
				&& scaledHeight == fullSizeBitmap.getHeight()) {
			mScaledBitmap = fullSizeBitmap;
		} else {
			mScaledBitmap = Bitmap.createScaledBitmap(fullSizeBitmap,
					scaledWidth, scaledHeight, true /* filter */);
		}

		Bitmap roundBitmap = getRoundedCornerBitmap(getContext(),
				mScaledBitmap, 10, scaledWidth, scaledHeight, false, false,
				false, false);
		canvas.drawBitmap(roundBitmap, 0, 0, null);

	}

	public static Bitmap getRoundedCornerBitmap(Context context, Bitmap input,
			int pixels, int w, int h, boolean squareTL, boolean squareTR,
			boolean squareBL, boolean squareBR) {

		Bitmap output = Bitmap.createBitmap(w, h, Config.ARGB_8888);
		Canvas canvas = new Canvas(output);
		final float densityMultiplier = context.getResources()
				.getDisplayMetrics().density;

		final int color = 0xff424242;
		final Paint paint = new Paint();
		final Rect rect = new Rect(0, 0, w, h);
		final RectF rectF = new RectF(rect);

		// make sure that our rounded corner is scaled appropriately
		final float roundPx = pixels * densityMultiplier;

		paint.setAntiAlias(true);
		canvas.drawARGB(0, 0, 0, 0);
		paint.setColor(color);
		canvas.drawRoundRect(rectF, roundPx, roundPx, paint);

		// draw rectangles over the corners we want to be square
		if (squareTL) {
			canvas.drawRect(0, 0, w / 2, h / 2, paint);
		}
		if (squareTR) {
			canvas.drawRect(w / 2, 0, w, h / 2, paint);
		}
		if (squareBL) {
			canvas.drawRect(0, h / 2, w / 2, h, paint);
		}
		if (squareBR) {
			canvas.drawRect(w / 2, h / 2, w, h, paint);
		}

		paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
		canvas.drawBitmap(input, 0, 0, paint);

		return output;
	}

	public void setDrawable(Drawable drawable) {
		mDrawable = drawable;
		if (!mIsFlipped)
			setImageDrawable(mDrawable);
	}

	public boolean isRotationXEnabled() {
		return mIsRotationXEnabled;
	}

	public void setRotationXEnabled(boolean enabled) {
		mIsRotationXEnabled = enabled;
	}

	public boolean isRotationYEnabled() {
		return mIsRotationYEnabled;
	}

	public void setRotationYEnabled(boolean enabled) {
		mIsRotationYEnabled = enabled;
	}

	public boolean isRotationZEnabled() {
		return mIsRotationZEnabled;
	}

	public void setRotationZEnabled(boolean enabled) {
		mIsRotationZEnabled = enabled;
	}

	public FlipAnimator getFlipAnimation() {
		return mAnimation;
	}

	public void setInterpolator(Interpolator interpolator) {
		mAnimation.setInterpolator(interpolator);
	}

	public void setDuration(int duration) {
		mAnimation.setDuration(duration);
	}

	public boolean isFlipped() {
		return mIsFlipped;
	}

	public boolean isFlipping() {
		return mIsFlipping;
	}

	public boolean isRotationReversed() {
		return mIsRotationReversed;
	}

	public void setRotationReversed(boolean rotationReversed) {
		mIsRotationReversed = rotationReversed;
	}

	public boolean isAnimated() {
		return mIsDefaultAnimated;
	}

	public void setAnimated(boolean animated) {
		mIsDefaultAnimated = animated;
	}

	public void setFlipped(boolean flipped) {
		setFlipped(flipped, mIsDefaultAnimated);
	}

	public void setFlipped(boolean flipped, boolean animated) {
		if (flipped != mIsFlipped) {
			toggleFlip(animated);
		}
	}

	public void toggleFlip() {
		toggleFlip(mIsDefaultAnimated);
	}

	public void toggleFlip(boolean animated) {
		if (animated) {
			mAnimation.setToDrawable(mIsFlipped ? mDrawable : mFlippedDrawable);
			startAnimation(mAnimation);
		} else {
			setImageDrawable(mIsFlipped ? mDrawable : mFlippedDrawable);
		}
		mIsFlipped = !mIsFlipped;
	}

	public void setOnFlipListener(OnFlipListener listener) {
		mListener = listener;
	}

	@Override
	public void onClick(View v) {
		toggleFlip();
		if (mListener != null) {
			mListener.onClick(this);
		}
	}

	@Override
	public void onAnimationStart(Animation animation) {
		if (mListener != null) {
			mListener.onFlipStart(this);
		}
		mIsFlipping = true;
	}

	@Override
	public void onAnimationEnd(Animation animation) {
		if (mListener != null) {
			mListener.onFlipEnd(this);
		}
		mIsFlipping = false;
	}

	@Override
	public void onAnimationRepeat(Animation animation) {
	}

	/**
	 * Animation part All credits goes to coomar
	 */
	public class FlipAnimator extends Animation {

		private Camera camera;

		private Drawable toDrawable;

		private float centerX;

		private float centerY;

		private boolean visibilitySwapped;

		public void setToDrawable(Drawable to) {
			toDrawable = to;
			visibilitySwapped = false;
		}

		public FlipAnimator() {
			setFillAfter(true);
		}

		@Override
		public void initialize(int width, int height, int parentWidth,
				int parentHeight) {
			super.initialize(width, height, parentWidth, parentHeight);
			camera = new Camera();
			this.centerX = width / 2;
			this.centerY = height / 2;
		}

		@Override
		protected void applyTransformation(float interpolatedTime,
				Transformation t) {
			// Angle around the y-axis of the rotation at the given time. It is
			// calculated both in radians and in the equivalent degrees.
			final double radians = Math.PI * interpolatedTime;
			float degrees = (float) (180.0 * radians / Math.PI);

			if (mIsRotationReversed) {
				degrees = -degrees;
			}

			// Once we reach the midpoint in the animation, we need to hide the
			// source view and show the destination view. We also need to change
			// the angle by 180 degrees so that the destination does not come in
			// flipped around. This is the main problem with SDK sample, it does
			// not
			// do this.
			if (interpolatedTime >= 0.5f) {
				if (mIsRotationReversed) {
					degrees += 180.f;
				} else {
					degrees -= 180.f;
				}

				if (!visibilitySwapped) {
					setImageDrawable(toDrawable);
					visibilitySwapped = true;
				}
			}

			final Matrix matrix = t.getMatrix();

			camera.save();
			camera.translate(0.0f, 0.0f, (float) (150.0 * Math.sin(radians)));
			camera.rotateX(mIsRotationXEnabled ? degrees : 0);
			camera.rotateY(mIsRotationYEnabled ? degrees : 0);
			camera.rotateZ(mIsRotationZEnabled ? degrees : 0);
			camera.getMatrix(matrix);
			camera.restore();

			matrix.preTranslate(-centerX, -centerY);
			matrix.postTranslate(centerX, centerY);
		}
	}
}
