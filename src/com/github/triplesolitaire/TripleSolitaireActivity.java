package com.github.triplesolitaire;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnDragListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.github.triplesolitaire.Move.Type;

public class TripleSolitaireActivity extends Activity
{
	public enum AutoPlayPreference {
		AUTOPLAY_NEVER, AUTOPLAY_WHEN_OBVIOUS, AUTOPLAY_WHEN_WON
	}

	private class OnCardFlipListener implements OnClickListener
	{
		private final int laneIndex;

		public OnCardFlipListener(final int laneIndex)
		{
			this.laneIndex = laneIndex;
		}

		@Override
		public void onClick(final View v)
		{
			gameState.move(new Move(Move.Type.FLIP, laneIndex));
		}
	}

	private class OnFoundationDragListener implements OnDragListener
	{
		private final int foundationIndex;

		public OnFoundationDragListener(final int foundationIndex)
		{
			this.foundationIndex = foundationIndex;
		}

		@Override
		public boolean onDrag(final View v, final DragEvent event)
		{
			final boolean isMyFoundation = foundationIndex == (Integer) event
					.getLocalState();
			if (event.getAction() == DragEvent.ACTION_DRAG_STARTED)
			{
				final String foundationCard = gameState
						.getFoundationCard(foundationIndex);
				if (isMyFoundation)
				{
					Log.d(TAG, "Drag " + foundationIndex + ": Started of "
							+ foundationCard);
					return false;
				}
				final String card = event.getClipDescription().getLabel()
						.toString();
				return gameState.acceptFoundationDrop(foundationIndex, card);
			}
			else if (event.getAction() == DragEvent.ACTION_DROP)
			{
				final String card = event.getClipData().getItemAt(0).getText()
						.toString();
				final int from = (Integer) event.getLocalState();
				gameState.move(new Move(Type.PLAYER_MOVE, foundationIndex,
						from, card));
			}
			else if (event.getAction() == DragEvent.ACTION_DRAG_ENDED)
				if (isMyFoundation)
					Log.d(TAG, "Drag " + foundationIndex + ": Ended with "
							+ (event.getResult() ? "success" : "failure"));
			return true;
		}
	}

	private class OnFoundationTouchListener implements OnTouchListener
	{
		private final int foundationIndex;

		public OnFoundationTouchListener(final int foundationIndex)
		{
			this.foundationIndex = foundationIndex;
		}

		@Override
		public boolean onTouch(final View v, final MotionEvent event)
		{
			final String foundationCard = gameState
					.getFoundationCard(foundationIndex);
			if (event.getAction() != MotionEvent.ACTION_DOWN
					|| foundationCard == null)
				return false;
			final ClipData dragData = ClipData.newPlainText(foundationCard,
					foundationCard);
			v.startDrag(dragData, new View.DragShadowBuilder(v),
					foundationIndex, 0);
			return true;
		}
	}

	public static final int DIALOG_ID_SHOW_GAME_ID = 1;
	public static final int DIALOG_ID_WINNING = 0;
	/**
	 * Logging tag
	 */
	private static final String TAG = "TripleSolitaireActivity";
	private final GameState gameState = new GameState(this);
	private final Handler handler = new Handler();

	public void animate(final Move move)
	{
		final Point fromLoc;
		if (move.getFromIndex() < 0)
			fromLoc = getFoundationLoc(-1 * move.getFromIndex() - 1);
		else if (move.getFromIndex() == 0)
			fromLoc = getWasteLoc();
		else
			fromLoc = getCascadeLoc(move.getFromIndex() - 1);
		final Point toLoc;
		if (move.getToIndex() < 0)
			toLoc = getFoundationLoc(-1 * move.getToIndex() - 1);
		else if (move.getToIndex() == 0)
			toLoc = getWasteLoc();
		else
			toLoc = getCascadeLoc(move.getToIndex() - 1);
		final Card toAnimate = new Card(getBaseContext(), getResources()
				.getIdentifier(move.getCard(), "drawable", getPackageName()));
		final FrameLayout layout = (FrameLayout) findViewById(R.id.animateLayout);
		layout.addView(toAnimate);
		layout.setX(fromLoc.x);
		layout.setY(fromLoc.y);
		layout.setVisibility(View.VISIBLE);
		final int animationDuration;
		if (move.getType() == Move.Type.UNDO)
			animationDuration = getResources().getInteger(
					R.integer.undo_animation_duration);
		else
			animationDuration = getResources().getInteger(
					R.integer.animation_duration);
		layout.animate().x(toLoc.x).y(toLoc.y).setDuration(animationDuration)
				.setListener(new AnimatorListenerAdapter()
				{
					@Override
					public void onAnimationEnd(final Animator animation)
					{
						if (move.getToIndex() < 0)
							updateFoundationUI(-1 * move.getToIndex() - 1);
						else if (move.getToIndex() == 0)
							updateWasteUI();
						else
							getLane(move.getToIndex() - 1).addCascade(
									move.getCascade());
						layout.removeAllViews();
						layout.setVisibility(View.GONE);
						if (move.getType() != Move.Type.UNDO)
							gameState.animationCompleted();
					}
				});
	}

