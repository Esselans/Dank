package me.saket.dank.ui.submission.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.auto.value.AutoValue;
import com.jakewharton.rxrelay2.Relay;

import me.saket.dank.R;
import me.saket.dank.data.PostedOrInFlightContribution;
import me.saket.dank.data.SpannableWithValueEquality;
import me.saket.dank.ui.subreddits.SubmissionSwipeActionsProvider;
import me.saket.dank.utils.Colors;
import me.saket.dank.utils.DankLinkMovementMethod;
import me.saket.dank.utils.Optional;
import me.saket.dank.utils.lifecycle.LifecycleStreams;
import me.saket.dank.widgets.swipe.SwipeableLayout;
import me.saket.dank.widgets.swipe.ViewHolderWithSwipeActions;

public interface SubmissionCommentsHeader {

  static int getWidthForAlbumContentLinkThumbnail(Context context) {
    return context.getResources().getDimensionPixelSize(R.dimen.submission_link_thumbnail_width_album);
  }

  @AutoValue
  abstract class UiModel implements SubmissionScreenUiModel {
    @Override
    public abstract long adapterId();

    public abstract SpannableWithValueEquality title();

    public abstract SpannableWithValueEquality byline();

    public abstract Optional<SpannableWithValueEquality> selfText();

    public abstract Optional<SubmissionContentLinkUiModel> contentLink();

    @Override
    public SubmissionCommentRowType type() {
      return SubmissionCommentRowType.SUBMISSION_HEADER;
    }

    /**
     * The original data model from which this Ui model was created.
     */
    public abstract PostedOrInFlightContribution originalSubmission();

    public static UiModel create(
        long adapterId,
        CharSequence title,
        CharSequence byline,
        Optional<CharSequence> optionalSelfText,
        Optional<SubmissionContentLinkUiModel> contentLink,
        PostedOrInFlightContribution originalSubmission)
    {
      return new AutoValue_SubmissionCommentsHeader_UiModel(
          adapterId,
          SpannableWithValueEquality.wrap(title),
          SpannableWithValueEquality.wrap(byline),
          optionalSelfText.isPresent()
              ? Optional.of(SpannableWithValueEquality.wrap(optionalSelfText.get()))
              : Optional.empty(),
          contentLink,
          originalSubmission
      );
    }
  }

  class ViewHolder extends RecyclerView.ViewHolder implements ViewHolderWithSwipeActions {
    private final TextView titleView;
    private final TextView bylineView;
    private final TextView selfTextView;
    private final ViewGroup contentLinkView;
    private final ImageView contentLinkIconView;
    private final ImageView contentLinkThumbnailView;
    private final TextView contentLinkTitleView;
    private final TextView contentLinkBylineView;
    private final ProgressBar contentLinkProgressView;
    private final DankLinkMovementMethod movementMethod;

    public static ViewHolder create(
        LayoutInflater inflater,
        ViewGroup parent,
        Relay<Object> headerClickStream,
        DankLinkMovementMethod movementMethod)
    {
      View itemView = inflater.inflate(R.layout.list_item_submission_comments_header, parent, false);
      ViewHolder holder = new ViewHolder(itemView, movementMethod);
      holder.itemView.setOnClickListener(v -> headerClickStream.accept(LifecycleStreams.NOTHING));
      return holder;
    }

    public void setupGestures(SubmissionCommentsAdapter adapter, SubmissionSwipeActionsProvider swipeActionsProvider) {
      getSwipeableLayout().setSwipeActionIconProvider(swipeActionsProvider);
      getSwipeableLayout().setOnPerformSwipeActionListener(action -> {
        UiModel headerUiModel = (UiModel) adapter.getItem(getAdapterPosition());
        swipeActionsProvider.performSwipeAction(action, headerUiModel.originalSubmission(), getSwipeableLayout());

        // TODO.
        //onBindViewHolder(holder, holder.getAdapterPosition());
      });
    }

    public ViewHolder(View itemView, DankLinkMovementMethod movementMethod) {
      super(itemView);
      titleView = itemView.findViewById(R.id.submission_title);
      bylineView = itemView.findViewById(R.id.submission_byline);
      selfTextView = itemView.findViewById(R.id.submission_selfpost_text);
      contentLinkView = itemView.findViewById(R.id.submission_link_container);
      contentLinkIconView = itemView.findViewById(R.id.submission_link_icon);
      contentLinkThumbnailView = itemView.findViewById(R.id.submission_link_thumbnail);
      contentLinkTitleView = itemView.findViewById(R.id.submission_link_title);
      contentLinkBylineView = itemView.findViewById(R.id.submission_link_byline);
      contentLinkProgressView = itemView.findViewById(R.id.submission_link_progress);
      this.movementMethod = movementMethod;

      contentLinkView.setClipToOutline(true);
    }

    public void bind(UiModel uiModel, SubmissionSwipeActionsProvider swipeActionsProvider) {
      titleView.setText(uiModel.title());
      bylineView.setText(uiModel.byline());

      // TODO.
      selfTextView.setVisibility(View.GONE);
      selfTextView.setMovementMethod(movementMethod);

      if (uiModel.contentLink().isPresent()) {
        SubmissionContentLinkUiModel contentLinkUiModel = uiModel.contentLink().get();
        contentLinkView.setVisibility(View.VISIBLE);
        contentLinkProgressView.setVisibility(contentLinkUiModel.progressVisible() ? View.VISIBLE : View.GONE);

        contentLinkTitleView.setText(contentLinkUiModel.title());
        contentLinkBylineView.setText(contentLinkUiModel.byline());

        Context context = itemView.getContext();
        contentLinkTitleView.setTextColor(ContextCompat.getColor(context, contentLinkUiModel.titleTextColorRes()));
        contentLinkBylineView.setTextColor(ContextCompat.getColor(context, contentLinkUiModel.bylineTextColorRes()));

        if (contentLinkUiModel.backgroundTintColor().isPresent()) {
          Integer tintColor = contentLinkUiModel.backgroundTintColor().get();
          contentLinkThumbnailView.setColorFilter(Colors.applyAlpha(tintColor, 0.4f));
          contentLinkView.getBackground().mutate().setTint(tintColor);
        } else {
          contentLinkView.getBackground().mutate().setTintList(null);
        }

        Bitmap favicon = contentLinkUiModel.icon().isPresent() ? contentLinkUiModel.icon().get() : null;
        contentLinkIconView.setImageBitmap(favicon);
        contentLinkIconView.setVisibility(contentLinkUiModel.icon().isPresent() ? View.VISIBLE : View.INVISIBLE);

        Bitmap thumbnail = contentLinkUiModel.thumbnail().isPresent() ? contentLinkUiModel.thumbnail().get() : null;
        contentLinkThumbnailView.setImageBitmap(thumbnail);

      } else {
        contentLinkView.setVisibility(View.GONE);
      }

      // Gestures.
      getSwipeableLayout().setSwipeActions(swipeActionsProvider.actionsFor(uiModel.originalSubmission()));
    }

    @Override
    public SwipeableLayout getSwipeableLayout() {
      return (SwipeableLayout) itemView;
    }
  }
}