	public AutoPlayPreference getAutoPlayPreference()
	{
		final SharedPreferences preferences = PreferenceManager
				.getDefaultSharedPreferences(this);
		final int preference = preferences.getInt("auto_play", 0);
		return AutoPlayPreference.values()[preference];
	}

	private Point getCascadeLoc(final int laneIndex)
	{
		final RelativeLayout lane = (RelativeLayout) findViewById(R.id.lane);
		final Card cascadeView = getLane(laneIndex).getTopCascadeCard();
		final float x = cascadeView.getX() + cascadeView.getPaddingLeft()
				+ getLane(laneIndex).getX()
				+ getLane(laneIndex).getPaddingLeft() + lane.getX()
				+ lane.getPaddingLeft();
		final float y = cascadeView.getY() + cascadeView.getPaddingTop()
				+ getLane(laneIndex).getY()
				+ getLane(laneIndex).getPaddingTop() + lane.getY()
				+ lane.getPaddingTop();
		return new Point((int) x, (int) y);
	}

	private Point getFoundationLoc(final int foundationIndex)
	{
		final RelativeLayout foundationLayout = (RelativeLayout) findViewById(R.id.foundation);
		final ImageView foundationView = (ImageView) findViewById(getResources()
				.getIdentifier("foundation" + (foundationIndex + 1), "id",
						getPackageName()));
		final float x = foundationView.getX() + foundationView.getPaddingLeft()
				+ foundationLayout.getX() + foundationLayout.getPaddingLeft();
		final float y = foundationView.getY() + foundationView.getPaddingTop()
				+ foundationLayout.getY() + foundationLayout.getPaddingTop();
		return new Point((int) x, (int) y);
	}

	public Lane getLane(final int laneIndex)
	{
		return (Lane) findViewById(getResources().getIdentifier(
				"lane" + (laneIndex + 1), "id", getPackageName()));
	}

	private Point getWasteLoc()
	{
		final RelativeLayout waste = (RelativeLayout) findViewById(R.id.waste);
		final ImageView waste1View = (ImageView) findViewById(R.id.waste1);
		final float x = waste.getX() + waste.getPaddingLeft()
				+ waste1View.getX() + waste1View.getPaddingLeft();
		final float y = waste.getY() + waste.getPaddingTop()
				+ waste1View.getY() + waste1View.getPaddingTop();
		return new Point((int) x, (int) y);
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		// Set up the progress bar area
		final View progressBar = getLayoutInflater().inflate(
				R.layout.progress_bar, null);
		final ActionBar bar = getActionBar();
		bar.setDisplayShowCustomEnabled(true);
		final ActionBar.LayoutParams layoutParams = new ActionBar.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		layoutParams.gravity = Gravity.LEFT;
		bar.setCustomView(progressBar, layoutParams);
		// Set up game listeners
		final ImageView stockView = (ImageView) findViewById(R.id.stock);
		stockView.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				if (!gameState.isStockEmpty() || !gameState.isWasteEmpty())
					gameState.move(new Move(Move.Type.STOCK));
			}
		});
		final ImageView wasteTopView = (ImageView) findViewById(R.id.waste1);
		wasteTopView.setOnTouchListener(new OnTouchListener()
		{
			@Override
			public boolean onTouch(final View v, final MotionEvent event)
			{
				if (event.getAction() != MotionEvent.ACTION_DOWN
						|| gameState.isWasteEmpty())
					return false;
				final ClipData dragData = ClipData.newPlainText(
						gameState.getWasteCard(0), gameState.getWasteCard(0));
				v.startDrag(dragData, new View.DragShadowBuilder(v), 0, 0);
				return true;
			}
		});
		wasteTopView.setOnDragListener(new OnDragListener()
		{
			private String card;

			@Override
			public boolean onDrag(final View v, final DragEvent event)
			{
				final boolean fromMe = (Integer) event.getLocalState() == 0;
				if (!fromMe)
					return false;
				if (event.getAction() == DragEvent.ACTION_DRAG_STARTED)
				{
					card = event.getClipDescription().getLabel().toString();
					Log.d(TAG, "Drag W: Started of " + card);
				}
				else if (event.getAction() == DragEvent.ACTION_DRAG_ENDED)
				{
					Log.d(TAG, "Drag W: Ended with "
							+ (event.getResult() ? "success" : "failure"));
					if (!event.getResult()
							&& card.equals(gameState.getWasteCard(0)))
						handler.postDelayed(new Runnable()
						{
							@Override
							public void run()
							{
								gameState
										.attemptAutoMoveFromWasteToFoundation();
							}
						}, 10);
				}
				return false;
			}
		});
		for (int curFoundation = 0; curFoundation < 12; curFoundation++)
		{
			final int foundationId = getResources().getIdentifier(
					"foundation" + (curFoundation + 1), "id", getPackageName());
			final ImageView foundationLayout = (ImageView) findViewById(foundationId);
			foundationLayout.setOnTouchListener(new OnFoundationTouchListener(
					-1 * (curFoundation + 1)));
			foundationLayout.setOnDragListener(new OnFoundationDragListener(-1
					* (curFoundation + 1)));
		}
		for (int curLane = 0; curLane < 13; curLane++)
		{
			final int laneId = getResources().getIdentifier(
					"lane" + (curLane + 1), "id", getPackageName());
			final Lane laneLayout = (Lane) findViewById(laneId);
			laneLayout
					.setOnCardFlipListener(new OnCardFlipListener(curLane + 1));
			laneLayout.setLaneId(curLane + 1);
			laneLayout.setGameState(gameState);
		}
		if (savedInstanceState == null)
			gameState.newGame();
	}

	@Override
	protected Dialog onCreateDialog(final int id)
	{
		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		switch (id)
		{
			case DIALOG_ID_WINNING:
				// Message is filled in by onPrepareDialog, which runs every
				// time the dialog is shown (unlike this, which runs only once)
				builder.setMessage("")
						.setCancelable(false)
						.setPositiveButton(getString(R.string.new_game),
								new DialogInterface.OnClickListener()
								{
									@Override
									public void onClick(
											final DialogInterface dialog,
											final int dialogId)
									{
										dialog.cancel();
										gameState.newGame();
									}
								})
						.setNegativeButton(getString(R.string.exit),
								new DialogInterface.OnClickListener()
								{
									@Override
									public void onClick(
											final DialogInterface dialog,
											final int dialogId)
									{
										TripleSolitaireActivity.this.finish();
									}
								});
				return builder.create();
			case DIALOG_ID_SHOW_GAME_ID:
				// Message is filled in by onPrepareDialog, which runs every
				// time the dialog is shown (unlike this, which runs only once)
				builder.setMessage("").setPositiveButton("OK",
						new DialogInterface.OnClickListener()
						{
							@Override
							public void onClick(final DialogInterface dialog,
									final int dialogId)
							{
								dialog.cancel();
							}
						});
				return builder.create();
		}
		return null;
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		getMenuInflater().inflate(R.menu.menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.undo:
				gameState.undo();
				return true;
			case R.id.new_game:
				gameState.newGame();
				return true;
			case R.id.game_id:
				showDialog(DIALOG_ID_SHOW_GAME_ID);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onPrepareDialog(final int id, final Dialog dialog)
	{
		switch (id)
		{
			case DIALOG_ID_WINNING:
				final TextView timeView = (TextView) getActionBar()
						.getCustomView().findViewById(R.id.time);
				final CharSequence time = timeView.getText();
				final TextView moveCountView = (TextView) getActionBar()
						.getCustomView().findViewById(R.id.move_count);
				final CharSequence moveCount = moveCountView.getText();
				final String message = getString(R.string.win_dialog_1) + " "
						+ time + " " + getString(R.string.win_dialog_2) + " "
						+ moveCount + " " + getString(R.string.win_dialog_3);
				((AlertDialog) dialog).setMessage(message);
				break;
			case DIALOG_ID_SHOW_GAME_ID:
				((AlertDialog) dialog)
						.setMessage(getString(R.string.game_id_label) + " "
								+ gameState.getGameId());
				break;
		}
	}

	@Override
	public boolean onPrepareOptionsMenu(final Menu menu)
	{
		menu.findItem(R.id.undo).setEnabled(gameState.canUndo());
		switch (getAutoPlayPreference())
		{
			case AUTOPLAY_WHEN_OBVIOUS:
				menu.findItem(R.id.autoplay).setTitle(
						R.string.autoplay_when_obvious);
				break;
			case AUTOPLAY_WHEN_WON:
				menu.findItem(R.id.autoplay).setTitle(
						R.string.autoplay_when_won);
				break;
			case AUTOPLAY_NEVER:
				menu.findItem(R.id.autoplay).setTitle(R.string.autoplay_never);
				break;
		}
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public void onRestoreInstanceState(final Bundle savedInstanceState)
	{
		super.onRestoreInstanceState(savedInstanceState);
		gameState.onRestoreInstanceState(savedInstanceState);
	}

	@Override
	public void onSaveInstanceState(final Bundle outState)
	{
		super.onSaveInstanceState(outState);
		gameState.onSaveInstanceState(outState);
	}

	/**
	 * This method is specified as an onClick handler in the menu xml and will
	 * take precedence over the Activity's onOptionsItemSelected method.
	 * 
	 * @param item
	 *            menu item clicked
	 */
	public void onSetAutoPlay(final MenuItem item)
	{
		final SharedPreferences preferences = PreferenceManager
				.getDefaultSharedPreferences(this);
		final Editor editor = preferences.edit();
		switch (item.getItemId())
		{
			case R.id.autoplay_when_obvious:
				editor.putInt("auto_play",
						AutoPlayPreference.AUTOPLAY_WHEN_OBVIOUS.ordinal());
				break;
			case R.id.autoplay_when_won:
				editor.putInt("auto_play",
						AutoPlayPreference.AUTOPLAY_WHEN_WON.ordinal());
				break;
			case R.id.autoplay_never:
				editor.putInt("auto_play",
						AutoPlayPreference.AUTOPLAY_NEVER.ordinal());
				break;
		}
		editor.apply();
		// Request a call to onPrepareOptionsMenu so we can change the auto play
		// title
		invalidateOptionsMenu();
	}

	@Override
	public void onWindowFocusChanged(final boolean hasFocus)
	{
		if (hasFocus)
			gameState.resumeGame();
		else
			gameState.pauseGame();
	}

	public void updateFoundationUI(final int foundationIndex)
	{
		final String foundationCard = gameState.getFoundationCard(-1
				* (foundationIndex + 1));
		final int foundationViewId = getResources().getIdentifier(
				"foundation" + (foundationIndex + 1), "id", getPackageName());
		final ImageView foundationView = (ImageView) findViewById(foundationViewId);
		if (foundationCard == null)
		{
			foundationView.setBackgroundResource(R.drawable.foundation);
			foundationView.setOnTouchListener(null);
		}
		else
		{
			foundationView.setBackgroundResource(getResources().getIdentifier(
					foundationCard, "drawable", getPackageName()));
			foundationView.setOnTouchListener(new OnFoundationTouchListener(-1
					* (foundationIndex + 1)));
		}
	}

	public void updateMoveCount(final int moveCount)
	{
		final TextView moveCountView = (TextView) getActionBar()
				.getCustomView().findViewById(R.id.move_count);
		moveCountView.setText(Integer.toString(moveCount));
	}

	public void updateStockUI()
	{
		final ImageView stockView = (ImageView) findViewById(R.id.stock);
		if (gameState.isStockEmpty())
			stockView.setBackgroundResource(R.drawable.lane);
		else
			stockView.setBackgroundResource(R.drawable.back);
	}

	public void updateTime(final int timeInSeconds)
	{
		final int minutes = timeInSeconds / 60;
		final int seconds = timeInSeconds % 60;
		final TextView timeView = (TextView) getActionBar().getCustomView()
				.findViewById(R.id.time);
		final StringBuilder sb = new StringBuilder();
		sb.append(minutes);
		sb.append(':');
		if (seconds < 10)
			sb.append(0);
		sb.append(seconds);
		timeView.setText(sb);
	}

	public void updateWasteUI()
	{
		for (int wasteIndex = 0; wasteIndex < 3; wasteIndex++)
			updateWasteUI(wasteIndex);
	}

	private void updateWasteUI(final int wasteIndex)
	{
		final String wasteCard = gameState.getWasteCard(wasteIndex);
		final ImageView waste = (ImageView) findViewById(getResources()
				.getIdentifier("waste" + (wasteIndex + 1), "id",
						getPackageName()));
		if (wasteCard == null)
			waste.setBackgroundResource(0);
		else
			waste.setBackgroundResource(getResources().getIdentifier(wasteCard,
					"drawable", getPackageName()));
	}
}